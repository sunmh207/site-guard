/// Webhook URL 脱敏显示工具。
///
/// 短 URL（<=12 字符）全部打码；
/// 长 URL 保留前半段 + **** + 末 4 位，避免完整 token 泄露。
///
/// 原本属于 NotificationSettingCard 的辅助函数，
/// 在此独立以便其它展示 Webhook 的页面复用。
export function maskWebhook(url: string): string {
  if (!url) return ''
  if (url.length <= 12) return '****'
  const half = url.length / 2 | 0
  return url.slice(0, half) + '****' + url.slice(-4)
}
