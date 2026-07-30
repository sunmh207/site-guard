package com.siteguard.monitor.probe;

import com.siteguard.monitor.entity.CheckStatus;
import com.siteguard.monitor.entity.SitePathCheckHistory;
import com.siteguard.monitor.entity.SitePathRule;
import com.siteguard.monitor.jsoncondition.JsonAssertionConfigCodec;
import com.siteguard.monitor.jsoncondition.JsonAssertionConfigDTO;
import com.siteguard.monitor.jsoncondition.JsonConditionDiagnostic;
import com.siteguard.monitor.jsoncondition.JsonConditionEvaluation;
import com.siteguard.monitor.jsoncondition.JsonConditionEvaluator;
import com.siteguard.monitor.repository.SitePathCheckHistoryRepository;
import com.siteguard.monitor.repository.SitePathRuleRepository;
import com.siteguard.site.entity.Site;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;

/// 站点自定义子路由探测组件。
///
/// transport 只负责取得状态码和受限响应体；HTTP_STATUS / KEYWORD / JSON_ASSERT 的业务判定
/// 统一在 evaluateResponse 中完成。普通请求和 cert-forgive lenient 请求因此不会产生两套判定口径。
@Component
@Slf4j
public class PathCheckProbe {

    private static final Duration TIMEOUT = Duration.ofSeconds(15);
    private static final int MAX_BODY_READ_BYTES = 1_048_576;
    private static final String USER_AGENT =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Safari/537.36";

    private final SitePathRuleRepository ruleRepo;
    private final SitePathCheckHistoryRepository historyRepo;
    private final HttpClient httpClient;
    private final JsonConditionEvaluator jsonEvaluator;
    private final JsonAssertionConfigCodec assertionCodec;

    @Autowired
    public PathCheckProbe(SitePathRuleRepository ruleRepo,
                          SitePathCheckHistoryRepository historyRepo,
                          JsonConditionEvaluator jsonEvaluator,
                          JsonAssertionConfigCodec assertionCodec) {
        this(ruleRepo, historyRepo, HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(TIMEOUT)
                .build(), jsonEvaluator, assertionCodec);
    }

    /// 兼容原有测试构造器；使用与生产相同的 Jackson 配置语义。
    PathCheckProbe(SitePathRuleRepository ruleRepo,
                   SitePathCheckHistoryRepository historyRepo,
                   HttpClient httpClient) {
        var mapper = JsonMapper.builder().build();
        this.ruleRepo = ruleRepo;
        this.historyRepo = historyRepo;
        this.httpClient = httpClient;
        this.jsonEvaluator = new JsonConditionEvaluator(mapper);
        this.assertionCodec = new JsonAssertionConfigCodec(mapper, jsonEvaluator);
    }

    PathCheckProbe(SitePathRuleRepository ruleRepo,
                   SitePathCheckHistoryRepository historyRepo,
                   HttpClient httpClient,
                   JsonConditionEvaluator jsonEvaluator,
                   JsonAssertionConfigCodec assertionCodec) {
        this.ruleRepo = ruleRepo;
        this.historyRepo = historyRepo;
        this.httpClient = httpClient;
        this.jsonEvaluator = jsonEvaluator;
        this.assertionCodec = assertionCodec;
    }

    public void probe(Site site) {
        List<SitePathRule> rules = ruleRepo.findBySiteIdOrderByIdAsc(site.getId());
        if (rules.isEmpty()) return;
        boolean https = isHttpsUrl(site.getUrl());
        long checkedAt = System.currentTimeMillis();
        for (SitePathRule rule : rules) {
            try {
                writeOutcome(rule, checkedAt, probeOne(site, rule, https));
            } catch (RuntimeException e) {
                /// 单条配置或非预期异常不影响同站其他规则；旧快照和 counter 保持不变。
                log.warn("path rule probe failed for site={} rule={}: {}",
                        site.getId(), rule.getId(), e.getMessage());
            }
        }
        try {
            ruleRepo.saveAll(rules);
        } catch (RuntimeException e) {
            log.warn("save path route probe state failed for site={}: {}", site.getId(), e.getMessage());
        }
    }

    /// 不落库的一次性探测，供管理端测试接口复用；不会写历史或改变 counter。
    public PathProbeResult test(Site site, SitePathRule rule) {
        return probeOne(site, rule, isHttpsUrl(site.getUrl())).toPublicResult(rule);
    }

