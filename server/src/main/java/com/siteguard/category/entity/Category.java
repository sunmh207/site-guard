package com.siteguard.category.entity;

import com.siteguard.common.persistent.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/// 站点分类实体（树状，邻接表）
///
/// 主从关系靠业务层维护，不加 DB FK。systemFlag=1 表示系统默认分类，不可删。
@Entity
@Table(name = "category")
@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class Category extends BaseEntity {

    /// 父分类 ID；null 表示根节点
    @Column(name = "parent_id")
    private Long parentId;

    /// 分类名称（同一 parent 下唯一，1-64 字符）
    @Column(nullable = false, length = 64)
    private String name;

    /// 兄弟节点间的排序权重
    @Column(name = "seq", nullable = false)
    private Integer seq;

    /// 系统标记：1 = 默认分类（受保护），0 = 普通
    @Column(name = "system_flag", nullable = false)
    private Boolean systemFlag;
}