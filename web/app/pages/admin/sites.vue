<script setup lang="tsx">
/// 站点管理后台列表页（Bundle 10：分类树 + 表格 双栏布局）。
///
/// - 左栏：CategoryContextMenu 包裹的 CategoryTree，右键/拖拽/选中节点
/// - 右栏：filter inputs + UTable（带行首拖拽手柄）+ 分页
/// - 抽屉：SiteEditSlideover（创建/编辑），CategoryEditSlideover（新建/重命名）
/// - 弹窗：UModal（站点删除），DeleteCategoryModal（分类删除）
///
/// 与 /admin/users 风格保持一致，但增加 useCategoryTree 状态联动。
import { UButton, UDropdownMenu, UModal } from '#components'
import type { DropdownMenuItem } from '@nuxt/ui'
import type { Row } from '@tanstack/vue-table'
import { adminSiteApi } from '~/features/site/api/site.api'
import type { SiteDto } from '~/features/site/types/site.dto'
import SiteStatusCell from '~/features/site/components/SiteStatusCell.vue'
import SiteCertificateCell from '~/features/site/components/SiteCertificateCell.vue'
import SiteEditSlideover from '~/features/site/components/SiteEditSlideover.vue'
import SiteCheckHistorySlideover from '~/features/site/components/SiteCheckHistorySlideover.vue'
import SitePathRuleSlideover from '~/features/site-path-rule/components/SitePathRuleSlideover.vue'
import { useCategoryTree } from '~/features/category/composables/useCategoryTree'
import { adminCategoryApi } from '~/features/category/api/category.api'
import type { CategoryTreeNode } from '~/features/category/types/category.dto'
import CategoryContextMenu from '~/features/category/components/CategoryContextMenu.vue'
import CategoryTree from '~/features/category/components/CategoryTree.vue'
import CategoryEditSlideover from '~/features/category/components/CategoryEditSlideover.vue'
import DeleteCategoryModal from '~/features/category/components/DeleteCategoryModal.vue'

definePageMeta({
  layout: 'admin',
})

const message = useMessage()

/// 构造搜索条件 + 分页。conditions 不含 page/size。
/// availabilityStatus 用空字符串表示"全部"（用于后端搜索条件）。
/// 但 USelect 不允许空字符串作为 item value（Nuxt UI 4 会在 SSR 渲染时抛 500），
/// 所以 UI 层用 'ALL' 作为 sentinel，computed 里做转换。
/// categoryId 用于按分类过滤；null 表示"全部分类"。
const { conditions, pager, pageSizeOptions } = useSearchPagination({
  keyword: '',
  availabilityStatus: '' as '' | 'UNKNOWN' | 'UP' | 'DOWN',
  categoryId: null as number | null,
})

/// 把 availabilityStatus 字段用 statusFilter 这个 ref 暴露给模板便于双向绑定
const statusFilter = computed<'ALL' | 'UNKNOWN' | 'UP' | 'DOWN'>({
  get: () => (conditions.availabilityStatus === '' ? 'ALL' : conditions.availabilityStatus),
  set: (v) => {
    conditions.availabilityStatus = v === 'ALL' ? '' : v
  },
})

/// 分类树状态（左栏 + 联动搜索条件）
const cat = useCategoryTree()
await cat.refresh()

/// 首次拉取后，把分类树默认选中的节点同步到搜索条件
if (cat.selectedId.value != null) {
  conditions.categoryId = cat.selectedId.value
}

/// 拉取列表（useFetch 风格，条件变化自动重发）
const { data: rows, refresh } = await adminSiteApi.searchSites(conditions as any, pager)

/// 分类树选中节点变化 → 写入搜索条件（useSearchPagination 自带 page=1 重置）
watch(() => cat.selectedId.value, (v) => {
  conditions.categoryId = v
})

/// 创建 / 编辑 站点 slideover 状态
const openSlideoverSite = ref<SiteDto | null>(null)
const createOpen = ref(false)
const editOpen = ref(false)

/// 复制场景的 slideover 状态。独立于 createOpen / editOpen，便于 v-if 显隐
const duplicateOpen = ref(false)
const prefillSource = ref<{ name: string, url: string, categoryId: number } | null>(null)

