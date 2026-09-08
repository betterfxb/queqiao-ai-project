package com.queqiao.server.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/** 反诈工单：高危风险自动建单，附被害人救助引导文案，供反诈专员核查跟进。 */
@Data
@Entity
@Table(name = "t_work_order")
public class WorkOrder {

    @Id
    @Column(length = 64)
    private String id;

    /** 工单标题，如「反诈核查：张三 涉钱关键词」 */
    private String title;

    private String memberId;

    private String memberName;

    /** 工单类型，固定为「反诈核查」 */
    private String type;

    /** 风险类型：涉钱关键词 / 高频私信 */
    private String riskType;

    /** 状态：待处理 / 处理中 / 已结案 */
    private String status;

    /** 优先级：高 / 中 / 低 */
    private String priority;

    /** 关联的风险事件 id */
    private String relatedRiskId;

    /** 被害人救助引导文案 */
    @Column(columnDefinition = "TEXT")
    private String guidance;

    /** 风险详情（命中关键词 / 私信频次等） */
    @Column(columnDefinition = "TEXT")
    private String detail;

    private String assignee;

    private String handler;

    private String handleNote;

    private LocalDateTime handledAt;

    private LocalDateTime createdAt;
}
