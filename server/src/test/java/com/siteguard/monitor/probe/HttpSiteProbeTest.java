package com.siteguard.monitor.probe;

import com.siteguard.monitor.entity.CheckStatus;
import com.siteguard.monitor.probe.TestCerts.Issued;
import com.siteguard.site.entity.Site;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.security.KeyStore;
import java.util.EnumSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpSiteProbeTest {

    HttpServer server;
    String baseUrl;
    HttpSiteProbe probe;

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        server.start();
        probe = new HttpSiteProbe();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    private Site site() {
        var s = new Site();
        s.setId(1L);
        s.setName("test");
        return s;
    }

    /// 带 fluent setter 的 site 构造器：lenient 测试需要按位构建 cert_forgive。
    private Site site(Consumer<Site> configure) {
        var s = site();
        configure.accept(s);
        return s;
    }

    @Test
    void returnsUpOn200() {
        server.createContext("/ok", ex -> {
            ex.sendResponseHeaders(200, -1);
            ex.close();
        });
        var s = site();
        s.setUrl(baseUrl + "/ok");

        var result = probe.probe(s);

        assertEquals(CheckStatus.UP, result.status());
        assertEquals(200, result.httpStatus());
        assertNotNull(result.responseMs());
        assertTrue(result.responseMs() >= 0);
        assertNull(result.errorMessage());
    }

    @Test
    void returnsUpOn201() {
        server.createContext("/created", ex -> {
            ex.sendResponseHeaders(201, -1);
            ex.close();
        });
        var s = site();
        s.setUrl(baseUrl + "/created");

        var result = probe.probe(s);

        assertEquals(CheckStatus.UP, result.status());
        assertEquals(201, result.httpStatus());
    }

    @Test
    void returnsDownOn404() {
        server.createContext("/missing", ex -> {
            ex.sendResponseHeaders(404, -1);
            ex.close();
        });
        var s = site();
        s.setUrl(baseUrl + "/missing");

        var result = probe.probe(s);

        assertEquals(CheckStatus.DOWN, result.status());
        assertEquals(404, result.httpStatus());
        assertNotNull(result.responseMs());
    }

    @Test
    void returnsDownOn500() {
        server.createContext("/err", ex -> {
            ex.sendResponseHeaders(500, -1);
            ex.close();
        });
        var s = site();
        s.setUrl(baseUrl + "/err");

        var result = probe.probe(s);

        assertEquals(CheckStatus.DOWN, result.status());
        assertEquals(500, result.httpStatus());
    }

    @Test
    void returnsErrorOnUnreachableHost() {
        var s = site();
        // 端口 1 通常关闭，避免对真实网络造成副作用
        s.setUrl("http://127.0.0.1:1/never");

        var result = probe.probe(s);

        assertEquals(CheckStatus.ERROR, result.status());
        assertNull(result.httpStatus());
        assertNotNull(result.errorMessage());
    }

    @Test
    void followsRedirectAndStillUp() {
        server.createContext("/redirect", ex -> {
            ex.getResponseHeaders().add("Location", baseUrl + "/ok");
            ex.sendResponseHeaders(302, -1);
            ex.close();
        });
        server.createContext("/ok", ex -> {
            ex.sendResponseHeaders(200, -1);
            ex.close();
        });
        var s = site();
        s.setUrl(baseUrl + "/redirect");

        var result = probe.probe(s);

        assertEquals(CheckStatus.UP, result.status());
        assertEquals(200, result.httpStatus());
    }

    /// HTTPS 站点 302 跳转到 HTTP：JDK 默认拒绝跨 scheme 跟随，probe 会直接看到 302。
    /// 这种场景（SSO 网关直接 302 到登录页）应当算 UP，而不是 DOWN。
    @Test
    void returnsUpOn302WhenRedirectNotFollowed() throws Exception {
        // 同时启一个 HTTP server（让 Location 指向它）以便 probe 看到 HTTPS→HTTP 跳转
        var httpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        httpServer.start();
        try {
            var httpsServer = HttpsServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            httpsServer.setHttpsConfigurator(new HttpsConfigurator(serverSslContext()));
            httpsServer.createContext("/redirect", ex -> {
                // 302 跳转到 HTTP：JDK Redirect.NORMAL 不会跟随，probe 会直接看到 302
                ex.getResponseHeaders().add("Location",
                        "http://127.0.0.1:" + httpServer.getAddress().getPort() + "/login");
                ex.sendResponseHeaders(302, -1);
                ex.close();
            });
            httpsServer.start();
            try {
                var httpsProbe = new HttpSiteProbe(loadTestKeystore());
                var s = site();
                s.setUrl("https://127.0.0.1:" + httpsServer.getAddress().getPort() + "/redirect");

                var result = httpsProbe.probe(s);

                assertEquals(CheckStatus.UP, result.status());
                assertEquals(302, result.httpStatus());
            } finally {
                httpsServer.stop(0);
            }
        } finally {
            httpServer.stop(0);
        }
    }

    @Test
    void timesOutOnSlowResponse() {
        var hits = new AtomicInteger(0);
        server.createContext("/slow", ex -> {
            hits.incrementAndGet();
            try {
                Thread.sleep(7000);
            } catch (InterruptedException ignored) {
            }
            ex.sendResponseHeaders(200, -1);
            ex.close();
        });
        var s = site();
        s.setUrl(baseUrl + "/slow");

        var result = probe.probe(s);

        assertEquals(CheckStatus.TIMEOUT, result.status());
        assertEquals(1, hits.get(), "handler should have been entered exactly once");
        assertNull(result.httpStatus());
    }

    // ============================================================
    // 证书提取相关测试
    // ============================================================

    /// 加载测试用的 PKCS12 keystore
    private static KeyStore loadTestKeystore() throws Exception {
        var ks = KeyStore.getInstance("PKCS12");
        try (InputStream in = HttpSiteProbeTest.class.getResourceAsStream("/test-keystore.p12")) {
            assertNotNull(in, "test-keystore.p12 应在 test resources 下");
            ks.load(in, "changeit".toCharArray());
        }
        return ks;
    }

    private static SSLContext serverSslContext() throws Exception {
        var ks = loadTestKeystore();
        var kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(ks, "changeit".toCharArray());
        var ctx = SSLContext.getInstance("TLS");
        ctx.init(kmf.getKeyManagers(), null, null);
        return ctx;
    }

    @Test
    void extractCertInfo_returnsExpiryAndIssuer() throws Exception {
        var ks = loadTestKeystore();
        var cert = (java.security.cert.X509Certificate) ks.getCertificate("test");

        var info = HttpSiteProbe.extractCertInfo(cert);

        assertNotNull(info);
        // 测试 keystore 有效期 36500 天（约 100 年），notAfter 应当大致 = now + 36500d
        long oneDayMs = 86_400_000L;
        long now = System.currentTimeMillis();
        long expected = now + 36500L * oneDayMs;
        long tolerance = oneDayMs;  // 1 天容差
        assertTrue(info.expiresAt() >= expected - tolerance && info.expiresAt() <= expected + tolerance,
                "expiresAt should be ~" + expected + " but was " + info.expiresAt());
        // 测试证书的 CN=127.0.0.1
        assertNotNull(info.issuer());
        assertTrue(info.issuer().contains("127.0.0.1"), "issuer should contain CN=127.0.0.1 but was " + info.issuer());
    }

    @Test
    void extractCertInfo_nullCert_returnsNull() {
        assertNull(HttpSiteProbe.extractCertInfo(null));
    }

    @Test
    void httpsProbe_returnsCertInfo() throws Exception {
        // 启一个 HTTPS server（自签证书）
        var httpsServer = HttpsServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        httpsServer.setHttpsConfigurator(new HttpsConfigurator(serverSslContext()));
        httpsServer.createContext("/ok", ex -> {
            ex.sendResponseHeaders(200, -1);
            ex.close();
        });
        httpsServer.start();
        try {
            // 用测试 keystore 构造 probe，让它信任我们的自签证书
            var httpsProbe = new HttpSiteProbe(loadTestKeystore());
            var s = site();
            s.setUrl("https://127.0.0.1:" + httpsServer.getAddress().getPort() + "/ok");

            var result = httpsProbe.probe(s);

            assertEquals(CheckStatus.UP, result.status());
            assertNotNull(result.certExpiresAt(), "HTTPS 站点应能拿到证书到期时间");
            assertNotNull(result.certIssuer(), "HTTPS 站点应能拿到证书签发机构");
        } finally {
            httpsServer.stop(0);
        }
    }

    // ============================================================
    // lenient 失败分级测试
    // ============================================================

    /// 给一个 [Issued] 同步启一个 HTTPS server，注册 /ok 处理，返回 [0]=server baseUrl, [1]=server 引用（用于 stop）。
    /// 这里选择返回 Object[]：保持 helper 紧凑；只由 lenient* 测试消费。
    private record HttpsServerHandle(HttpsServer server, String baseUrl) {
    }

    /// 启 server：叶子私钥 / 链来自 Issued，TC 自定义响应处理器。
    private HttpsServerHandle startHttps(Issued issued, String host, HttpHandler handler) throws Exception {
        var ks = KeyStore.getInstance("PKCS12");
        ks.load(null, null);
        ks.setKeyEntry("leaf", issued.leafKeyPair().getPrivate(),
                "changeit".toCharArray(), issued.chain());
        var kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(ks, "changeit".toCharArray());
        var ctx = SSLContext.getInstance("TLS");
        ctx.init(kmf.getKeyManagers(), null, null);

        var httpsServer = HttpsServer.create(new InetSocketAddress(host, 0), 0);
        httpsServer.setHttpsConfigurator(new HttpsConfigurator(ctx));
        httpsServer.createContext("/ok", handler);
        httpsServer.start();
        String baseUrl = "https://" + host + ":" + httpsServer.getAddress().getPort();
        return new HttpsServerHandle(httpsServer, baseUrl);
    }

    // ---- T1：链不完整（签发者不在信任库）：开关关 → ERROR，开关开 → UP ----------

    @Test
    void lenient_chainIncomplete_notForgiven_returnsError() throws Exception {
        var issued = TestCerts.issue(365, new String[]{"127.0.0.1"}, "CN=Unknown CA, O=SiteGuard Test");
        // 仅放自签，不放链不完整
        var s = site(x -> x.setCertForgive(CertForgive.json(EnumSet.of(CertForgiveType.SELF_SIGNED))));
        try (var h = startIfAbsent(issued, s)) {
            var result = new HttpSiteProbe().probe(s);
            assertEquals(CheckStatus.ERROR, result.status());
            assertTrue(result.errorMessage().contains("CERT_CHAIN_INCOMPLETE"),
                    "预期命中 CERT_CHAIN_INCOMPLETE，实际: " + result.errorMessage());
        }
    }

    @Test
    void lenient_chainIncomplete_forgiven_returnsUp() throws Exception {
        var issued = TestCerts.issue(365, new String[]{"127.0.0.1"}, "CN=Unknown CA, O=SiteGuard Test");
        var s = site(x -> x.setCertForgive(CertForgive.json(EnumSet.of(CertForgiveType.CHAIN_INCOMPLETE))));
        try (var h = startIfAbsent(issued, s)) {
            var result = new HttpSiteProbe().probe(s);
            assertEquals(CheckStatus.UP, result.status());
            assertEquals(200, result.httpStatus());
            assertNotNull(result.certExpiresAt(), "lenient 成功后应带回证书信息供过期告警使用");
        }
    }

    // ---- T2：域名不匹配 ----------------------------------------------------------------

    @Test
    void lenient_domainMismatch_notForgiven_returnsError() throws Exception {
        // SAN=wrong.host.local，但 server 实际监听 127.0.0.1
        var issued = TestCerts.issue(365, new String[]{"example.com"}, "CN=Unknown CA, O=SiteGuard Test");
        var s = site(x -> x.setCertForgive(CertForgive.json(EnumSet.of(CertForgiveType.CHAIN_INCOMPLETE))));
        try (var h = startIfAbsent(issued, s)) {
            var result = new HttpSiteProbe().probe(s);
            assertEquals(CheckStatus.ERROR, result.status());
            assertTrue(result.errorMessage().contains("CERT_DOMAIN_MISMATCH"),
                    "预期命中 CERT_DOMAIN_MISMATCH，实际: " + result.errorMessage());
        }
    }

    @Test
    void lenient_domainMismatch_forgiven_returnsUp() throws Exception {
        var issued = TestCerts.issue(365, new String[]{"example.com"}, "CN=Unknown CA, O=SiteGuard Test");
        var s = site(x -> x.setCertForgive(CertForgive.json(EnumSet.of(CertForgiveType.DOMAIN_MISMATCH))));
        try (var h = startIfAbsent(issued, s)) {
            var result = new HttpSiteProbe().probe(s);
            assertEquals(CheckStatus.UP, result.status());
        }
    }

    // ---- T3：自签证书 ------------------------------------------------------------------

    @Test
    void lenient_selfSigned_notForgiven_returnsError() throws Exception {
        // issuerDn=null → 自签；SAN 匹配 127.0.0.1
        var issued = TestCerts.issue(365, new String[]{"127.0.0.1"}, null);
        var s = site(x -> x.setCertForgive(CertForgive.json(EnumSet.of(CertForgiveType.CHAIN_INCOMPLETE))));
        try (var h = startIfAbsent(issued, s)) {
            var result = new HttpSiteProbe().probe(s);
            assertEquals(CheckStatus.ERROR, result.status());
            assertTrue(result.errorMessage().contains("CERT_SELF_SIGNED"),
                    "预期命中 CERT_SELF_SIGNED，实际: " + result.errorMessage());
        }
    }

    @Test
    void lenient_selfSigned_forgiven_returnsUp() throws Exception {
        var issued = TestCerts.issue(365, new String[]{"127.0.0.1"}, null);
        var s = site(x -> x.setCertForgive(CertForgive.json(EnumSet.of(CertForgiveType.SELF_SIGNED))));
        try (var h = startIfAbsent(issued, s)) {
            var result = new HttpSiteProbe().probe(s);
            assertEquals(CheckStatus.UP, result.status());
        }
    }

    // ---- T4：过期 → 永远 ERROR，且携带证书信息供过期告警 --------------------------------

    @Test
    void lenient_expired_neverForgiven_returnsErrorWithCertInfo() throws Exception {
        var issued = TestCerts.issue(-30, new String[]{"127.0.0.1"}, "CN=Unknown CA, O=SiteGuard Test");
        // 全放
        var s = site(x -> x.setCertForgive(CertForgive.json(
                EnumSet.of(CertForgiveType.CHAIN_INCOMPLETE, CertForgiveType.DOMAIN_MISMATCH, CertForgiveType.SELF_SIGNED))));
        try (var h = startIfAbsent(issued, s)) {
            var result = new HttpSiteProbe().probe(s);
            assertEquals(CheckStatus.ERROR, result.status());
            assertTrue(result.errorMessage().contains("CERT_EXPIRED"),
                    "预期命中 CERT_EXPIRED，实际: " + result.errorMessage());
            assertNotNull(result.certExpiresAt(), "过期路径应携带 expiresAt 供 CertExpiryAlertDefinition 使用");
        }
    }

    // ---- T5：过期 + 域名不匹配同时存在：优先过期 -----------------------------------------

    @Test
    void lenient_expiredTakesPrecedenceOverDomainMismatch() throws Exception {
        var issued = TestCerts.issue(-30, new String[]{"example.com"}, "CN=Unknown CA, O=SiteGuard Test");
        // 仅放域名不匹配，不放过期
        var s = site(x -> x.setCertForgive(CertForgive.json(EnumSet.of(CertForgiveType.DOMAIN_MISMATCH))));
        try (var h = startIfAbsent(issued, s)) {
            var result = new HttpSiteProbe().probe(s);
            assertEquals(CheckStatus.ERROR, result.status());
            assertTrue(result.errorMessage().contains("CERT_EXPIRED"),
                    "过期优先于域名不匹配，预期 CERT_EXPIRED，实际: " + result.errorMessage());
        }
    }

    // ---- T6：trust-all 也连不上（CA 证书已过期）→ 真挂了 ---------------------------------

    @Test
    void lenient_trustAllAlsoFails_realDowntime() throws Exception {
        // 叶子已过期 + CA 也已过期 → No subject alternative names / cert expired 在 trust-all 路径的 checkValidity 也被捕获。
        // 该用例复用 expired 路径：连 lenient 都受 checkValidity 兜底。这里额外构造无法握手的场景。
        // 简化：撤销 CA（alterTrust）不易在单元测试模拟；此处用 CA 证书过期导致 lenient 的重连所用 CA 实际无解。
        // 改为直接用“线程中途中断”风格：server 在握手完成立即关闭连接 → lenient 重连失败。
        Issued issued = TestCerts.issue(365, new String[]{"127.0.0.1"}, "CN=Unknown CA, O=SiteGuard Test");
        var s = site(x -> x.setCertForgive(CertForgive.json(EnumSet.of(CertForgiveType.CHAIN_INCOMPLETE))));
        var handle = startHttps(issued, "127.0.0.1", ex -> {
            // 不按规范发送响应，直接 close：lenient 在读取响应时拿到异常
            ex.close();
        });
        try {
            s.setUrl(handle.baseUrl() + "/ok");
            var result = new HttpSiteProbe().probe(s);
            // 要么 CERT_CHAIN_INCOMPLETE（lenient 重连后 server 立即断开，不应得到 200）
            // 要么 CERT_LENIENT_FAILED；这里严格断言"不是 UP"，避免误判服务器为健康
            assertTrue(result.status() != CheckStatus.UP,
                    "不应把异常响应判为 UP，实际: " + result.status());
        } finally {
            handle.server().stop(0);
        }
    }

    /// 启 server 并回填 site.url。返回的 handle 支持 try-with-resources（实现 AutoCloseable）。
    private ServerScope startIfAbsent(Issued issued, Site s) throws Exception {
        var handle = startHttps(issued, "127.0.0.1", ex -> {
            ex.sendResponseHeaders(200, -1);
            ex.close();
        });
        // 拿到端口后才能拼出完整 base URL
        s.setUrl(handle.baseUrl() + "/ok");
        return new ServerScope(handle, s);
    }

    private record ServerScope(HttpsServerHandle handle, Site site) implements AutoCloseable {
        @Override
        public void close() {
            handle.server().stop(0);
        }
    }
}