/// 子路由检测 slideover 状态：独立于 create/edit/duplicate，从下拉菜单直接触发
const pathRuleOpen = ref(false)
const pathRuleTarget = ref<SiteDto | null>(null)

/// 探测历史 slideover 状态：点击"站点名称"列触发，独立于编辑/子路由/删除等动作
const historyOpen = ref(false)
const historyTarget = ref<SiteDto | null>(null)

/// 点击站点名称：记录目标并打开探测历史 slideover
function openHistory(site: SiteDto) {
  historyTarget.value = site
  historyOpen.value = true
}

function openCreate() {
  openSlideoverSite.value = null
  createOpen.value = true
}

function openEdit(site: SiteDto) {
  openSlideoverSite.value = site
  editOpen.value = true
}

/// 从列表下拉菜单触发的子路由检测入口：记录目标并打开独立 slideover
function openPathRule(site: SiteDto) {
  pathRuleTarget.value = site
  pathRuleOpen.value = true
}

/// 复制：构造 prefill（name 追加" 复制"）并打开 SiteEditSlideover create 模式
/// paused 状态不继承：createSite 后端默认 paused=false，新站点进入扫描
function requestDuplicate(site: SiteDto) {
  prefillSource.value = {
    name: `${site.name} 复制`,
    url: site.url,
    categoryId: site.categoryId,
  }
  duplicateOpen.value = true
}

function onSaved() {
  refresh()
}

/// 暂停 / 恢复 二次确认
const pauseTarget = ref<SiteDto | null>(null)
const pauseTargetPaused = ref<boolean>(false)
const pauseModalOpen = ref(false)
const pauseLoading = ref(false)

const pauseDescription = computed(() => {
  const s = pauseTarget.value
  if (!s) return ''
  return pauseTargetPaused.value
    ? `确定要暂停站点「${s.name}」吗？暂停后该站点将不再被扫描。`
    : `确定要恢复对站点「${s.name}」的监控吗？`
})

const pauseModalTitle = computed(() =>
  pauseTargetPaused.value ? '确认暂停' : '确认恢复',
)

function requestPause(site: SiteDto, target: boolean) {
  pauseTarget.value = site
  pauseTargetPaused.value = target
  pauseModalOpen.value = true
}

async function confirmPause() {
  if (!pauseTarget.value) return
  pauseLoading.value = true
  try {
    await adminSiteApi.setPaused(pauseTarget.value.id, pauseTargetPaused.value)
    message.success(pauseTargetPaused.value ? '站点已暂停' : '站点已恢复')
    pauseModalOpen.value = false
    pauseTarget.value = null
    await refresh()
  }
  catch {
    message.error(pauseTargetPaused.value ? '暂停失败' : '恢复失败')
  }
  finally {
    pauseLoading.value = false
  }
}

/// 站点删除二次确认
const deleteTarget = ref<SiteDto | null>(null)
const deleteModalOpen = ref(false)
const deleteLoading = ref(false)

const deleteDescription = computed(() => {
  const s = deleteTarget.value
  if (!s)
    return ''
  return `确定要删除站点「${s.name}」吗？此操作不可恢复。`
})

function requestDelete(site: SiteDto) {
  deleteTarget.value = site
  deleteModalOpen.value = true
}

async function confirmDelete() {
  if (!deleteTarget.value)
    return
  deleteLoading.value = true
  try {
    await adminSiteApi.deleteSite(deleteTarget.value.id)
    message.success('站点已删除')
    deleteModalOpen.value = false
    deleteTarget.value = null
    await refresh()
  }
  catch {
    message.error('删除失败')
  }
  finally {
    deleteLoading.value = false
  }
}

/// 分类相关 slideover / modal 状态
const catEditOpen = ref(false)
const editParent = ref<CategoryTreeNode | null>(null)
const editNode = ref<CategoryTreeNode | null>(null)

const delOpen = ref(false)
const delNode = ref<CategoryTreeNode | null>(null)

/// 右键命中节点（驱动全局右键菜单）
const ctxNode = ref<CategoryTreeNode | null>(null)

/// 右键入口：记录命中节点；不调用 preventDefault，让事件冒泡到 UContextMenu 的 trigger。
/// reka-ui 的 trigger 在 nextTick 后检查 event.defaultPrevented，
/// 若已被 prevent 则不会打开自己的菜单，所以这里只能由 trigger 自己来屏蔽浏览器原生菜单。
function onCategoryContext(node: CategoryTreeNode) {
  ctxNode.value = node
  /// 顺手把节点也选中（与左键点击一致）
  cat.select(node.id)
}

