package com.queqiao.server.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/** 会员积分累计（按会员维度去范式化一行，便于快速读取与排行） */
@Data
@Entity
@Table(name = "t_member_points")
public class MemberPoints {

    @Id
    @Column(length = 64)
    private String id;

    @Column(length = 64, unique = true)
    private String memberId;

    @Column(length = 64)
    private String memberName;

    private int total;

    @Column(length = 32)
    private String level;

    private LocalDateTime updatedAt;
}
