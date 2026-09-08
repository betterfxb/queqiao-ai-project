package com.queqiao.server.common;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * 统一分页返回体。前端 http.ts 直接取 res.json()，因此这里返回裸结构：
 * { list: [...], total: n, page: 1, size: 20 }
 */
public record PageResult<T>(List<T> list, long total, int page, int size) {

    public static <T> PageResult<T> of(Page<T> p) {
        return new PageResult<>(p.getContent(), p.getTotalElements(), p.getNumber() + 1, p.getSize());
    }

    public static <T> PageResult<T> of(List<T> all, int page, int size) {
        int from = Math.max(0, (page - 1) * size);
        int to = Math.min(all.size(), from + size);
        List<T> slice = from >= all.size() ? List.of() : all.subList(from, to);
        return new PageResult<>(slice, all.size(), page, size);
    }

    public static <T> PageResult<T> all(List<T> all) {
        return new PageResult<>(all, all.size(), 1, Math.max(all.size(), 1));
    }
}
