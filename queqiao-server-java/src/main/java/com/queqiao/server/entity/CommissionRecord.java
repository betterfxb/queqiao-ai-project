package com.queqiao.server.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;

import java.time.LocalDateTime;

/**
 * 红娘分佣记录：撮合成功 / 续费 两类事件按机构配置比例自动计提，
 * 状态机：待结算 → 已结算(结算单) → 已提现(提现记录)。
 * 幂等键：(orderId + type) 唯一，防止同一笔订单重复计提。
 */
@Data
@Entity
@Table(name = "t_commission_record")
public class CommissionRecord {

    @Id
    @Column(length = 64)
    private String id;

    /** 红娘 ID（由 agentName 反查，解析失败也保留 agentName 便于展示） */
    private String agentId;

    /** 红娘姓名（与 Pair.agent / FinanceOrder.agent 一致，是查询「我的佣金」的主键） */
    private String agentName;

    /** 资金来源订单 */
    private String orderId;

    /** 关联牵线（仅成功撮合类有值） */
    private String pairId;

    /** 佣金类型：成功 / 续费 */
    private String type;

    /** 佣金金额（元） */
    private BigDecimal amount;

    /** 原始订单金额（计提基数） */
    private BigDecimal orderAmount;

    /** 计提时采用的比例（百分比，如 10 表示 10%） */
    private Double rate;

    /** 待结算 / 已结算 / 已提现 */
    private String status;

    /** 结算单批次号（结算时写入） */
    private String batchNo;

    private LocalDateTime settledAt;

    private LocalDateTime withdrawnAt;

    private LocalDateTime createdAt;
}