/// 新建子分类
function onCreateChild(parent: CategoryTreeNode) {
  editParent.value = parent
  editNode.value = null
  catEditOpen.value = true
}

/// 顶部按钮入口：创建根分类（没有父分类）。
/// 走"editParent=null"路径，让 CategoryEditSlideover 进入 create 模式 + parent=null；
/// 后端 POST /category/create 已支持 parentId=null。
function onCreateRootCategory() {
  editParent.value = null
  editNode.value = null
  catEditOpen.value = true
}

/// 重命名
function onRename(node: CategoryTreeNode) {
  editNode.value = node
  editParent.value = null
  catEditOpen.value = true
}

/// 移动分类到新父节点
async function onMoveTo(node: CategoryTreeNode, targetId: number) {
  try {
    await adminCategoryApi.update({ id: node.id, parentId: targetId })
    message.success('分类已移动')
    await cat.refresh()
  }
  catch (e: any) {
    message.error(e?.data?.message ?? e?.message ?? '移动失败')
  }
}

/// 请求删除分类（打开确认弹窗）
function onDeleteRequest(node: CategoryTreeNode) {
  delNode.value = node
  delOpen.value = true
}

/// 分类增/改/删成功后：刷新树 + 刷新表格。
/// 新建模式下传入新建节点，自动把该节点设为当前选中；
/// CategoryTree 内的 `expanded` 仅控制子节点可见性，根节点行始终渲染，
/// 因此新建根分类时无需手动展开，行本身就出现在树顶层并被高亮。
async function onCategoryChanged(newNode?: CategoryTreeNode) {
  await cat.refresh()
  if (newNode) cat.select(newNode.id)
  await refresh()
}

/// 拖拽站点行的 dragstart：把 id 列表写入 dataTransfer
function onRowDragStart(e: DragEvent, site: SiteDto) {
  if (!e.dataTransfer) return
  e.dataTransfer.setData('text/site-ids', JSON.stringify([site.id]))
  e.dataTransfer.effectAllowed = 'move'
}

/// 拖到分类树上：批量移动站点 + 同步刷新。
/// 不切换 cat.selectedId：用户连续拖动时焦点保持当前分类，便于继续拖动其他站点。
/// refresh() 仍调用，让表格反映站点已移出；cat.refresh() 同步树上的 siteCount。
async function onDropSites(siteIds: number[], targetId: number) {
  try {
    await adminSiteApi.moveSites(siteIds, targetId)
    message.success(`已移动 ${siteIds.length} 个站点`)
    await cat.refresh()
    await refresh()
  }
  catch (e: any) {
    message.error(e?.data?.message ?? e?.message ?? '移动失败')
  }
}

/// 状态过滤选项 - 全部状态用 'ALL' sentinel（USelect 不接受空 value）
const statusOptions = [
  { label: '全部可用性状态', value: 'ALL' },
  { label: '未检测', value: 'UNKNOWN' },
  { label: '在线', value: 'UP' },
  { label: '离线', value: 'DOWN' },
]

