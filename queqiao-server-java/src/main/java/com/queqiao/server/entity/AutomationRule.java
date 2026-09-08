package com.queqiao.server.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/** 营销自动化规则（持久化，替代原内存硬编码规则，重启不丢、可启停可编辑） */
@Data
@Entity
@Table(name = "t_automation_rule")
public class AutomationRule {

    @Id
    @Column(length = 64)
    private String id;

    private String name;

    @Column(columnDefinition = "TEXT")
    private String desc;

    /** 作用域：renewal / churn / match / activity */
    private String scope;

    private boolean enabled;

    /** cron 表达式（预留定时执行） */
    private String cron;

    /** 规则参数 JSON（如阈值等，预留扩展） */
    @Column(columnDefinition = "TEXT")
    private String params;

    private String lastRunAt;

    private Integer lastCreated;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