    private void writeOutcome(SitePathRule rule, long checkedAt, PathProbeOutcome outcome) {
        rule.setLastCheckedAt(checkedAt);
        rule.setLastHttpStatus(outcome.httpStatus());
        rule.setLastTextMatched(outcome.textMatched());
        rule.setLastJsonMatched(outcome.jsonMatched());
        rule.setLastJsonDetail(outcome.jsonDetail());
        rule.setLastErrorMessage(outcome.errorMessage());
        int currentCounter = rule.getConsecutiveFailures();
        rule.setConsecutiveFailures(SitePathRule.isFailing(rule) ? currentCounter + 1 : 0);

        try {
            var history = new SitePathCheckHistory();
            history.setSiteId(rule.getSiteId());
            history.setRuleId(rule.getId());
            history.setPath(rule.getPath());
            history.setCheckedAt(checkedAt);
            history.setStatus(toHistoryStatus(outcome));
            history.setHttpStatus(outcome.httpStatus());
            history.setTextMatched(outcome.textMatched());
            history.setJsonMatched(outcome.jsonMatched());
            history.setJsonDetail(outcome.jsonDetail());
            history.setErrorMessage(outcome.errorMessage());
            historyRepo.save(history);
        } catch (RuntimeException e) {
            log.warn("save path check history for site={} rule={}: {}",
                    rule.getSiteId(), rule.getId(), e.getMessage());
        }
    }

    /// 历史 status 只描述请求是否完成；JSON 解析/条件失败属于业务失败，仍记录为 UP。
    private CheckStatus toHistoryStatus(PathProbeOutcome outcome) {
        return outcome.errorMessage() == null ? CheckStatus.UP : CheckStatus.ERROR;
    }

    private PathProbeOutcome probeOne(Site site, SitePathRule rule, boolean https) {
        HttpClient client = httpClient;
        CapturingTrustManager captureTm = null;
        if (https && site.hasAnyCertForgive()) {
            captureTm = HttpSiteProbe.createCapturingTrustManager(null);
            client = buildHttpsCapturingClient(captureTm);
        }
        URI uri = resolve(site.getUrl(), rule.getPath());
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(TIMEOUT)
                .header("User-Agent", USER_AGENT)
                .GET()
                .build();
        boolean readBody = needsBody(rule);

        try {
            FetchResult fetched;
            if (readBody) {
                var response = client.send(request, cappedBody(MAX_BODY_READ_BYTES));
                fetched = new FetchResult(response.statusCode(), response.body().body(),
                        response.body().truncated(), null);
            } else {
                var response = client.send(request, HttpResponse.BodyHandlers.discarding());
                fetched = new FetchResult(response.statusCode(), null, false, null);
            }
            return evaluateResponse(rule, fetched);
        } catch (HttpTimeoutException e) {
            return PathProbeOutcome.error("timeout after " + TIMEOUT.toSeconds() + "s");
        } catch (SSLHandshakeException e) {
            if (captureTm != null) {
                FetchResult forgiven = tryForgiveCert(site, uri, captureTm, rule);
                if (forgiven != null) return evaluateResponse(rule, forgiven);
            }
            return PathProbeOutcome.error(truncate("SSLHandshakeException: " + e.getMessage(), 500));
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            return PathProbeOutcome.error(truncate(e.getClass().getSimpleName() + ": " + e.getMessage(), 500));
        }
    }

    private PathProbeOutcome evaluateResponse(SitePathRule rule, FetchResult fetched) {
        if (fetched.errorMessage() != null) {
            return PathProbeOutcome.error(fetched.errorMessage());
        }
        PathCheckType type = rule.getCheckType() == null ? PathCheckType.HTTP_STATUS : rule.getCheckType();
        return switch (type) {
            case HTTP_STATUS -> new PathProbeOutcome(fetched.httpStatus(), null, null, null,
                    null, null, true, List.of());
            case KEYWORD -> {
                boolean matched = fetched.body() != null && fetched.body().contains(rule.getExpectedText());
                yield new PathProbeOutcome(fetched.httpStatus(), matched, null, null,
                        null, null, true, List.of());
            }
            case JSON_ASSERT -> evaluateJson(rule, fetched);
        };
    }

