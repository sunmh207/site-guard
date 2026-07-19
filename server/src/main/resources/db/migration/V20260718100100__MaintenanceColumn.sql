-- 站点运维时段（maintenance）
--
-- 在指定时段内,站点按"自动暂停"处理 —— 跳过探测 + 告警,避免定期"运维"误报。
-- 效果与 Site.paused 同质,只是触发方式从"人工开关"升级为"按时间表自动开关"。
--
-- maintenance : VARCHAR(256), 存储 JSON 对象字符串；NULL / 空 / "{}" = 未启用(默认)。
--
-- 结构:
--   {"start":"22:00","end":"08:00","days":["MON","TUE","WED","THU","FRI"]}
--
-- 字段:
--   - start / end: 必填,24 小时制 "HH:mm";不可相等(相等 = 0 长度或 24 小时,二义性)
--   - days       : 可选,MON..SUN 子集(Monday..Sunday 的 3 字母短名);
--                  不传 = 全周(最常见场景,默认够用)
--
-- 约定:
--   - start > end: 视为跨日窗口(22:00-08:00),一行判定 t >= start || t < end
--   - start < end: 视为普通窗口(09:00-18:00),判定 start <= t < end
--   - 解析失败(非法格式 / 非法天数 / start==end)→ 整体退回未启用,站点保持 24h 监控
--
-- 例:
--   - NULL / "" / "{}"                              : 未启用,站点 24h 监控
--   - {"start":"22:00","end":"08:00"}              : 每天 22:00-次日 08:00(跨日)
--   - {"start":"22:00","end":"08:00","days":["MON","TUE","WED","THU","FRI"]}
--                                                    : 工作日夜间运维
--
-- 判定路径: com.siteguard.site.entity.SiteMaintenance.isInMaintenance
--
-- 时区: 默认服务器时区(ZoneId.systemDefault)。中国不用 DST,暂不引入 timezone 字段;
--       未来海外部署时再追加 maintenance_tz 列,本迁移预留语义位置,不加列。
--
-- 向前兼容: MaintenanceWindow.parse 静默忽略未知 key;未来加字段不会让老数据解析失败。

ALTER TABLE `site`
    ADD COLUMN `maintenance` VARCHAR(256) NULL COMMENT
        '运维时段 JSON 对象,每日该时段跳过探测+告警;例 {"start":"22:00","end":"08:00","days":["MON"]};null/空/"{}"=未启用' AFTER `cert_forgive`;
