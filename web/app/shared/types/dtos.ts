/**
 * 共享通用类型定义
 */

/**
 * 基础实体接口
 */
export interface BaseDTO {
  id: string
  createdAt: string
  updatedAt: string
}

/**
 * 系统信息接口
 */
export interface SystemInfoDTO {
  version: string
  buildTime: string
  environment: string
  licenseStatus: string
  currentProjectCount: number
  projectMaxCount: number
  canCreateProject: boolean
  remainingProjectCount: number
}