    private PathProbeOutcome evaluateJson(SitePathRule rule, FetchResult fetched) {
        if (fetched.truncated()) {
            String detail = "响应体超过 1 MB 读取上限，无法安全解析完整 JSON";
            return new PathProbeOutcome(fetched.httpStatus(), null, false, detail,
                    null, null, false, List.of());
        }
        JsonAssertionConfigDTO config;
        try {
            config = assertionCodec.decode(rule.getAssertionConfig());
            if (config == null) throw new IllegalArgumentException("配置为空");
        } catch (IllegalArgumentException e) {
            String detail = truncate("JSON 条件配置错误：" + e.getMessage(), 2048);
            return new PathProbeOutcome(fetched.httpStatus(), null, false, detail,
                    null, null, false, List.of());
        }
        JsonConditionEvaluation evaluation = jsonEvaluator.evaluate(fetched.body(), config);
        return new PathProbeOutcome(fetched.httpStatus(), null, evaluation.matched(), evaluation.detail(),
                null, evaluation.parseSucceeded(), evaluation.parseSucceeded(), evaluation.conditions());
    }

    /// cert-forgive 只选择传输通道；拿到统一 FetchResult 后仍回到 evaluateResponse 判定。
    private FetchResult tryForgiveCert(Site site, URI uri, CapturingTrustManager tm, SitePathRule rule) {
        try {
            var chain = tm.getCapturedChain();
            if (chain == null || chain.length == 0) return null;
            var leaf = (X509Certificate) chain[0];
            try {
                leaf.checkValidity();
            } catch (java.security.cert.CertificateNotYetValidException |
                     java.security.cert.CertificateExpiredException e) {
                return null;
            }
            var type = HttpSiteProbe.classifyFailure(uri.getHost(), leaf);
            boolean forgiven = switch (type) {
                case DOMAIN_MISMATCH -> site.isForgiveDomainMismatch();
                case SELF_SIGNED -> site.isForgiveSelfSigned();
                case CHAIN_INCOMPLETE -> site.isForgiveChainIncomplete();
            };
            return forgiven ? executeLenientGet(uri, needsBody(rule)) : null;
        } catch (Exception e) {
            log.debug("path rule cert-forgive classify error: {}", e.getMessage());
            return null;
        }
    }

