package com.siteguard.monitor.probe;

import javax.net.ssl.X509TrustManager;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

/// 包装型 X509TrustManager：把对端证书链"顺手"抓下来供探活结果使用，
/// 验证行为完全透传给被包装的 delegate。
///
/// 用途：java.net.http.HttpClient 不暴露 SSLSession，握手阶段是唯一能拿到对端
/// 证书的机会，所以通过一个能捕获的 TrustManager 把证书从握手过程中抠出来。
///
/// 生命周期：必须 per-probe 创建一个新实例，绑定到对应 HttpClient 上。
/// 原因：JDK HttpClient 的 SSL 握手跑在 selector 线程上，跟发起 send() 的调用线程不是同一个；
/// 若共用一个 TM + HttpClient，多个并发探活会相互覆盖 capturedChain。
///
/// 内存可见性：握手线程写 capturedChain（volatile），调用线程在 client.send() 返回后读，
/// 同一 HttpClient 的 send() 内部有 happens-before 保证，volatile 提供额外保证。
public class CapturingTrustManager implements X509TrustManager {

    private final X509TrustManager delegate;

    /// 握手阶段抓到的对端证书链。volatile 跨线程可见。
    private volatile X509Certificate[] capturedChain;

    public CapturingTrustManager(X509TrustManager delegate) {
        this.delegate = delegate;
    }

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        delegate.checkClientTrusted(chain, authType);
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        // 即使后续 delegate 抛 CertificateException，证书链也已经记下
        this.capturedChain = chain;
        delegate.checkServerTrusted(chain, authType);
    }

    @Override
    public X509Certificate[] getAcceptedIssuers() {
        return delegate.getAcceptedIssuers();
    }

    /// 取出最近一次捕获的证书链。null 表示没有。
    public X509Certificate[] getCapturedChain() {
        return capturedChain;
    }

    /// 清空。HttpSiteProbe 读完后调用，避免下次同 TM 复用时读到旧值。
    public void clearCapturedChain() {
        this.capturedChain = null;
    }
}
