package com.queqiao.server.entity;

import com.queqiao.server.security.Sensitive;
import com.queqiao.server.security.SensitiveScope;
import com.queqiao.server.security.SensitiveType;
import jakarta.persistence.*;
import lombok.Data;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 活动报名 */
@Data
@Entity
@Table(name = "t_activity_signup",
        uniqueConstraints = @UniqueConstraint(name = "uk_signup_activity_member",
                columnNames = {"activity_id", "member_id"}))
public class ActivitySignup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String activityId;

    private String memberId;

    private String memberName;

    private String gender;

    private String status;

    private Boolean paid;

    private Double amount;

    /** 报名人手机：红娘/管理员可见原文，其余角色脱敏 */
    @Sensitive(value = SensitiveType.PHONE, scope = SensitiveScope.MEMBER)
    @Getter(onMethod_ = @Sensitive(value = SensitiveType.PHONE, scope = SensitiveScope.MEMBER))
    private String phone;

    private LocalDateTime signedAt;

}
