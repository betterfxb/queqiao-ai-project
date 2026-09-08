package com.queqiao.server.common;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/** 动态查询条件构造器（避免每个模块重复写 Specification） */
public final class Specs {

    private Specs() {
    }

    public static <T> Builder<T> of() {
        return new Builder<>();
    }

    public static boolean has(String s) {
        return s != null && !s.isBlank() && !"全部".equals(s) && !"all".equalsIgnoreCase(s);
    }

    public static class Builder<T> {
        private final List<SpecFn<T>> fns = new ArrayList<>();

        /** 等值（值为空则忽略） */
        public Builder<T> eq(String field, Object value) {
            if (value != null && !(value instanceof String s && !has(s))) {
                fns.add((root, cb) -> cb.equal(root.get(field), value));
            }
            return this;
        }

        /** 模糊匹配（值为空则忽略） */
        public Builder<T> like(String field, String value) {
            if (has(value)) {
                fns.add((root, cb) -> cb.like(root.get(field), "%" + value.trim() + "%"));
            }
            return this;
        }

        /** 多字段 OR 模糊（关键词搜索） */
        public Builder<T> keyword(String value, String... fields) {
            if (has(value) && fields.length > 0) {
                String v = "%" + value.trim() + "%";
                fns.add((root, cb) -> {
                    Predicate[] ps = new Predicate[fields.length];
                    for (int i = 0; i < fields.length; i++) {
                        ps[i] = cb.like(root.get(fields[i]).as(String.class), v);
                    }
                    return cb.or(ps);
                });
            }
            return this;
        }

        /** 数值区间 */
        public Builder<T> between(String field, Integer min, Integer max) {
            if (min != null) fns.add((root, cb) -> cb.greaterThanOrEqualTo(root.get(field), min));
            if (max != null) fns.add((root, cb) -> cb.lessThanOrEqualTo(root.get(field), max));
            return this;
        }

        /** 布尔 */
        public Builder<T> is(String field, Boolean value) {
            if (value != null) fns.add((root, cb) -> cb.equal(root.get(field), value));
            return this;
        }

        public Specification<T> build() {
            return (root, query, cb) -> {
                List<Predicate> ps = new ArrayList<>();
                for (SpecFn<T> fn : fns) ps.add(fn.apply(root, cb));
                return ps.isEmpty() ? cb.conjunction() : cb.and(ps.toArray(new Predicate[0]));
            };
        }
    }

    @FunctionalInterface
    private interface SpecFn<T> {
        Predicate apply(jakarta.persistence.criteria.Root<T> root, jakarta.persistence.criteria.CriteriaBuilder cb);
    }
}
