package com.queqiao.server.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/** 活动现场签到（1-E）：红娘扫码 / 代记为会员签到 */
@Data
@Entity
@Table(name = "t_activity_checkin")
public class ActivityCheckin {

    @Id
    @Column(length = 64)
    private String id;

    private String activityId;

    private String memberId;

    private String memberName;

    /** 核销人（当前登录红娘 / 系统） */
    private String scannedBy;

    private LocalDateTime at;
}
