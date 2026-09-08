package com.queqiao.server.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 营销获客渠道（区别于支付侧的 {@link ChannelLedger}）。
 * 用于线索归因与 CAC / ROI 计算。
 */
@Data
@Entity
@Table(name = "t_market_channel")
public class MarketChannel {

    @Id
    @Column(length = 64)
    private String id;

    private String name;

    /** 付费投放 / 转介绍 / 活动获客 / 单位合作 / 自然流量 */
    private String category;

    /** 渠道短码，线索 channel 字段与之对齐 */
    private String code;

    /** 累计投入（元） */
    private Double cost;

    private String owner;

    /** 启用 / 停用 */
    private String status;

    @Column(columnDefinition = "TEXT")
    private String remark;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
