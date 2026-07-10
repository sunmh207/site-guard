package com.siteguard.category;

import com.siteguard.category.repository.CategoryRepository;
import com.siteguard.category.service.impl.CategoryServiceImpl;
import com.siteguard.site.repository.SiteRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategoryServiceImplTest {

    @Mock
    CategoryRepository repo;

    @Mock
    SiteRepository siteRepo;

    @InjectMocks
    CategoryServiceImpl service;

    @Test
    void tree_emptyRepo_seedsDefaultAndReturnsSingleRoot() {
        /// 库为空时 tree() 走 seed 分支：repo.count()==0 → 插入默认分类 → 直接返回单节点
        /// （不调用 findAllByOrderBySeqAscIdAsc / siteRepo.findAll / findFirstBySystemFlagTrue）
        when(repo.count()).thenReturn(0L);
        when(repo.save(any(com.siteguard.category.entity.Category.class)))
                .thenAnswer(inv -> {
                    var c = (com.siteguard.category.entity.Category) inv.getArgument(0);
                    c.setId(1L);
                    return c;
                });

        var tree = service.tree();

        verify(repo, times(1)).save(any(com.siteguard.category.entity.Category.class));
        assertEquals(1, tree.size());
        assertEquals("默认分类", tree.get(0).getName());
        assertEquals(true, tree.get(0).getSystemFlag());
        assertEquals(0L, tree.get(0).getSiteCount());
    }

    @Test
    void descendantIds_returnsSelfAndAllDescendants() {
        var c1 = new com.siteguard.category.entity.Category(); c1.setId(1L); c1.setParentId(null);
        var c2 = new com.siteguard.category.entity.Category(); c2.setId(2L); c2.setParentId(1L);
        var c3 = new com.siteguard.category.entity.Category(); c3.setId(3L); c3.setParentId(2L);
        var c4 = new com.siteguard.category.entity.Category(); c4.setId(4L); c4.setParentId(null);

        when(repo.findAllByOrderBySeqAscIdAsc()).thenReturn(List.of(c1, c2, c3, c4));

        var ids = service.descendantIds(1L);

        assertEquals(new java.util.HashSet<>(List.of(1L, 2L, 3L)), ids);
    }

    @Test
    void create_ok() {
        var params = new com.siteguard.category.dto.CategoryCreateParams();
        params.setParentId(1L);
        params.setName("浙江");

        when(repo.existsByParentIdAndName(1L, "浙江")).thenReturn(false);
        when(repo.save(any(com.siteguard.category.entity.Category.class)))
                .thenAnswer(inv -> {
                    var c = (com.siteguard.category.entity.Category) inv.getArgument(0);
                    c.setId(2L);
                    return c;
                });

        var node = service.create(params);

        assertEquals(2L, node.getId());
        assertEquals("浙江", node.getName());
        assertEquals(1L, node.getParentId());
    }

    @Test
    void create_duplicateName_throwsConflict() {
        var params = new com.siteguard.category.dto.CategoryCreateParams();
        params.setParentId(1L);
        params.setName("浙江");

        when(repo.existsByParentIdAndName(1L, "浙江")).thenReturn(true);

        var ex = assertThrows(com.siteguard.common.exception.AppException.class,
                () -> service.create(params));
        assertEquals(409, ex.getStatus());
        verify(repo, never()).save(any(com.siteguard.category.entity.Category.class));
    }

    @Test
    void update_systemFlag_cannotChangeParent() {
        var params = new com.siteguard.category.dto.CategoryUpdateParams();
        params.setId(1L);
        params.setParentId(2L);

        var c = new com.siteguard.category.entity.Category();
        c.setId(1L); c.setSystemFlag(true); c.setName("默认分类"); c.setParentId(null);

        when(repo.findById(1L)).thenReturn(java.util.Optional.of(c));

        var ex = assertThrows(com.siteguard.common.exception.AppException.class,
                () -> service.update(params));
        assertEquals(403, ex.getStatus());
    }

    @Test
    void update_moveToDescendant_throwsInvalidParent() {
        var params = new com.siteguard.category.dto.CategoryUpdateParams();
        params.setId(1L);
        params.setParentId(2L); // 2 是 1 的后代

        var c1 = new com.siteguard.category.entity.Category();
        c1.setId(1L); c1.setSystemFlag(false); c1.setName("A");
        var c2 = new com.siteguard.category.entity.Category();
        c2.setId(2L); c2.setSystemFlag(false); c2.setName("B"); c2.setParentId(1L);

        when(repo.findById(1L)).thenReturn(java.util.Optional.of(c1));
        when(repo.findAllByOrderBySeqAscIdAsc()).thenReturn(List.of(c1, c2));

        var ex = assertThrows(com.siteguard.common.exception.AppException.class,
                () -> service.update(params));
        assertEquals(422, ex.getStatus());
    }

    @Test
    void update_rename_ok() {
        var params = new com.siteguard.category.dto.CategoryUpdateParams();
        params.setId(1L);
        params.setName("我的站点");

        var c = new com.siteguard.category.entity.Category();
        c.setId(1L); c.setSystemFlag(true); c.setName("默认分类"); c.setSeq(0);

        when(repo.findById(1L)).thenReturn(java.util.Optional.of(c));
        when(repo.existsByParentIdAndNameAndIdNot(null, "我的站点", 1L)).thenReturn(false);
        when(repo.save(any(com.siteguard.category.entity.Category.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        /// update() 仅在 params.getParentId()!=null 时才会调用 descendantIds → findAllByOrderBySeqAscIdAsc；
        /// 本测试只传 name，因此不触发整树加载，无需 stub。

        var node = service.update(params);
        assertEquals("我的站点", node.getName());
    }

    @Test
    void update_renameDuplicate_throwsConflict() {
        var params = new com.siteguard.category.dto.CategoryUpdateParams();
        params.setId(1L);
        params.setName("已存在");

        var c = new com.siteguard.category.entity.Category();
        c.setId(1L); c.setSystemFlag(false); c.setName("A"); c.setSeq(0);

        when(repo.findById(1L)).thenReturn(java.util.Optional.of(c));
        when(repo.existsByParentIdAndNameAndIdNot(null, "已存在", 1L)).thenReturn(true);

        var ex = assertThrows(com.siteguard.common.exception.AppException.class,
                () -> service.update(params));
        assertEquals(409, ex.getStatus());
    }

    @Test
    void delete_systemFlag_throwsForbidden() {
        var c = new com.siteguard.category.entity.Category();
        c.setId(1L); c.setSystemFlag(true); c.setName("默认分类");

        when(repo.findById(1L)).thenReturn(java.util.Optional.of(c));

        var ex = assertThrows(com.siteguard.common.exception.AppException.class,
                () -> service.delete(1L, 2L));
        assertEquals(403, ex.getStatus());
        verify(repo, never()).deleteById(any());
    }

    @Test
    void delete_hasChildren_throwsConflict() {
        var c = new com.siteguard.category.entity.Category();
        c.setId(1L); c.setSystemFlag(false); c.setName("A");
        var child = new com.siteguard.category.entity.Category();
        child.setId(2L); child.setParentId(1L); child.setSystemFlag(false);

        when(repo.findById(1L)).thenReturn(java.util.Optional.of(c));
        when(repo.findAllByOrderBySeqAscIdAsc()).thenReturn(List.of(c, child));
        when(repo.existsById(3L)).thenReturn(true);

        var ex = assertThrows(com.siteguard.common.exception.AppException.class,
                () -> service.delete(1L, 3L));
        assertEquals(409, ex.getStatus());
    }

    @Test
    void delete_withSites_relocatesAndDeletes() {
        var c = new com.siteguard.category.entity.Category();
        c.setId(1L); c.setSystemFlag(false); c.setName("A");

        when(repo.findById(1L)).thenReturn(java.util.Optional.of(c));
        when(repo.findAllByOrderBySeqAscIdAsc()).thenReturn(List.of(c));
        when(repo.existsById(99L)).thenReturn(true);

        service.delete(1L, 99L);

        verify(siteRepo, times(1)).updateCategoryIdBulk(1L, 99L);
        verify(repo, times(1)).delete(c);
    }

    @Test
    void tree_siteCount_aggregatesSelfPlusAllDescendants() {
        /// 树形：Root(id=1) → A(id=2) → B(id=3)，同 parent 下还有 C(id=4) 直接挂在 Root 下
        /// 站点分布：3 个挂在 B，1 个挂在 C
        /// 期望 siteCount：
        ///   Root = 4（A 没有直接站点；B 有 3，C 有 1 → 后代合计 4）
        ///   A    = 3（B 有 3）
        ///   B    = 3（自身 3）
        ///   C    = 1（自身 1）
        var c1 = new com.siteguard.category.entity.Category();
        c1.setId(1L); c1.setParentId(null); c1.setName("Root"); c1.setSystemFlag(true);
        var c2 = new com.siteguard.category.entity.Category();
        c2.setId(2L); c2.setParentId(1L); c2.setName("A"); c2.setSystemFlag(false);
        var c3 = new com.siteguard.category.entity.Category();
        c3.setId(3L); c3.setParentId(2L); c3.setName("B"); c3.setSystemFlag(false);
        var c4 = new com.siteguard.category.entity.Category();
        c4.setId(4L); c4.setParentId(1L); c4.setName("C"); c4.setSystemFlag(false);

        var s1 = new com.siteguard.site.entity.Site(); s1.setCategoryId(3L);
        var s2 = new com.siteguard.site.entity.Site(); s2.setCategoryId(3L);
        var s3 = new com.siteguard.site.entity.Site(); s3.setCategoryId(3L);
        var s4 = new com.siteguard.site.entity.Site(); s4.setCategoryId(4L);

        when(repo.count()).thenReturn(4L);
        when(repo.findAllByOrderBySeqAscIdAsc()).thenReturn(List.of(c1, c2, c3, c4));
        when(siteRepo.findAll()).thenReturn(List.of(s1, s2, s3, s4));

        var tree = service.tree();

        /// 找各节点的 helper（用 byName 代替 byId，避免依赖遍历顺序）
        var root = findByName(tree, "Root");
        var a = findByName(root.getChildren(), "A");
        var b = findByName(a.getChildren(), "B");
        var c = findByName(root.getChildren(), "C");

        assertEquals(4L, root.getSiteCount(), "Root 应聚合所有后代站点");
        assertEquals(3L, a.getSiteCount(), "A 应聚合 B 的站点");
        assertEquals(3L, b.getSiteCount(), "B 应等于自身直接站点");
        assertEquals(1L, c.getSiteCount(), "C 应等于自身直接站点");
    }

    private static com.siteguard.category.dto.CategoryTreeNode findByName(
            List<com.siteguard.category.dto.CategoryTreeNode> nodes, String name) {
        for (var n : nodes) {
            if (name.equals(n.getName())) return n;
        }
        throw new AssertionError("未找到节点 " + name);
    }
}