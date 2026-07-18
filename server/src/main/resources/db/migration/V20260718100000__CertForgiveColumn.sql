-- 证书校验失败分级放行（cert_forgive）
--
-- 当 JDK strict 握手失败（SSLHandshakeException）时，probe 用 trust-all 重连一次。
-- 重连成功 → 按失败类型判定是否"放过"（仍判 UP），仍按证书过期永不放过。
--
-- cert_forgive : TEXT, 存储 JSON 字符串数组，枚举值来自 CertForgiveType。
--
-- 例：
--   - NULL / []                                  : 全不放（默认，对齐 JDK 严格校验）
--   - ["chain_incomplete"]                       : 仅放链不完整
--   - ["chain_incomplete","domain_mismatch"]     : 放两种
--   - ["chain_incomplete","domain_mismatch","self_signed"] : 全放（仍不放过期）
--
-- "证书过期" 不在此列中：probe 在归类前先调 X509Certificate.checkValidity()，
-- 过期直接返回 ERROR（ProbeResult.expired），不走本列的判定链路。
--
-- 向前兼容：CertForgiveType.fromValue 静默忽略未知枚举值，
-- 未来新增类型时老数据里没这个值不会抛反序列化异常。

ALTER TABLE `site`
    ADD COLUMN `cert_forgive` TEXT NULL COMMENT
        '证书校验失败分级放行集合，JSON 字符串数组（chain_incomplete / domain_mismatch / self_signed）；null 或空=全不放' AFTER `consecutive_availability_failures`;
