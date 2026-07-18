package com.siteguard.monitor.probe;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.util.Date;

/// 测试用证书工厂：按需生成各类异常证书覆盖 [HttpSiteProbe] lenient 路径的 4 种失败分级
/// （链不完整 / 域名不匹配 / 自签 / 过期）。
///
/// 所有证书 2048-bit RSA + SHA256withRSA，兼容 TLS 1.2/1.3。issuerDn==null 表示自签。
public final class TestCerts {

    static {
        if (Security.getProvider("BC") == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    private TestCerts() {
    }

    /// 签发结果：叶子证书、完整链（[leaf, ca] 或 [leaf] 自签时）、叶子 KeyPair、CA 私钥（叶子私钥在 leafKeyPair）。
    public record Issued(
            X509Certificate leaf,
            X509Certificate[] chain,
            KeyPair leafKeyPair,
            PrivateKey caPrivateKey
    ) {
    }

    /// SAN 集合：既支持 DNS 名也支持 IP 字节的 SAN。
    public record Sans(String[] dnsNames, byte[][] ipAddresses) {
    }

    public static Sans sans(String... dnsNames) {
        return new Sans(dnsNames, new byte[][]{});
    }

    public static Sans sans(String[] dnsNames, byte[]... ipAddresses) {
        return new Sans(dnsNames, ipAddresses);
    }

    /// 签发一张叶子证书。
    ///
    /// @param daysValid 有效期天数；负数 = 已过期（notBefore~now, notBefore+abs(days)）。
    ///                 有效证书请传 >= 2，留出边界缓冲。
    /// @param sanDns    叶子 SAN DNS 列表；null/空 = 无 SAN（只有 CN）。verifyHostname 会通过 SAN 匹配失败判定域名不匹配。
    /// @param issuerDn  颁发者 DN；= null → 自签（issuer == subject）。非 null → CA 签发（链不完整场景用）。
    public static Issued issue(int daysValid, String[] sanDns, String issuerDn) throws Exception {
        var caKpg = KeyPairGenerator.getInstance("RSA");
        caKpg.initialize(2048);
        var caPair = caKpg.generateKeyPair();

        boolean selfSigned = (issuerDn == null);
        // self-signed: 签发者 DN 即叶子自身 DN，签名者即叶子密钥 —— leaf == issuer 结构。
        // non-self-signed: CA 独立密钥与 DN，叶子由 CA 签发。

        var leafKpg = KeyPairGenerator.getInstance("RSA");
        leafKpg.initialize(2048);
        var leafPair = leafKpg.generateKeyPair();   // 先生成叶子 key，再决定 signer

        String firstSan = (sanDns != null && sanDns.length > 0) ? sanDns[0] : "no-san.local";
        var leafSubject = new X500Name("CN=" + firstSan);

        KeyPair signerPair = selfSigned ? leafPair : caPair;        // 签名者
        var caDn = selfSigned ? leafSubject
                : new X500Name(issuerDn != null ? issuerDn : "CN=Unknown CA, O=SiteGuard Test");

        var now = System.currentTimeMillis();
        Date from;
        Date to;
        if (daysValid < 0) {
            to = new Date(now - 86_400_000L);
            from = new Date(to.getTime() - ((long) Math.abs(daysValid)) * 86_400_000L);
        } else {
            from = new Date(now);
            to = new Date(now + ((long) daysValid) * 86_400_000L);
        }

        var serial = BigInteger.probablePrime(64, new java.security.SecureRandom());

        X509v3CertificateBuilder leafBuilder = new JcaX509v3CertificateBuilder(
                caDn, serial, from, to, leafSubject, leafPair.getPublic()
        );
        leafBuilder.addExtension(Extension.basicConstraints, true, new BasicConstraints(false));
        leafBuilder.addExtension(Extension.keyUsage, true,
                new KeyUsage(KeyUsage.digitalSignature | KeyUsage.keyEncipherment));

        if (sanDns != null && sanDns.length > 0) {
            GeneralName[] gns = new GeneralName[sanDns.length];
            for (int i = 0; i < sanDns.length; i++) {
                gns[i] = new GeneralName(GeneralName.dNSName, sanDns[i]);
            }
            leafBuilder.addExtension(Extension.subjectAlternativeName, false, new GeneralNames(gns));
        }

        String sigAlg = "SHA256withRSA";
        ContentSigner signer = new JcaContentSignerBuilder(sigAlg).setProvider("BC").build(signerPair.getPrivate());
        X509CertificateHolder holder = leafBuilder.build(signer);
        X509Certificate leafCert = new JcaX509CertificateConverter().setProvider("BC").getCertificate(holder);

        X509Certificate[] chain = selfSigned
                ? new X509Certificate[]{leafCert}
                : new X509Certificate[]{leafCert, caCert(caDn, caPair, sigAlg)};

        return new Issued(leafCert, chain, leafPair, signerPair.getPrivate());
    }

    private static X509Certificate caCert(X500Name caDn, KeyPair caPair, String sigAlg) throws Exception {
        var now = System.currentTimeMillis();
        var from = new Date(now - 86_400_000L);
        var to = new Date(now + 10L * 365 * 86_400_000L);
        var serial = BigInteger.probablePrime(64, new java.security.SecureRandom());
        X509v3CertificateBuilder b = new JcaX509v3CertificateBuilder(
                caDn, serial, from, to, caDn, caPair.getPublic()
        );
        b.addExtension(Extension.basicConstraints, true, new BasicConstraints(true));
        b.addExtension(Extension.keyUsage, true, new KeyUsage(KeyUsage.keyCertSign | KeyUsage.cRLSign));
        ContentSigner signer = new JcaContentSignerBuilder(sigAlg).setProvider("BC").build(caPair.getPrivate());
        return new JcaX509CertificateConverter().setProvider("BC").getCertificate(b.build(signer));
    }
}
