package com.queqiao.server.entity;

import com.queqiao.server.security.Sensitive;
import com.queqiao.server.security.SensitiveScope;
import com.queqiao.server.security.SensitiveType;
import jakarta.persistence.*;
import lombok.Data;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 红娘 */
@Data
@Entity
@Table(name = "t_agent")
public class Agent {

    @Id
    @Column(length = 64)
    private String id;

    private String name;

    private String level;

    private String status;

    private Integer memberCount;

    private Integer monthMatch;

    private Integer successRate;

    private Double monthRevenue;

    private Double rating;

    private String avatarBg;

    /** 员工手机：仅管理员可见原文 */
    @Sensitive(value = SensitiveType.PHONE, scope = SensitiveScope.STAFF)
    @Getter(onMethod_ = @Sensitive(value = SensitiveType.PHONE, scope = SensitiveScope.STAFF))
    private String phone;

    private String region;

    @Column(name = "join_date")
    private LocalDate joinDate;

    private Integer capacity;

    private LocalDateTime createdAt;

}
