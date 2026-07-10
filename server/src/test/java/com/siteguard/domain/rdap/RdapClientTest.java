package com.siteguard.domain.rdap;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class RdapClientTest {

    HttpServer bootstrapServer;
    HttpServer rdapServer;
    String bootstrapBase;
    String rdapBase;
    RdapClient client;

    @BeforeEach
    void setUp() throws IOException {
        bootstrapServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        rdapServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        bootstrapBase = "http://127.0.0.1:" + bootstrapServer.getAddress().getPort();
        rdapBase = "http://127.0.0.1:" + rdapServer.getAddress().getPort();

        bootstrapServer.start();
        rdapServer.start();

        // 用一个能跳到本地 RDAP 服务器的 bootstrap
        bootstrapServer.createContext("/dns.json", ex -> {
            String body = """
                {
                  "services": [
                    [["dns"], [
                      ["com"], ["%s"]
                    ]]
                  ]
                }
                """.formatted(rdapBase);
            ex.sendResponseHeaders(200, body.getBytes().length);
            ex.getResponseBody().write(body.getBytes());
            ex.close();
        });

        client = new RdapClient(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build(),
                bootstrapBase + "/dns.json"
        );
        client.initBootstrap();
    }

    @AfterEach
    void tearDown() {
        bootstrapServer.stop(0);
        rdapServer.stop(0);
    }

    @Test
    void lookup_returnsExpiryFromEvents() {
        rdapServer.createContext("/domain/example.com", ex -> {
            String body = """
                {
                  "objectClassName": "domain",
                  "events": [
                    {"eventAction": "registration", "eventDate": "2020-01-01T00:00:00Z"},
                    {"eventAction": "expiration", "eventDate": "2027-12-31T23:59:59Z"}
                  ]
                }
                """;
            ex.sendResponseHeaders(200, body.getBytes().length);
            ex.getResponseBody().write(body.getBytes());
            ex.close();
        });

        Long expires = client.lookup("example.com");

        assertNotNull(expires);
        // 2027-12-31T23:59:59Z = 1830297599000L
        assertEquals(1830297599000L, expires.longValue());
    }

    @Test
    void lookup_unknownTld_returnsNull() {
        assertNull(client.lookup("example.xyz"));
    }

    @Test
    void lookup_rdapReturns500_returnsNull() {
        rdapServer.createContext("/domain/broken.com", ex -> {
            ex.sendResponseHeaders(500, -1);
            ex.close();
        });
        assertNull(client.lookup("broken.com"));
    }

    @Test
    void lookup_rdapReturns404_returnsNull() {
        rdapServer.createContext("/domain/missing.com", ex -> {
            ex.sendResponseHeaders(404, -1);
            ex.close();
        });
        assertNull(client.lookup("missing.com"));
    }

    @Test
    void lookup_noExpirationEvent_returnsNull() {
        rdapServer.createContext("/domain/noexp.com", ex -> {
            String body = """
                {
                  "events": [
                    {"eventAction": "registration", "eventDate": "2020-01-01T00:00:00Z"}
                  ]
                }
                """;
            ex.sendResponseHeaders(200, body.getBytes().length);
            ex.getResponseBody().write(body.getBytes());
            ex.close();
        });
        assertNull(client.lookup("noexp.com"));
    }

    @Test
    void lookup_rdapReturnsMalformedJson_returnsNull() {
        rdapServer.createContext("/domain/bad.com", ex -> {
            String body = "{ this is not json";
            ex.sendResponseHeaders(200, body.getBytes().length);
            ex.getResponseBody().write(body.getBytes());
            ex.close();
        });
        assertNull(client.lookup("bad.com"));
    }
}