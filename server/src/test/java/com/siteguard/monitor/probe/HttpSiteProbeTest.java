package com.siteguard.monitor.probe;

import com.siteguard.monitor.entity.CheckStatus;
import com.siteguard.site.entity.Site;
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
import java.util.concurrent.atomic.AtomicInteger;

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
}
