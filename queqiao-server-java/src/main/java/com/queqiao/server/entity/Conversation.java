package com.queqiao.server.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 会话 */
@Data
@Entity
@Table(name = "t_conversation")
public class Conversation {

    @Id
    @Column(length = 64)
    private String id;

    private String name;

    private String memberId;

    private String timeAgo;

    private String preview;

    private Integer unread;

    private String kind;

    private Boolean online;

    private String agent;

    private String avatarBg;

    /** 幂等去重键（规则Id:会员Id:业务日期），防止重复执行制造重复会话 */
    @Column(name = "dedup_key")
    private String dedupKey;

    private LocalDateTime lastAt;

    private LocalDateTime createdAt;

}
