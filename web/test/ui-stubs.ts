/// @nuxt/ui 组件的 vitest 测试桩。
///
/// @nuxt/ui 的组件在运行时依赖 Nuxt 的虚拟模块（#build/ui/* / #imports），
/// 在 vitest happy-dom 环境下无法解析。这里提供一组最小桩：渲染默认 / 具名 slot，
/// 使 wrapper.text() 能拿到内容、对纯展示型组件做断言。
///
/// - UCard   转发 #header 与默认 slot
/// - UTable  把 :data 渲染为 <ul><li>...</li></ul>：每个 accessorKey 优先尝试
///           `<col.accessorKey>-cell` slot；没有具名 slot 时回落到 `row[col.accessorKey]` 原文。
///           这样 #xxx-cell 槽位里渲染的中文标签 / 图标 / UBadge 都会被收集进 wrapper.text()。
/// - UIcon   渲染 :name（icon 名称字符串足够支撑"是否传了图标"的断言）
/// - UBadge  转发默认 slot
/// - USkeleton / UDashboardPanel / UDashboardNavbar / UDashboardSidebarCollapse / UButton
///           页面级 Dashboard 测试需要：dashboard 布局 + 加载骨架 + 按钮（loading 态）。
import type { Component } from 'vue'

export const uiStubs: Record<string, Component> = {
  UCard: {
    template: '<div><slot name="header" /><slot /></div>',
  } as Component,
  UTable: {
    props: ['data', 'columns'],
    template:
      '<ul>'
      + '<li v-for="(row, i) in (data || [])" :key="i">'
      + '<span v-for="col in (columns || [])" :key="col.accessorKey">'
      + '<slot v-if="$slots[col.accessorKey + \'-cell\']" :name="col.accessorKey + \'-cell\'" :row="{ original: row }" />'
      + '<template v-else>{{ row[col.accessorKey] }}</template>'
      + '</span>'
      + '</li>'
      + '</ul>',
  } as Component,
  UIcon: {
    props: ['name'],
    template: '<i>{{ name }}</i>',
  } as Component,
  UBadge: {
    template: '<span><slot /></span>',
  } as Component,
  USkeleton: {
    template: '<div data-testid="skeleton"><slot /></div>',
  } as Component,
  UDashboardPanel: {
    template: '<section><slot name="header" /><slot name="body" /></section>',
  } as Component,
  UDashboardNavbar: {
    template: '<header><slot name="leading" /><slot /><slot name="right" /></header>',
  } as Component,
  UDashboardSidebarCollapse: {
    template: '<button type="button"><slot /></button>',
  } as Component,
  UButton: {
    props: ['loading', 'icon'],
    template: '<button type="button" :disabled="loading"><slot /></button>',
  } as Component,
  /// 站点大屏内的业务组件：测试只关心"渲染没报错 + 卡片/告警出现"，
  /// 不展开内部断言。这里透传默认 slot，让 wrapper.text() 仍能拿到子内容。
  DashboardSummaryCards: {
    template: '<div data-testid="summary-cards"><slot /></div>',
  } as Component,
  RecentAlertsTable: {
    template: '<div data-testid="recent-alerts"><slot /></div>',
  } as Component,
  DashboardSkeleton: {
    template: '<div data-testid="dashboard-skeleton"><slot /></div>',
  } as Component,
}
