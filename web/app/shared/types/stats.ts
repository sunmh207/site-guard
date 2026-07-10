/**
 * 项目统计项
 */
export interface ProjectStatsItem {
  projectName: string
  commitCount: number
  averageScore: number
}

/**
 * 提交者统计项
 */
export interface AuthorStatsItem {
  author: string
  commitCount: number
  averageScore: number
}

/**
 * 代码变更统计项
 */
export interface CodeChangeStatsItem {
  name: string
  additions: number
  deletions: number
}

/**
 * 统计数据类型定义
 */
export interface StatsData {
  activeProjects: number
  contributors: number
  totalCommits: number
  averageScore: number
  projectCommitStats: ProjectStatsItem[]
  projectAverageScoreStats: ProjectStatsItem[]
  authorCommitStats: AuthorStatsItem[]
  authorAverageScoreStats: AuthorStatsItem[]
  projectCodeChangeStats: CodeChangeStatsItem[]
  authorCodeChangeStats: CodeChangeStatsItem[]
}

/**
 * 日期范围类型
 */
export type DateRangeType = 'week' | 'twoWeeks' | 'month' | 'custom'

/**
 * 日期范围选项
 */
export interface DateRangeOption {
  label: string
  value: DateRangeType
}