    private static FetchResult executeLenientGet(URI uri, boolean readBody) {
        HttpURLConnection connection = null;
        try {
            URL url = uri.toURL();
            var sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, new TrustManager[]{new TrustAllManager()}, null);
            HttpsURLConnection https = (HttpsURLConnection) url.openConnection();
            connection = https;
            https.setSSLSocketFactory(sslContext.getSocketFactory());
            https.setHostnameVerifier((hostname, session) -> true);
            https.setRequestMethod("GET");
            https.setRequestProperty("User-Agent", USER_AGENT);
            https.setConnectTimeout((int) TIMEOUT.toMillis());
            https.setReadTimeout((int) TIMEOUT.toMillis());
            https.setInstanceFollowRedirects(true);
            https.connect();
            int status = https.getResponseCode();
            if (!readBody) return new FetchResult(status, null, false, null);
            CappedBody body = readBody(https);
            return new FetchResult(status, body.body(), body.truncated(), null);
        } catch (Exception e) {
            return FetchResult.error("cert_lenient_failed: " + e.getClass().getSimpleName() + ": " + e.getMessage());
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static CappedBody readBody(HttpsURLConnection connection) throws IOException {
        InputStream stream;
        try {
            stream = connection.getInputStream();
        } catch (IOException e) {
            stream = connection.getErrorStream();
            if (stream == null) throw e;
        }
        final InputStream input = stream;
        try (input) {
            byte[] bytes = input.readNBytes(MAX_BODY_READ_BYTES + 1);
            boolean truncated = bytes.length > MAX_BODY_READ_BYTES;
            int length = Math.min(bytes.length, MAX_BODY_READ_BYTES);
            return new CappedBody(new String(bytes, 0, length, StandardCharsets.UTF_8), truncated);
        }
    }

    private static boolean needsBody(SitePathRule rule) {
        PathCheckType type = rule.getCheckType() == null ? PathCheckType.HTTP_STATUS : rule.getCheckType();
        return type == PathCheckType.KEYWORD || type == PathCheckType.JSON_ASSERT;
    }

    private static URI resolve(String siteUrl, String path) {
        String base = siteUrl.endsWith("/") ? siteUrl.substring(0, siteUrl.length() - 1) : siteUrl;
        String tail = path.startsWith("/") ? path : "/" + path;
        return URI.create(base + tail);
    }

    private static boolean isHttpsUrl(String url) {
        return url != null && url.regionMatches(true, 0, "https://", 0, 8);
    }

    private static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) return value;
        return value.substring(0, maxLength);
    }

    private static HttpResponse.BodyHandler<CappedBody> cappedBody(int maxBytes) {
        return info -> new CappedBodySubscriber(maxBytes);
    }

    private static final class CappedBodySubscriber implements HttpResponse.BodySubscriber<CappedBody> {
        private final int maxBytes;
        private final CompletableFuture<CappedBody> result = new CompletableFuture<>();
        private final ByteArrayOutputStream output;
        private boolean truncated;

        private CappedBodySubscriber(int maxBytes) {
            this.maxBytes = maxBytes;
            this.output = new ByteArrayOutputStream(Math.min(maxBytes, 8192));
        }

        @Override
        public CompletionStage<CappedBody> getBody() {
            return result;
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            subscription.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(List<ByteBuffer> items) {
            for (ByteBuffer buffer : items) {
                int room = maxBytes - output.size();
                if (room <= 0) {
                    if (buffer.hasRemaining()) truncated = true;
                    continue;
                }
                int length = Math.min(buffer.remaining(), room);
                byte[] bytes = new byte[length];
                buffer.get(bytes);
                output.writeBytes(bytes);
                if (buffer.hasRemaining()) truncated = true;
            }
        }

        @Override
        public void onError(Throwable throwable) {
            result.completeExceptionally(throwable);
        }

        @Override
        public void onComplete() {
            result.complete(new CappedBody(output.toString(StandardCharsets.UTF_8), truncated));
        }
    }

    private static final class TrustAllManager implements X509TrustManager {
        @Override public void checkClientTrusted(X509Certificate[] chain, String authType) { }
        @Override public void checkServerTrusted(X509Certificate[] chain, String authType) { }
        @Override public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
    }

    private static HttpClient buildHttpsCapturingClient(CapturingTrustManager tm) {
        try {
            var context = SSLContext.getInstance("TLS");
            context.init(null, new TrustManager[]{tm}, null);
            return HttpClient.newBuilder()
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .connectTimeout(TIMEOUT)
                    .sslContext(context)
                    .build();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to build HTTPS capturing HttpClient for path rule", e);
        }
    }

    private record CappedBody(String body, boolean truncated) { }

    private record FetchResult(Integer httpStatus, String body, boolean truncated, String errorMessage) {
        private static FetchResult error(String message) {
            return new FetchResult(null, null, false, truncate(message, 500));
        }
    }

    private record PathProbeOutcome(
            Integer httpStatus,
            Boolean textMatched,
            Boolean jsonMatched,
            String jsonDetail,
            String errorMessage,
            Boolean bodyParsed,
            boolean requestCompleted,
            List<JsonConditionDiagnostic> conditions
    ) {
        private static PathProbeOutcome error(String message) {
            return new PathProbeOutcome(null, null, null, null, message,
                    null, false, List.of());
        }

        private PathProbeResult toPublicResult(SitePathRule rule) {
            boolean statusMatched = httpStatus != null && httpStatus.equals(rule.getExpectedHttpStatus());
            boolean healthy = switch (rule.getCheckType() == null ? PathCheckType.HTTP_STATUS : rule.getCheckType()) {
                case HTTP_STATUS -> statusMatched;
                case KEYWORD -> Boolean.TRUE.equals(textMatched);
                case JSON_ASSERT -> statusMatched && Boolean.TRUE.equals(bodyParsed) && Boolean.TRUE.equals(jsonMatched);
            };
            String summary = errorMessage != null ? errorMessage
                    : !statusMatched ? "HTTP 状态码不满足：期望 " + rule.getExpectedHttpStatus() + "，实际 " + httpStatus
                    : jsonDetail != null ? jsonDetail
                    : healthy ? "检测通过" : "检测未通过";
            return new PathProbeResult(requestCompleted, httpStatus, statusMatched, bodyParsed,
                    jsonMatched, textMatched, healthy, summary, conditions, errorMessage);
        }
    }

    /// 不落库测试接口使用的统一结果模型。
    public record PathProbeResult(
            boolean requestCompleted,
            Integer httpStatus,
            boolean httpStatusMatched,
            Boolean bodyParsed,
            Boolean jsonMatched,
            Boolean textMatched,
            boolean healthy,
            String summary,
            List<JsonConditionDiagnostic> conditions,
            String errorMessage
    ) { }
}
