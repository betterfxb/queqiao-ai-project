package com.queqiao.server.service;

import com.queqiao.server.common.BizException;
import com.queqiao.server.common.Ids;
import com.queqiao.server.entity.FinanceOrder;
import com.queqiao.server.repo.FinanceOrderRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * P0-7 集成测试：退款状态机守卫 + 幂等，支付幂等。
 * 使用内存 H2，整体上下文启动即作为冒烟测试（contextLoads）。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
public class FinanceServiceIT {

    @Autowired
    FinanceService financeService;
    @Autowired
    FinanceOrderRepository orderRepo;

    private FinanceOrder paidOrder() {
        FinanceOrder o = new FinanceOrder();
        o.setId(Ids.biz("FO"));
        o.setOrderNo("T" + System.nanoTime());
        o.setMemberId("M1");
        o.setMember("测试会员");
        o.setType("收款");
        o.setAmount(new BigDecimal("100.00"));
        o.setStatus("已完成");
        o.setOrderType("新购");
        return orderRepo.save(o);
    }

    @Test
    void refundGeneratesNegativeOrderAndMarksRefunded() {
        FinanceOrder o = paidOrder();
        FinanceOrder r = financeService.refund(o.getId(), null, "用户申请");
        assertEquals("退款", r.getType());
        assertTrue(r.getAmount().compareTo(BigDecimal.ZERO) < 0, "退款金额应为负");
        FinanceOrder reloaded = orderRepo.findById(o.getId()).orElseThrow();
        assertEquals("已退款", reloaded.getStatus());
    }

    @Test
    void refundIsIdempotent() {
        FinanceOrder o = paidOrder();
        FinanceOrder r1 = financeService.refund(o.getId(), null, "r1");
        long before = orderRepo.count();
        FinanceOrder r2 = financeService.refund(o.getId(), null, "r2");
        long after = orderRepo.count();
        assertEquals(before, after, "重复退款不应新增订单");
        assertEquals(r1.getId(), r2.getId());
    }

    @Test
    void payIsIdempotent() {
        FinanceOrder o = paidOrder();
        FinanceOrder p = financeService.pay(o.getId(), "线下", "OFFLINE-1");
        long before = orderRepo.count();
        financeService.pay(o.getId(), "线下", "OFFLINE-2");
        long after = orderRepo.count();
        assertEquals(before, after, "重复支付不应新增订单");
        assertEquals("已完成", p.getStatus());
    }

    @Test
    void refundAlreadyRefundedIsIdempotent() {
        FinanceOrder o = paidOrder();
        financeService.refund(o.getId(), null, "first");
        long before = orderRepo.count();
        financeService.refund(o.getId(), null, "second");
        assertEquals(before, orderRepo.count());
    }
}