const columns = [
  {
    /// 拖拽手柄列：把站点行"握住"拖到分类树上即可修改分类
    id: 'drag',
    header: '',
    cell: ({ row }: { row: Row<SiteDto> }) => (
      <div
        draggable="true"
        class="cursor-move text-muted select-none px-2"
        title="拖动以修改分类"
        onDragstart={(e: DragEvent) => onRowDragStart(e, row.original)}
      >⋮⋮</div>
    ),
  },
  {
    accessorKey: 'id',
    header: 'ID',
    cell: ({ row }: { row: Row<SiteDto> }) => <div>{row.original.id}</div>,
  },
  {
    accessorKey: 'name',
    header: '站点名称',
    /// 站点名称改为可点击按钮：触发探测历史 slideover。
    /// 视觉上与 URL 列的"链接"风格保持一致（text-primary + hover:underline），
    /// 但语义上是按钮（type="button"，避免被当作表单提交）；点击区域限制在文字本身。
    /// onClick 用块语句包裹（{}），确保返回值是 void，与 UTable cell 回调签名一致。
    cell: ({ row }: { row: Row<SiteDto> }) => (
      <button
        type="button"
        class="text-primary hover:underline cursor-pointer text-left"
        title="点击查看探测历史"
        onClick={() => { openHistory(row.original) }}
      >
        {row.original.name || '-'}
      </button>
    ),
  },
  {
    accessorKey: 'url',
    header: 'URL',
    cell: ({ row }: { row: Row<SiteDto> }) => (
      <a
        href={row.original.url}
        target="_blank"
        rel="noopener noreferrer"
        class="text-primary hover:underline"
      >
        {row.original.url}
      </a>
    ),
  },
  {
    accessorKey: 'availabilityStatus',
    header: '可用性',
    cell: ({ row }: { row: Row<SiteDto> }) => (
      <SiteStatusCell
        status={row.original.availabilityStatus}
        paused={row.original.paused}
      />
    ),
  },
  {
    accessorKey: 'certificateExpiresAt',
    header: '证书到期',
    cell: ({ row }: { row: Row<SiteDto> }) => (
      <SiteCertificateCell expiresAt={row.original.certificateExpiresAt} />
    ),
  },
  {
    id: 'actions',
    header: '操作',
    cell: ({ row }: { row: Row<SiteDto> }) => {
      const menuItems: DropdownMenuItem[] = [
        { type: 'label', label: '操作' },
        { label: '编辑', icon: 'i-lucide-pencil', onSelect: () => openEdit(row.original) },
        row.original.paused
          ? { label: '恢复', icon: 'i-lucide-play', onSelect: () => requestPause(row.original, false) }
          : { label: '暂停', icon: 'i-lucide-pause', onSelect: () => requestPause(row.original, true) },
        /// 复制：name 追加" 复制" 后打开 create slideover，paused 状态不继承
        { label: '复制', icon: 'i-lucide-copy', onSelect: () => requestDuplicate(row.original) },
        /// 子路由检测：打开独立 slideover，避免与编辑抽屉共用导致两个"保存"按钮歧义
        { label: '子路由检测', icon: 'i-lucide-route', onSelect: () => openPathRule(row.original) },
        { label: '删除', icon: 'i-lucide-trash', color: 'error', onSelect: () => requestDelete(row.original) },
      ]
      return (
        <div class="text-right">
          <UDropdownMenu content={{ align: 'end' }} items={menuItems}>
            <UButton icon="i-lucide-ellipsis-vertical" color="neutral" variant="ghost" class="ml-auto" />
          </UDropdownMenu>
        </div>
      )
    },
  },
]

async function handleSearch() {
  pager.page = 1
  await refresh()
}

async function resetFilters() {
  conditions.keyword = ''
  statusFilter.value = 'ALL'
  /// 分类条件重置为"全部分类"（清空搜索过滤但保留树的选中状态高亮）
  conditions.categoryId = null
  pager.page = 1
  await refresh()
}
</script>

