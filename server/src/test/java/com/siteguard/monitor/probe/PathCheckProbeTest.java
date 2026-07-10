package com.siteguard.monitor.probe;

import com.siteguard.monitor.entity.SitePathRule;
import com.siteguard.monitor.repository.SitePathRuleRepository;
import com.siteguard.site.entity.Site;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpHeaders;
import java.net.http.HttpTimeoutException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PathCheckProbeTest {

    HttpServer server;
    String baseUrl;
    PathCheckProbe probe;

    @Mock
    SitePathRuleRepository ruleRepo;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        server.start();
        // 构造时注入测试用 HttpClient
        probe = new PathCheckProbe(ruleRepo, HttpClient.newHttpClient());
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    private Site site() {
        var s = new Site();
        s.setId(1L);
        s.setName("test");
        s.setUrl(baseUrl);  // 不带尾斜杠
        return s;
    }

    private SitePathRule rule(String path, int expected) {
        var r = new SitePathRule();
        r.setId(System.nanoTime());  // 唯一 id
        r.setSiteId(1L);
        r.setPath(path);
        r.setExpectedHttpStatus(expected);
        return r;
    }

    @Test
    void noRules_isNoop() {
        when(ruleRepo.findBySiteIdOrderByIdAsc(1L)).thenReturn(List.of());

        probe.probe(site());

        verify(ruleRepo, never()).saveAll(any());
    }

    @Test
    void matchingStatus_writesLastHttpStatus_noError() {
        server.createContext("/app_dev.php", ex -> {
            ex.sendResponseHeaders(200, -1);
            ex.close();
        });
        var r = rule("/app_dev.php", 200);
        when(ruleRepo.findBySiteIdOrderByIdAsc(1L)).thenReturn(List.of(r));

        probe.probe(site());

        assertEquals(200, r.getLastHttpStatus());
        assertNull(r.getLastErrorMessage());
        assertNotNull(r.getLastCheckedAt());
        verify(ruleRepo, times(1)).saveAll(any());
    }

    @Test
    void mismatchedStatus_writesActualAndNoError() {
        server.createContext("/app_dev.php", ex -> {
            ex.sendResponseHeaders(404, -1);
            ex.close();
        });
        var r = rule("/app_dev.php", 200);
        when(ruleRepo.findBySiteIdOrderByIdAsc(1L)).thenReturn(List.of(r));

        probe.probe(site());

        assertEquals(404, r.getLastHttpStatus());
        assertNull(r.getLastErrorMessage());
    }

    @Test
    void connectionRefused_writesNullStatusAndErrorMessage() {
        var s = site();
        s.setUrl("http://127.0.0.1:1");  // 几乎不会监听的端口
        var r = rule("/anything", 200);
        when(ruleRepo.findBySiteIdOrderByIdAsc(1L)).thenReturn(List.of(r));

        probe.probe(s);

        assertNull(r.getLastHttpStatus());
        assertNotNull(r.getLastErrorMessage());
    }

    @Test
    void pathStartingWithSlash_resolvedCorrectly() {
        server.createContext("/systeminfo", ex -> {
            ex.sendResponseHeaders(200, -1);
            ex.close();
        });
        var r = rule("/systeminfo", 200);
        when(ruleRepo.findBySiteIdOrderByIdAsc(1L)).thenReturn(List.of(r));

        probe.probe(site());

        assertEquals(200, r.getLastHttpStatus());
    }

    @Test
    void siteUrlWithTrailingSlash_stillResolvesCorrectly() {
        server.createContext("/health", ex -> {
            ex.sendResponseHeaders(200, -1);
            ex.close();
        });
        var s = site();
        s.setUrl(baseUrl + "/");
        var r = rule("/health", 200);
        when(ruleRepo.findBySiteIdOrderByIdAsc(1L)).thenReturn(List.of(r));

        probe.probe(s);

        assertEquals(200, r.getLastHttpStatus());
    }

    @Test
    void oneRuleFails_doesNotPreventOthers() {
        // 同一站点、同一次 probe() 调用：r1 失败、r2 成功 —— 验证 in-loop try/catch 隔离失败
        // 用 StubHttpClient 按 URI 分发：/bad → IOException，/ok → 200
        var stub = StubHttpClient.builder()
                .onPath("/bad", StubOutcome.throwIo(new IOException("connection reset")))
                .onPath("/ok", StubOutcome.status(200))
                .build();
        var stubProbe = new PathCheckProbe(ruleRepo, stub);

        var r1 = rule("/bad", 200);
        var r2 = rule("/ok", 200);
        when(ruleRepo.findBySiteIdOrderByIdAsc(1L)).thenReturn(List.of(r1, r2));

        stubProbe.probe(site());

        // r1 失败：lastHttpStatus 为 null，lastErrorMessage 包含异常类名
        assertNull(r1.getLastHttpStatus());
        assertNotNull(r1.getLastErrorMessage());
        assertTrue(r1.getLastErrorMessage().contains("IOException"));

        // r2 成功：lastHttpStatus=200，errorMessage 为 null —— 关键：r1 失败没阻止 r2
        assertEquals(200, r2.getLastHttpStatus());
        assertNull(r2.getLastErrorMessage());

        // 两条规则都被回写
        verify(ruleRepo, times(1)).saveAll(any());
    }

    @Test
    void httpTimeoutException_writesTimeoutMessage() {
        // StubHttpClient 抛 HttpTimeoutException → lastErrorMessage 必须是 "timeout after 5s"
        var stub = StubHttpClient.builder()
                .onPath("/slow", StubOutcome.throwOf(HttpTimeoutException.class, "request timed out"))
                .build();
        var stubProbe = new PathCheckProbe(ruleRepo, stub);

        var r = rule("/slow", 200);
        when(ruleRepo.findBySiteIdOrderByIdAsc(1L)).thenReturn(List.of(r));

        stubProbe.probe(site());

        assertNull(r.getLastHttpStatus());
        assertEquals("timeout after 5s", r.getLastErrorMessage());
    }

    @Test
    void interruptedException_preservesInterruptFlag() {
        // StubHttpClient 抛 InterruptedException → 探测后当前线程的 interrupt flag 必须被重新设置
        var stub = StubHttpClient.builder()
                .onPath("/slow", StubOutcome.throwOf(InterruptedException.class, "interrupted"))
                .build();
        var stubProbe = new PathCheckProbe(ruleRepo, stub);

        var r = rule("/slow", 200);
        when(ruleRepo.findBySiteIdOrderByIdAsc(1L)).thenReturn(List.of(r));

        // 探测前确认 flag 干净（清掉残余）
        boolean leftover = Thread.interrupted();
        assertFalse(leftover, "precondition: no leftover interrupt flag");

        stubProbe.probe(site());

        // 探测结果：写入 errorMessage
        assertNull(r.getLastHttpStatus());
        assertNotNull(r.getLastErrorMessage());
        assertTrue(r.getLastErrorMessage().contains("InterruptedException"));

        // 中断标志必须被重新设置
        assertTrue(Thread.currentThread().isInterrupted(), "interrupt flag must be re-set after catching InterruptedException");
        // 清掉 flag，避免污染后续测试
        Thread.interrupted();
    }

    @Test
    void allRules_persistViaSaveAll() {
        server.createContext("/a", ex -> { ex.sendResponseHeaders(200, -1); ex.close(); });
        server.createContext("/b", ex -> { ex.sendResponseHeaders(404, -1); ex.close(); });
        var r1 = rule("/a", 200);
        var r2 = rule("/b", 404);
        when(ruleRepo.findBySiteIdOrderByIdAsc(1L)).thenReturn(List.of(r1, r2));

        probe.probe(site());

        ArgumentCaptor<List<SitePathRule>> captor = ArgumentCaptor.forClass(List.class);
        verify(ruleRepo).saveAll(captor.capture());
        assertTrue(captor.getValue().size() == 2);
    }

    // ---------- 连续失败 counter 维护测试 ----------

    @Test
    void probeIncrementsCounterOnFailingRule() {
        // 探测返回 500（≠ 期望 200），初始 counter=0 → 应被增加到 1
        var stub = StubHttpClient.builder()
                .onPath("/api/orders", StubOutcome.status(500))
                .build();
        var stubProbe = new PathCheckProbe(ruleRepo, stub);

        var r = rule("/api/orders", 200);
        r.setConsecutiveFailures(0);
        when(ruleRepo.findBySiteIdOrderByIdAsc(1L)).thenReturn(List.of(r));

        stubProbe.probe(site());

        verify(ruleRepo).saveAll(argThat((Iterable<SitePathRule> rules) -> {
            var saved = rules.iterator().next();
            return saved.getConsecutiveFailures() == 1;
        }));
    }

    @Test
    void probeResetsCounterOnSuccessfulRule() {
        // 探测返回 200（= 期望 200），counter 之前累计到 5 → 应归零
        var stub = StubHttpClient.builder()
                .onPath("/api/orders", StubOutcome.status(200))
                .build();
        var stubProbe = new PathCheckProbe(ruleRepo, stub);

        var r = rule("/api/orders", 200);
        r.setConsecutiveFailures(5);
        when(ruleRepo.findBySiteIdOrderByIdAsc(1L)).thenReturn(List.of(r));

        stubProbe.probe(site());

        verify(ruleRepo).saveAll(argThat((Iterable<SitePathRule> rules) -> {
            var saved = rules.iterator().next();
            return saved.getConsecutiveFailures() == 0;
        }));
    }

    @Test
    void probeKeepsCounterOnRuntimeException() {
        // httpClient.send 抛 RuntimeException → probe 必须不抛异常；saveAll 仍被调用，
        // 但 in-memory state 因为 setLastXxx / setConsecutiveFailures 都没执行过，
        // 持久化下去的还是旧值（counter=3），磁盘 counter 自然保持。
        var stub = StubHttpClient.builder()
                .onPath("/api/orders", StubOutcome.throwOf(RuntimeException.class, "boom"))
                .build();
        var stubProbe = new PathCheckProbe(ruleRepo, stub);

        var r = rule("/api/orders", 200);
        r.setConsecutiveFailures(3);
        when(ruleRepo.findBySiteIdOrderByIdAsc(1L)).thenReturn(List.of(r));

        assertDoesNotThrow(() -> stubProbe.probe(site()));

        // saveAll 仍被调用（与其他规则共享一次落盘）
        verify(ruleRepo).saveAll(anyList());
        // 入参里的 rule.counter 仍是前值 3：probeOne 抛 RuntimeException 后，
        // setLastCheckedAt / setLastHttpStatus / setLastErrorMessage / setConsecutiveFailures 都没执行，
        // in-memory 状态不变 → saveAll 持久化的也是旧值
        assertEquals(3, r.getConsecutiveFailures());
    }

    // ---------- StubHttpClient：按 URI 分发的可编程 HttpClient ----------
    // 委托给一个真 HttpClient，只覆盖 send()。其他抽象方法维持原行为。

    private record StubOutcome(Integer status, Class<? extends Throwable> throwClass, String throwMessage) {
        static StubOutcome status(int s) { return new StubOutcome(s, null, null); }
        static StubOutcome throwIo(IOException e) { return new StubOutcome(null, IOException.class, e.getMessage()); }
        @SuppressWarnings("unchecked")
        static <T extends Throwable> StubOutcome throwOf(Class<T> cls, String msg) {
            return new StubOutcome(null, (Class<? extends Throwable>) cls, msg);
        }
    }

    /// 可编程 HttpClient：按请求 URI 的 path 查表得到 outcome；
    /// 未注册 path → 抛 IOException（让"漏写映射"立刻暴露）。
    /// 委托给一个真 HttpClient，覆盖 send() 即可。
    private static class StubHttpClient extends HttpClient {
        private final HttpClient delegate = HttpClient.newHttpClient();
        private final Map<String, StubOutcome> outcomes = new LinkedHashMap<>();

        private StubHttpClient() {}

        static Builder builder() { return new Builder(); }

        @Override
        public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler)
                throws IOException, InterruptedException {
            URI uri = request.uri();
            String path = uri.getPath();
            StubOutcome outcome = outcomes.get(path);
            if (outcome == null) {
                throw new IOException("StubHttpClient: no outcome for path " + path);
            }
            if (outcome.throwClass() != null) {
                Throwable t;
                try {
                    t = outcome.throwClass().getDeclaredConstructor(String.class).newInstance(outcome.throwMessage());
                } catch (ReflectiveOperationException e) {
                    throw new IOException("StubHttpClient: cannot construct " + outcome.throwClass(), e);
                }
                if (t instanceof IOException) throw (IOException) t;
                if (t instanceof InterruptedException) throw (InterruptedException) t;
                if (t instanceof RuntimeException) throw (RuntimeException) t;
                throw new IOException("StubHttpClient: unsupported throwable " + outcome.throwClass());
            }
            // 不需要 body：直接构造一个空 HttpResponse
            @SuppressWarnings("unchecked")
            HttpResponse<T> resp = (HttpResponse<T>) new FakeHttpResponse(outcome.status());
            return resp;
        }

        // 以下方法委托给真实 HttpClient，避免触发 7+ 个抽象方法
        @Override public java.util.Optional<java.net.CookieHandler> cookieHandler() { return delegate.cookieHandler(); }
        @Override public java.util.Optional<java.time.Duration> connectTimeout() { return delegate.connectTimeout(); }
        @Override public HttpClient.Redirect followRedirects() { return delegate.followRedirects(); }
        @Override public java.util.Optional<java.net.ProxySelector> proxy() { return delegate.proxy(); }
        @Override public javax.net.ssl.SSLContext sslContext() { return delegate.sslContext(); }
        @Override public javax.net.ssl.SSLParameters sslParameters() { return delegate.sslParameters(); }
        @Override public java.util.Optional<java.net.Authenticator> authenticator() { return delegate.authenticator(); }
        @Override public HttpClient.Version version() { return delegate.version(); }
        @Override public java.util.Optional<java.util.concurrent.Executor> executor() { return delegate.executor(); }
        @Override public <T> java.util.concurrent.CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) {
            return delegate.sendAsync(request, responseBodyHandler);
        }
        @Override public <T> java.util.concurrent.CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler, HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
            return delegate.sendAsync(request, responseBodyHandler, pushPromiseHandler);
        }

        // ---- 内部类型 ----

        private static class FakeHttpResponse implements HttpResponse<Void> {
            private final int status;
            FakeHttpResponse(int status) { this.status = status; }
            @Override public int statusCode() { return status; }
            @Override public HttpRequest request() { return null; }
            @Override public java.util.Optional<HttpResponse<Void>> previousResponse() { return java.util.Optional.empty(); }
            @Override public HttpHeaders headers() { return HttpHeaders.of(java.util.Map.of(), (a, b) -> true); }
            @Override public Void body() { return null; }
            @Override public java.util.Optional<javax.net.ssl.SSLSession> sslSession() { return java.util.Optional.empty(); }
            @Override public java.net.URI uri() { return null; }
            @Override public HttpClient.Version version() { return HttpClient.Version.HTTP_1_1; }
        }

        static class Builder {
            private final StubHttpClient client = new StubHttpClient();
            Builder onPath(String path, StubOutcome outcome) {
                client.outcomes.put(path, outcome);
                return this;
            }
            StubHttpClient build() { return client; }
        }
    }
}
