package com.queqiao.server.common;

/**
 * 订单状态机：待支付 → 处理中 → 已完成 → 已退款。
 * 非法流转由 {@link #canTransitTo(OrderStatus)} 拒绝，保证收款/退款状态正确。
 */
public enum OrderStatus {

    待支付,
    处理中,
    已完成,
    已退款;

    /** 终态：不再允许任何向外流转 */
    public boolean isTerminal() {
        return this == 已退款;
    }

    /** 是否允许从当前态流转到 to（同态视为幂等放行） */
    public boolean canTransitTo(OrderStatus to) {
        if (to == null) return false;
        if (this == to) return true;
        return switch (this) {
            case 待支付 -> to == 处理中 || to == 已完成 || to == 已退款;
            case 处理中 -> to == 已完成 || to == 已退款;
            case 已完成 -> to == 已退款;
            case 已退款 -> false;
        };
    }

    /** 安全解析（忽略大小写/空白；非法或 null 返回 null，调用方据此走兼容逻辑） */
    public static OrderStatus parse(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return OrderStatus.valueOf(s.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
