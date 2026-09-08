package com.queqiao.server.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 黑名单（拉黑后限制牵线等功能） */
@Data
@Entity
@Table(name = "t_blacklist")
public class Blacklist {

    @Id
    @Column(length = 64)
    private String id;

    private String memberId;

    private String memberName;

    @Column(columnDefinition = "TEXT")
    private String reason;

    /** 解封日期；null 表示永久拉黑 */
    private LocalDate until;

    /** 状态：生效 / 已解封 */
    private String status;

    private String operator;

    private LocalDateTime createdAt;
}
