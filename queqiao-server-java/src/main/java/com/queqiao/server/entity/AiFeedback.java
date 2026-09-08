package com.queqiao.server.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 建议反馈（反馈学习的持久化事实表）。
 *
 * <p>AI 服务本身保持无状态：权重只存在于内存，重启后由本表全量回灌，
 * 因此这里必须记录「足以完整重建权重」的最小信息集。</p>
 */
@Data
@Entity
@Table(name = "t_ai_feedback")
public class AiFeedback {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 场景：match / reply / assign / audit / coach */
    @Column(length = 32)
    private String scene;

    /** 被评价对象（如 pairId、memberId） */
    @Column(length = 64)
    private String itemId;

    /** accept 采纳 / reject 否决 */
    @Column(length = 16)
    private String action;

    /** 命中的匹配维度，逗号分隔（age,education,city,hobby,height） */
    @Column(length = 255)
    private String dimensions;

    @Column(columnDefinition = "TEXT")
    private String reason;

    @Column(length = 64)
    private String operator;

    @Column(length = 64)
    private String operatorId;

    private LocalDateTime createdAt;
}
