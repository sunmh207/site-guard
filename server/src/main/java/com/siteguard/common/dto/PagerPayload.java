package com.siteguard.common.dto;

import lombok.Getter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

@Getter
public class PagerPayload<T> {

    public PagerPayload(Page<T> pageData, Pageable pageRequest) {
        this.data = pageData.getContent();
        this.total = pageData.getTotalElements();
        // 将 Spring 的 0-based 页码转换为前端的 1-based 页码
        this.page = pageRequest.getPageNumber() + 1;
        this.size = pageRequest.getPageSize();
    }

    public PagerPayload(List<T> data, Long total, Pageable pageRequest) {
        this.data = data;
        this.total = total;
        // 将 Spring 的 0-based 页码转换为前端的 1-based 页码
        this.page = pageRequest.getPageNumber() + 1;
        this.size = pageRequest.getPageSize();
    }

    private final List<T> data;

    /// 当前页码，从 1 开始计算
    private final int page;

    /// 每页条目数量
    private final int size;

    /// 总条目数
    private final long total;
}

