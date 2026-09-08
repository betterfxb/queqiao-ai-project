package com.queqiao.server.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** P0-7：订单状态机流转守卫 */
public class OrderStatusTest {

    @Test
    void allowedTransitions() {
        assertTrue(OrderStatus.待支付.canTransitTo(OrderStatus.已完成));
        assertTrue(OrderStatus.处理中.canTransitTo(OrderStatus.已完成));
        assertTrue(OrderStatus.已完成.canTransitTo(OrderStatus.已退款));
    }

    @Test
    void illegalTransitionsRejected() {
        assertFalse(OrderStatus.已退款.canTransitTo(OrderStatus.已完成));
        assertFalse(OrderStatus.已完成.canTransitTo(OrderStatus.处理中));
        assertFalse(OrderStatus.处理中.canTransitTo(OrderStatus.待支付));
    }

    @Test
    void sameStateIsIdempotent() {
        assertTrue(OrderStatus.已完成.canTransitTo(OrderStatus.已完成));
    }

    @Test
    void parseInvalidReturnsNull() {
        assertNull(OrderStatus.parse("不存在"));
        assertNull(OrderStatus.parse(null));
        assertEquals(OrderStatus.已完成, OrderStatus.parse(" 已完成 "));
    }
}
