/**
 * 日期工具函数
 */

/**
 * 获取指定天数前的日期（时间设置为 00:00:00）
 */
export function getDaysAgo(days: number): Date {
  const date = new Date()
  date.setDate(date.getDate() - days)
  date.setHours(0, 0, 0, 0)
  return date
}

/**
 * 获取今天的结束时间（23:59:59）
 */
export function getEndOfToday(): Date {
  const date = new Date()
  date.setHours(23, 59, 59, 999)
  return date
}

/**
 * 将日期转换为毫秒时间戳
 */
export function dateToTimestamp(date: Date): number {
  return date.getTime()
}

/**
 * 格式化日期为 YYYY-MM-DD
 */
export function formatDate(date: Date): string {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

/**
 * 从 YYYY-MM-DD 字符串解析日期
 */
export function parseDate(dateStr: string): Date {
  const date = new Date(dateStr)
  date.setHours(0, 0, 0, 0)
  return date
}

/**
 * 验证日期范围
 */
export function validateDateRange(startDate: Date, endDate: Date): { valid: boolean; error?: string } {
  // 开始时间必须小于结束时间
  if (startDate >= endDate) {
    return { valid: false, error: '开始日期必须小于结束日期' }
  }

  return { valid: true }
}
