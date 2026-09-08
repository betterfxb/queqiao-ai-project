package com.queqiao.server.common;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/** P0-8：金额 BigDecimal 精确运算 */
public class MoneyTest {

    @Test
    void addAvoidsDoubleError() {
        assertEquals(0, Money.add(BigDecimal.valueOf(0.1), BigDecimal.valueOf(0.2))
                .compareTo(new BigDecimal("0.30")));
    }

    @Test
    void percentScalesToTwo() {
        assertEquals(0, Money.percent(new BigDecimal("100"), 10.0)
                .compareTo(new BigDecimal("10.00")));
        assertEquals(0, Money.percent(new BigDecimal("99.99"), 33.0)
                .compareTo(new BigDecimal("32.997").setScale(2, java.math.RoundingMode.HALF_UP)));
    }

    @Test
    void negateAndAbs() {
        assertEquals(0, Money.negate(new BigDecimal("5")).compareTo(new BigDecimal("-5.00")));
        assertEquals(0, Money.abs(new BigDecimal("-5")).compareTo(new BigDecimal("5.00")));
    }

    @Test
    void gtZeroAndNz() {
        assertTrue(Money.gtZero(new BigDecimal("0.01")));
        assertFalse(Money.gtZero(BigDecimal.ZERO));
        assertFalse(Money.gtZero(null));
        assertEquals(0, Money.nz(null).compareTo(Money.ZERO));
    }

    @Test
    void sumSkipsNullEntries() {
        java.util.List<BigDecimal> list = new java.util.ArrayList<>();
        list.add(new BigDecimal("1"));
        list.add(null);
        list.add(new BigDecimal("2"));
        assertEquals(0, Money.sum(list, x -> x).compareTo(new BigDecimal("3.00")));
    }
}
