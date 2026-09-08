package com.queqiao.server.common;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.function.Function;

/**
 * 金额工具（P0-8）。
 *
 * <p>所有钱字段一律使用 {@link BigDecimal} 存储与计算，统一 2 位小数、HALF_UP，
 * 杜绝 {@code double} 累加带来的 0.1+0.2=0.30000000000000004 类误差与对账差异。</p>
 */
public final class Money {

    public static final int SCALE = 2;
    public static final BigDecimal ZERO = BigDecimal.ZERO.setScale(SCALE, RoundingMode.HALF_UP);
    /** 百分比换算基数 */
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private Money() {
    }

    /** 统一标度 */
    public static BigDecimal scale(BigDecimal v) {
        return v == null ? null : v.setScale(SCALE, RoundingMode.HALF_UP);
    }

    /** null → 0.00 */
    public static BigDecimal nz(BigDecimal v) {
        return v == null ? ZERO : scale(v);
    }

    /** 从任意入参（Number / String / null）解析金额，非法值抛业务异常 */
    public static BigDecimal of(Object v) {
        if (v == null) return null;
        if (v instanceof BigDecimal b) return scale(b);
        if (v instanceof Number n) return scale(BigDecimal.valueOf(n.doubleValue()));
        String s = String.valueOf(v).trim();
        if (s.isEmpty()) return null;
        try {
            return scale(new BigDecimal(s));
        } catch (NumberFormatException e) {
            throw new BizException("金额格式不正确：" + s);
        }
    }

    /** 从任意入参解析金额，null / 非法 → 0.00 */
    public static BigDecimal ofOrZero(Object v) {
        try {
            BigDecimal b = of(v);
            return b == null ? ZERO : b;
        } catch (BizException e) {
            return ZERO;
        }
    }

    public static BigDecimal add(BigDecimal a, BigDecimal b) {
        return scale(nz(a).add(nz(b)));
    }

    public static BigDecimal sub(BigDecimal a, BigDecimal b) {
        return scale(nz(a).subtract(nz(b)));
    }

    public static BigDecimal abs(BigDecimal a) {
        return nz(a).abs();
    }

    public static BigDecimal negate(BigDecimal a) {
        return nz(a).negate();
    }

    /** 按百分比取值：amount * ratePercent / 100 */
    public static BigDecimal percent(BigDecimal amount, Double ratePercent) {
        if (amount == null || ratePercent == null) return ZERO;
        return scale(amount.multiply(BigDecimal.valueOf(ratePercent)).divide(HUNDRED, SCALE, RoundingMode.HALF_UP));
    }

    /** 乘以整数倍数（如单价 × 人数） */
    public static BigDecimal times(BigDecimal amount, long factor) {
        return scale(nz(amount).multiply(BigDecimal.valueOf(factor)));
    }

    public static boolean gtZero(BigDecimal v) {
        return v != null && v.compareTo(BigDecimal.ZERO) > 0;
    }

    public static boolean ltZero(BigDecimal v) {
        return v != null && v.compareTo(BigDecimal.ZERO) < 0;
    }

    /** 金额是否相等（忽略标度差异） */
    public static boolean eq(BigDecimal a, BigDecimal b) {
        return nz(a).compareTo(nz(b)) == 0;
    }

    public static <T> BigDecimal sum(Collection<T> list, Function<T, BigDecimal> get) {
        BigDecimal total = BigDecimal.ZERO;
        for (T t : list) {
            BigDecimal v = get.apply(t);
            if (v != null) total = total.add(v);
        }
        return scale(total);
    }

    /** 仅用于前端展示 / 旧接口兼容的 double 转换，禁止用于计算 */
    public static double toDouble(BigDecimal v) {
        return nz(v).doubleValue();
    }
}
