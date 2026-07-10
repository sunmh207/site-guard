package com.siteguard.domain.rdap;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/// RDAP 客户端：启动时拉 IANA bootstrap（每个 TLD → base URL），运行时按 host 查到期日。
///
/// 失败语义：所有异常 / 不支持的 TLD / 非 2xx / JSON 解析失败 / 缺 events 一律返回 null，
/// 由调用方决定怎么处理。**绝不抛出**。
@Slf4j
@Component
public class RdapClient {

    /// 单次查询超时：RDAP 服务器比 HTTP 站点慢，给 10s 余量
    private static final Duration QUERY_TIMEOUT = Duration.ofSeconds(10);

    private final HttpClient httpClient;
    private final String bootstrapUrl;
    private final ObjectMapper mapper = JsonMapper.builder().build();

    /// TLD → RDAP base URL（如 com → https://rdap.verisign.com/com/v1）。
    /// 启动时填充；为 null 表示 bootstrap 拉取失败。
    private Map<String, String> bootstrap;

    /// 生产构造器：用一个新建的 HttpClient + 默认 IANA bootstrap URL
    public RdapClient() {
        this(HttpClient.newBuilder().connectTimeout(QUERY_TIMEOUT).build(),
                "https://data.iana.org/rdap/dns.json");
    }

    /// 测试构造器：注入自定义 HttpClient + bootstrap URL（可指向本地测试 server）。
    /// **不**自动拉取 bootstrap：测试需在 setUp() 末尾显式调 initBootstrap()，
    /// 与生产路径（@PostConstruct 调 initBootstrap）行为一致。
    RdapClient(HttpClient httpClient, String bootstrapUrl) {
        this.httpClient = httpClient;
        this.bootstrapUrl = bootstrapUrl;
    }

    /// 拉取并解析 IANA bootstrap。失败时 log error 并把 bootstrap 保持 null，
    /// 后续 lookup 全部返回 null（不抛）。
    public void initBootstrap() {
        try {
            var response = httpClient.send(
                    HttpRequest.newBuilder(URI.create(bootstrapUrl))
                            .timeout(QUERY_TIMEOUT)
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString()
            );
            if (response.statusCode() / 100 != 2) {
                log.error("RDAP bootstrap HTTP {} from {}", response.statusCode(), bootstrapUrl);
                this.bootstrap = null;
                return;
            }
            this.bootstrap = parseBootstrap(response.body());
            log.info("RDAP bootstrap loaded with {} TLDs", bootstrap.size());
        } catch (IOException | InterruptedException | RuntimeException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.error("Failed to load RDAP bootstrap from {}: {}", bootstrapUrl, e.getMessage());
            this.bootstrap = null;
        }
    }

    /// 把 IANA bootstrap JSON 解析为 Map<TLD, baseUrl>。
    ///
    /// IANA JSON 结构：
    /// { "services": [
    ///     [ ["dns"], [
    ///         ["com", "net"], ["https://rdap.verisign.com/com/v1"],
    ///         ["foo"],        ["https://rdap.example/foo"]
    ///     ] ]
    /// ] }
    ///
    /// 每个 service 的第二个数组里，TLD 数组与单元素 URL 数组交替出现。
    Map<String, String> parseBootstrap(String json) {
        Map<String, String> result = new HashMap<>();
        try {
            JsonNode root = mapper.readTree(json);
            JsonNode services = root.path("services");
            for (JsonNode entry : services) {
                JsonNode serviceData = entry.get(1);
                if (serviceData == null) continue;

                Iterator<JsonNode> it = serviceData.iterator();
                while (it.hasNext()) {
                    JsonNode tldsNode = it.next();
                    if (!it.hasNext()) break;
                    JsonNode urlNode = it.next();
                    // tlds 节点是字符串数组；url 节点是单字符串数组
                    if (!tldsNode.isArray() || !urlNode.isArray() || urlNode.isEmpty()) continue;
                    String url = urlNode.get(0).asString();
                    for (JsonNode tld : tldsNode) {
                        result.put(tld.asString(), url);
                    }
                }
            }
        } catch (RuntimeException e) {
            log.warn("Failed to parse RDAP bootstrap JSON: {}", e.getMessage());
            return new HashMap<>();
        }
        return result;
    }

    /// 查询单个 host 的域名到期日。失败返回 null（不抛）。
    public Long lookup(String host) {
        if (bootstrap == null || host == null || host.isBlank()) {
            return null;
        }
        // 提取 TLD（最后一段）
        int lastDot = host.lastIndexOf('.');
        if (lastDot < 0 || lastDot == host.length() - 1) {
            return null;
        }
        String tld = host.substring(lastDot + 1).toLowerCase();
        String baseUrl = bootstrap.get(tld);
        if (baseUrl == null) {
            log.debug("No RDAP server for TLD: {}", tld);
            return null;
        }

        String url = baseUrl + (baseUrl.endsWith("/") ? "" : "/") + "domain/" + host;
        try {
            var response = httpClient.send(
                    HttpRequest.newBuilder(URI.create(url))
                            .timeout(QUERY_TIMEOUT)
                            .header("Accept", "application/rdap+json")
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString()
            );
            if (response.statusCode() / 100 != 2) {
                log.debug("RDAP {} returned {} for {}", url, response.statusCode(), host);
                return null;
            }
            return parseExpiration(response.body());
        } catch (IOException | InterruptedException | RuntimeException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.debug("RDAP lookup failed for {}: {}", host, e.getMessage());
            return null;
        }
    }

    /// 从 RDAP 响应 JSON 中找到 events[].eventAction == "expiration" 的 eventDate，转毫秒。
    Long parseExpiration(String json) {
        try {
            JsonNode root = mapper.readTree(json);
            JsonNode events = root.path("events");
            for (JsonNode event : events) {
                if ("expiration".equals(event.path("eventAction").asString())) {
                    String date = event.path("eventDate").asString();
                    if (date != null && !date.isEmpty()) {
                        return Instant.parse(date).toEpochMilli();
                    }
                }
            }
        } catch (RuntimeException e) {
            log.debug("Failed to parse RDAP expiration: {}", e.getMessage());
        }
        return null;
    }
}