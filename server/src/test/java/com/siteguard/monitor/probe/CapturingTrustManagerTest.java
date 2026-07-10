package com.siteguard.monitor.probe;

import org.junit.jupiter.api.Test;

import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.cert.X509Certificate;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class CapturingTrustManagerTest {

    private X509TrustManager defaultTrustManager() throws Exception {
        var tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init((KeyStore) null);
        return (X509TrustManager) tmf.getTrustManagers()[0];
    }

    private X509Certificate loadTestCert() throws Exception {
        var ks = KeyStore.getInstance("PKCS12");
        try (InputStream in = getClass().getResourceAsStream("/test-keystore.p12")) {
            assertNotNull(in, "test-keystore.p12 应在 test resources 下");
            ks.load(in, "changeit".toCharArray());
        }
        return (X509Certificate) ks.getCertificate("test");
    }

    @Test
    void capturesPeerCertificateDuringCheckServerTrusted() throws Exception {
        var capturing = new CapturingTrustManager(defaultTrustManager());
        var cert = loadTestCert();
        var chain = new X509Certificate[] { cert };

        try {
            capturing.checkServerTrusted(chain, "RSA");
        } catch (Exception expected) {
            // 自签证书不被默认 truststore 信任
        }

        var captured = capturing.getCapturedChain();
        assertNotNull(captured, "证书链应被捕获");
        assertSame(cert, captured[0]);
    }

    @Test
    void getCapturedChainIsNullBeforeHandshake() throws Exception {
        var capturing = new CapturingTrustManager(defaultTrustManager());
        assertNull(capturing.getCapturedChain());
    }

    @Test
    void clearCapturedChainResetsToNull() throws Exception {
        var capturing = new CapturingTrustManager(defaultTrustManager());
        var cert = loadTestCert();
        var chain = new X509Certificate[] { cert };

        try {
            capturing.checkServerTrusted(chain, "RSA");
        } catch (Exception expected) {
        }

        assertNotNull(capturing.getCapturedChain());
        capturing.clearCapturedChain();
        assertNull(capturing.getCapturedChain());
    }
}
