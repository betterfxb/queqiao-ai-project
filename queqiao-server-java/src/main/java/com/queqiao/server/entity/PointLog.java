package com.queqiao.server.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/** 积分变动流水（不可变，用于审计与明细追溯） */
@Data
@Entity
@Table(name = "t_point_log")
public class PointLog {

    @Id
    @Column(length = 64)
    private String id;

    @Column(length = 64)
    private String memberId;

    @Column(length = 64)
    private String memberName;

    /** 积分类型（见 PointsService.Rule） */
    @Column(length = 32)
    private String type;

    /** 积分类型中文文案 */
    @Column(length = 64)
    private String typeLabel;

    /** 本次变动分值（正为发放） */
    private int delta;

    /** 关联业务单据号（如牵线 ID），用于幂等去重 */
    @Column(length = 64)
    private String refId;

    /** 操作人 */
    @Column(length = 64)
    private String operator;

    @Column(columnDefinition = "TEXT")
    private String remark;

    private LocalDateTime createdAt;
}
