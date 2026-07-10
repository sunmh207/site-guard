import type { RobotPlatform } from '../types/notification-setting.dto'

/// USelect 下拉选项（按显示顺序）
export const ROBOT_PLATFORM_OPTIONS: { label: string, value: RobotPlatform }[] = [
  { label: '钉钉', value: 'DINGTALK' },
  { label: '企业微信', value: 'WECHAT_WORK' },
  { label: '飞书', value: 'FEISHU' },
]

/// 平台枚举 → 中文标签
export const PLATFORM_LABEL_MAP: Record<RobotPlatform, string> = {
  DINGTALK: '钉钉',
  WECHAT_WORK: '企业微信',
  FEISHU: '飞书',
}