<template>
  <UDashboardPanel id="dashboard">
    <template #header>
      <UDashboardNavbar title="站点管理">
        <template #leading>
          <UDashboardSidebarCollapse />
        </template>
        <template #right>
          <UButton icon="i-lucide-plus" label="新建站点" @click="openCreate" />
        </template>
      </UDashboardNavbar>
    </template>

    <template #body>
      <div class="flex gap-4 h-full">
        <aside class="w-[260px] border-r border-default pr-3 overflow-auto">
          <!--
            顶部"新建根分类"按钮：sticky 在树滚动时保持可见。
            后端 POST /category/create 已支持 parentId=null，这里复用
            CategoryEditSlideover 的 create 模式（parent=null 即代表"根"）。
          -->
          <div class="sticky top-0 z-10 bg-default pb-2">
            <UButton
              icon="i-lucide-plus"
              label="新建分类"
              color="primary"
              variant="solid"
              block
              @click="onCreateRootCategory"
            />
          </div>
          <CategoryContextMenu
            :node="ctxNode"
            :all-nodes="cat.tree.value"
            @create-child="onCreateChild"
            @rename="onRename"
            @delete="onDeleteRequest"
            @move-to="onMoveTo"
          >
            <CategoryTree
              :tree="cat.tree.value"
              :selected-id="cat.selectedId.value"
              @select="(id) => cat.select(id)"
              @context-menu="onCategoryContext"
              @drop-sites="onDropSites"
            />
          </CategoryContextMenu>
        </aside>

        <section class="flex-1 flex flex-col min-w-0">
          <div class="flex items-center gap-2 flex-wrap">
            <UInput
              v-model="conditions.keyword"
              placeholder="请输入站点名称"
              class="w-full max-w-sm"
              @keyup.enter="handleSearch"
            />
            <USelect
              v-model="statusFilter"
              :items="statusOptions"
              class="w-40"
            />
            <UButton label="查询" @click="handleSearch" />
            <UButton label="重置" color="neutral" variant="outline" @click="resetFilters" />
          </div>

          <UTable :data="rows?.data || []" :columns="columns" class="mt-4 flex-1" />

          <div class="mt-4 flex items-center justify-between">
            <div class="text-sm text-default">
              共 {{ rows?.total || 0 }} 条
            </div>
            <div class="flex items-center gap-3">
              <UPagination
                v-model:page="pager.page"
                :items-per-page="pager.size"
                :total="rows?.total || 0"
                :max="7"
              />
              <USelect v-model="pager.size" :items="pageSizeOptions" class="w-26" />
            </div>
          </div>
        </section>
      </div>

      <SiteEditSlideover
        v-if="createOpen"
        v-model:open="createOpen"
        :site="null"
        :category-options="cat.options.value"
        :default-category-id="cat.selectedId.value ?? 0"
        @ok="onSaved"
      />
      <SiteEditSlideover
        v-if="openSlideoverSite"
        v-model:open="editOpen"
        :site="openSlideoverSite"
        :category-options="cat.options.value"
        :default-category-id="cat.selectedId.value ?? 0"
        @ok="onSaved"
      />
      <SiteEditSlideover
        v-if="duplicateOpen"
        v-model:open="duplicateOpen"
        :site="null"
        :prefill="prefillSource"
        :category-options="cat.options.value"
        :default-category-id="cat.selectedId.value ?? 0"
        @ok="onSaved"
      />

      <SitePathRuleSlideover
        v-if="pathRuleTarget"
        v-model:open="pathRuleOpen"
        :site-id="pathRuleTarget.id"
        :site-name="pathRuleTarget.name"
      />

      <!--
        探测历史 slideover：仅在 historyTarget 已设置时挂载，
        避免对所有站点行同时渲染 panel（参见 SiteCheckHistoryPanel.vue 注释）。
      -->
      <SiteCheckHistorySlideover
        v-if="historyTarget"
        v-model:open="historyOpen"
        :site-id="historyTarget.id"
        :site-name="historyTarget.name"
      />

      <UModal
        v-model:open="deleteModalOpen"
        title="确认删除"
        :description="deleteDescription"
      >
        <template #footer>
          <div class="flex w-full justify-end gap-3">
            <UButton
              label="取消"
              color="neutral"
              variant="subtle"
              :disabled="deleteLoading"
              @click="deleteModalOpen = false"
            />
            <UButton
              label="删除"
              color="error"
              variant="solid"
              :loading="deleteLoading"
              @click="confirmDelete"
            />
          </div>
        </template>
      </UModal>

      <UModal
        v-model:open="pauseModalOpen"
        :title="pauseModalTitle"
        :description="pauseDescription"
      >
        <template #footer>
          <div class="flex w-full justify-end gap-3">
            <UButton
              label="取消"
              color="neutral"
              variant="subtle"
              :disabled="pauseLoading"
              @click="pauseModalOpen = false"
            />
            <UButton
              :label="pauseTargetPaused ? '暂停' : '恢复'"
              :color="pauseTargetPaused ? 'warning' : 'primary'"
              variant="solid"
              :loading="pauseLoading"
              @click="confirmPause"
            />
          </div>
        </template>
      </UModal>

      <CategoryEditSlideover
        v-if="catEditOpen"
        v-model:open="catEditOpen"
        :parent="editParent"
        :node="editNode"
        @ok="onCategoryChanged"
      />
      <DeleteCategoryModal
        v-if="delOpen"
        v-model:open="delOpen"
        :node="delNode"
        :options="cat.options.value"
        @ok="onCategoryChanged"
      />
    </template>
  </UDashboardPanel>
</template>
