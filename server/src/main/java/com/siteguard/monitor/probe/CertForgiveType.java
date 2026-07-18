package com.siteguard.monitor.probe;

/// SSL 握手失败类型，strict 校验失败 + trust-all 重连成功后由 [HttpSiteProbe] 分类。
///
/// "证书过期" 不在此枚举中，永远不放——见 [HttpSiteProbe#probeLenient] 里的
/// X509Certificate.checkValidity() 分支，过期直接返回 ERROR 且不上报到本枚举的判定链路。
///
/// 字符串值即 DB 与 JSON 中存储的英文短语，小写下划线、人类直读。
/// 修改任何枚举的 value 等于破坏已有数据，新增时用过的 value 不允许复用作其他语义。
///
/// 注意：本类不依赖 Jackson 注解（项目当前 Jackson 绑定不含 tools.jackson.annotation 包），
/// 序列化/反序列化由 [CertForgive] 通过 getValue()/fromValue() 显式完成。
public enum CertForgiveType {

    /// 服务器未发送中间 CA，JDK 默认信任库报 PKIX path building failed，
    /// 浏览器通常能 AIA 补链正常访问。
    CHAIN_INCOMPLETE("chain_incomplete"),

    /// 证书 SAN/CN 与实际访问 host 不一致（多域名共用证书、或内网按 IP 直连）。
    DOMAIN_MISMATCH("domain_mismatch"),

    /// issuer DN == subject DN 的自签证书（内部系统、测试环境）。
    SELF_SIGNED("self_signed");

    /// 序列化值。DB/JSON 直读自解释。
    private final String value;

    CertForgiveType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    /// 小写短语 → 枚举。遇到不认识的值静默返回 null，保证向前兼容
    /// （未来新增枚举时，老数据里没这个值不会抛反序列化异常）。probe 层因此需容忍 null。
    public static CertForgiveType fromValue(String v) {
        if (v == null) return null;
        for (var t : values()) {
            if (t.value.equals(v)) return t;
        }
        return null;
    }
}
