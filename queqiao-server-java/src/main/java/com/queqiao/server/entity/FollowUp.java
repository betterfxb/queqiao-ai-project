package com.queqiao.server.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 会员跟进记录（红娘服务过程时间轴） */
@Data
@Entity
@Table(name = "t_follow_up")
public class FollowUp {

    @Id
    @Column(length = 64)
    private String id;

    private String memberId;

    private String agent;

    /** 跟进方式：电话 / 微信 / 约见 / 回访 / 系统 */
    private String type;

    /** 沟通要点（TEXT） */
    @Column(columnDefinition = "TEXT")
    private String content;

    /** 意向结论：成交意向 / 待定 / 无意向 */
    private String outcome;

    /** 下次跟进日期 */
    private LocalDate nextFollowUp;

    /** 幂等键（系统自动建跟进时写入，防止重复造数据） */
    @Column(length = 128)
    private String dedupKey;

    private LocalDateTime createdAt;

}
