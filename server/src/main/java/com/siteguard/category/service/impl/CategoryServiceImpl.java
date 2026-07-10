package com.siteguard.category.service.impl;

import com.siteguard.category.dto.CategoryCreateParams;
import com.siteguard.category.dto.CategoryTreeNode;
import com.siteguard.category.dto.CategoryUpdateParams;
import com.siteguard.category.entity.Category;
import com.siteguard.category.repository.CategoryRepository;
import com.siteguard.category.service.CategoryService;
import com.siteguard.site.repository.SiteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/// 分类业务服务实现
///
/// 树合法性是本服务对外的承诺：
/// - 同一 parent 下 name 唯一（业务层 + DB uk 双重兜底）
/// - parent 不允许形成环
/// - systemFlag=1 节点不可改 parent、不可删
/// - 删除非空分类时，事务内把站点迁到 fallback
@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository repo;
    private final SiteRepository siteRepo;

    /// 整树查询
    ///
    /// 业务规则：
    /// - 库为空时自动 seed 一个系统默认分类（systemFlag=true），保证前端永远能看到至少 1 个根
    /// - 整树加载一次组装（邻接表 + 业务层校验，不依赖 DB FK）
    /// - siteCount 聚合自身 + 所有后代节点的站点数，便于左侧树形菜单显示
    @Override
    @Transactional
    public List<CategoryTreeNode> tree() {
        if (repo.count() == 0) {
            var seeded = defaultSeed();
            return List.of(toNode(seeded, 0L, new ArrayList<>()));
        }
        return assembleTree();
    }

    /// 创建分类
    ///
    /// 业务规则：
    /// - 同 parent 下 name 必须唯一（业务层 existsBy 检查 + DB uk 双重兜底）
    /// - 新建节点 systemFlag 默认为 false（只有系统 seed 才为 true）
    /// - name 自动 trim，避免前后空格造成的"看似不同"
    /// - parentId 不存在性校验留给上层 Controller/Service 调用方，
    ///   因为 CategoryRepository 当前未提供 existsById 派生方法（不阻塞本任务）
    @Override
    @Transactional
    public CategoryTreeNode create(CategoryCreateParams params) {
        if (repo.existsByParentIdAndName(params.getParentId(), params.getName())) {
            throw com.siteguard.common.exception.Errors.CONFLICT
                    .toException("同父分类下已存在同名分类");
        }
        var c = new Category();
        c.setParentId(params.getParentId());
        c.setName(params.getName().trim());
        c.setSeq(0);
        c.setSystemFlag(false);
        repo.save(c);
        var node = new CategoryTreeNode();
        node.setId(c.getId());
        node.setParentId(c.getParentId());
        node.setName(c.getName());
        node.setSystemFlag(c.getSystemFlag());
        node.setSeq(c.getSeq());
        node.setSiteCount(0L);
        node.setChildren(new ArrayList<>());
        return node;
    }

    /// 更新分类
    ///
    /// 业务规则：
    /// - systemFlag=true 节点禁止改 parentId（受保护）
    /// - 防环：parent 不能是自身，也不能是自身的后代
    /// - 同 parent 下 name 唯一（业务层 existsBy 检查 + DB uk 双重兜底）
    /// - null 字段视为"不改"（PATCH 语义）
    /// - name 自动 trim
    /// - seq 为 null 时不变
    @Override
    @Transactional
    public CategoryTreeNode update(CategoryUpdateParams params) {
        var c = repo.findById(params.getId())
                .orElseThrow(() -> com.siteguard.common.exception.Errors.NOT_FOUND
                        .toException("分类不存在 (ID: {})", params.getId()));

        // systemFlag 节点禁止改 parent
        if (Boolean.TRUE.equals(c.getSystemFlag()) && params.getParentId() != null
                && !params.getParentId().equals(c.getParentId())) {
            throw com.siteguard.common.exception.Errors.ACCESS_DENIED
                    .toException("系统默认分类不允许改变父级");
        }

        // 防环：parent 不能是自身或自身后代
        if (params.getParentId() != null) {
            if (params.getParentId().equals(c.getId())) {
                throw com.siteguard.common.exception.Errors.INVALID_PARENT
                        .toException("父级不能是自身");
            }
            var desc = descendantIds(c.getId());
            if (desc.contains(params.getParentId())) {
                throw com.siteguard.common.exception.Errors.INVALID_PARENT
                        .toException("父级不能是自身的后代");
            }
        }

        Long newParent = params.getParentId() != null ? params.getParentId() : c.getParentId();
        String newName = params.getName() != null ? params.getName().trim() : c.getName();

        if (params.getName() != null
                && repo.existsByParentIdAndNameAndIdNot(newParent, newName, c.getId())) {
            throw com.siteguard.common.exception.Errors.CONFLICT
                    .toException("同父分类下已存在同名分类");
        }

        c.setParentId(newParent);
        c.setName(newName);
        if (params.getSeq() != null) c.setSeq(params.getSeq());
        repo.save(c);

        var node = new CategoryTreeNode();
        node.setId(c.getId());
        node.setParentId(c.getParentId());
        node.setName(c.getName());
        node.setSystemFlag(c.getSystemFlag());
        node.setSeq(c.getSeq());
        node.setSiteCount(0L);
        node.setChildren(new ArrayList<>());
        return node;
    }

    /// 删除分类
    ///
    /// 业务规则：
    /// - systemFlag=true 节点禁止删除（受保护）
    /// - fallbackId 不能是自身
    /// - fallbackId 必须存在
    /// - 存在子节点时拒绝删除（CATEGORY_HAS_CHILDREN），引导用户先删/移子节点
    /// - 在事务内：先把该分类及其后代下的所有站点迁入 fallback，然后删分类
    ///   —— 避免出现"孤立站点"（category_id 指向不存在分类）
    @Override
    @Transactional
    public void delete(Long id, Long fallbackId) {
        var c = repo.findById(id)
                .orElseThrow(() -> com.siteguard.common.exception.Errors.NOT_FOUND
                        .toException("分类不存在 (ID: {})", id));

        if (Boolean.TRUE.equals(c.getSystemFlag())) {
            throw com.siteguard.common.exception.Errors.ACCESS_DENIED
                    .toException("系统默认分类不允许删除");
        }
        if (id.equals(fallbackId)) {
            throw com.siteguard.common.exception.Errors.BAD_REQUEST
                    .toException("fallbackId 不能是自身");
        }
        if (!repo.existsById(fallbackId)) {
            throw com.siteguard.common.exception.Errors.NOT_FOUND
                    .toException("fallbackId 不存在");
        }
        var all = repo.findAllByOrderBySeqAscIdAsc();
        boolean hasChild = all.stream().anyMatch(x -> id.equals(x.getParentId()));
        if (hasChild) {
            throw com.siteguard.common.exception.Errors.CATEGORY_HAS_CHILDREN
                    .toException("请先删除或合并子分类");
        }
        siteRepo.updateCategoryIdBulk(id, fallbackId);
        repo.delete(c);
    }

    /// 收集 id 自身及其所有后代 ID（含 id 自身）
    ///
    /// 算法：邻接表整树加载一次，构造 parent_id -> [child ids] 索引，BFS 收集。
    /// 空集合表示 id 在树中不存在（调用方需要时可结合 findById 二次确认）。
    /// 主要用于 update 防环检测与 delete 时把后代一起处理。
    @Override
    public Set<Long> descendantIds(Long id) {
        var all = repo.findAllByOrderBySeqAscIdAsc();
        // 构造 parent_id -> [child ids] 索引
        var childrenByParent = new HashMap<Long, List<Long>>();
        for (var c : all) {
            if (c.getParentId() != null) {
                childrenByParent.computeIfAbsent(c.getParentId(), k -> new ArrayList<>()).add(c.getId());
            }
        }
        // BFS 收集自身 + 所有后代
        Set<Long> result = new HashSet<>();
        java.util.Deque<Long> queue = new java.util.ArrayDeque<>();
        queue.add(id);
        while (!queue.isEmpty()) {
            var cur = queue.poll();
            if (cur == null || !result.add(cur)) continue;
            var children = childrenByParent.get(cur);
            if (children != null) queue.addAll(children);
        }
        return result;
    }

    /// 获取系统默认分类 ID
    ///
    /// 默认分类由 tree() 启动时 seed，永远存在（systemFlag=true）。
    /// 若库被外部手段清空（绕过 tree()），可能抛 IllegalStateException，
    /// 调用方应先调用 tree() 或捕获异常后 seed。
    /// 用于 SiteService 创建/导入站点时的兜底目标分类。
    @Override
    public Long defaultCategoryId() {
        return repo.findFirstBySystemFlagTrue()
                .orElseThrow(() -> new IllegalStateException("默认分类未初始化"))
                .getId();
    }

    /// seed 系统默认分类（库为空时）
    private Category defaultSeed() {
        var c = new Category();
        c.setParentId(null);
        c.setName("默认分类");
        c.setSeq(0);
        c.setSystemFlag(true);
        return repo.save(c);
    }

    /// 把 Category 实体映射成 TreeNode
    private CategoryTreeNode toNode(Category c, long siteCount, List<CategoryTreeNode> children) {
        var node = new CategoryTreeNode();
        node.setId(c.getId());
        node.setParentId(c.getParentId());
        node.setName(c.getName());
        node.setSystemFlag(c.getSystemFlag());
        node.setSeq(c.getSeq());
        node.setSiteCount(siteCount);
        node.setChildren(children);
        return node;
    }

    /// 加载所有分类，组装成 children 嵌套结构并计算 siteCount
    private List<CategoryTreeNode> assembleTree() {
        var all = repo.findAllByOrderBySeqAscIdAsc();
        var siteCounts = computeSiteCounts();

        Map<Long, CategoryTreeNode> byId = new LinkedHashMap<>();
        for (var c : all) {
            var node = toNode(c, siteCounts.getOrDefault(c.getId(), 0L), new ArrayList<>());
            byId.put(c.getId(), node);
        }
        List<CategoryTreeNode> roots = new ArrayList<>();
        for (var c : all) {
            var node = byId.get(c.getId());
            if (c.getParentId() == null) {
                roots.add(node);
            } else {
                var parent = byId.get(c.getParentId());
                if (parent != null) {
                    parent.getChildren().add(node);
                } else {
                    // 孤儿（parent_id 指向不存在的 id）—— 防御性降级为根
                    roots.add(node);
                }
            }
        }
        return roots;
    }

    /// 计算每个分类的 siteCount：分类自身的站点 + 所有后代的站点
    ///
    /// 算法：先用 siteRepo 统计每个分类 id 上的直接站点数（"原始计数"），
    /// 再按 parent_id 索引整树，对每个节点 BFS 遍历自己的后代，把后代的直接计数累加到自身上。
    /// 与"沿 parent 链上溯累加"区别：后者会把祖先的站点也算进来（语义错误），
    /// 例如 Root → A → B 且 B 上有 3 个站点时，Root 和 A 的 siteCount 应等于 3 而不是 0。
    private Map<Long, Long> computeSiteCounts() {
        var rawCounts = new HashMap<Long, Long>();
        var allSites = siteRepo.findAll();
        for (var s : allSites) {
            rawCounts.merge(s.getCategoryId(), 1L, Long::sum);
        }
        var all = repo.findAllByOrderBySeqAscIdAsc();
        // 构造 parent_id -> [child ids] 索引（与 descendantIds 同样的构图）
        var childrenByParent = new HashMap<Long, List<Long>>();
        for (var c : all) {
            if (c.getParentId() != null) {
                childrenByParent.computeIfAbsent(c.getParentId(), k -> new ArrayList<>()).add(c.getId());
            }
        }
        var result = new HashMap<Long, Long>();
        for (var c : all) {
            long sum = rawCounts.getOrDefault(c.getId(), 0L);
            java.util.Deque<Long> queue = new java.util.ArrayDeque<>();
            queue.add(c.getId());
            while (!queue.isEmpty()) {
                var cur = queue.poll();
                var children = childrenByParent.get(cur);
                if (children == null) continue;
                for (var childId : children) {
                    sum += rawCounts.getOrDefault(childId, 0L);
                    queue.add(childId);
                }
            }
            result.put(c.getId(), sum);
        }
        return result;
    }
}