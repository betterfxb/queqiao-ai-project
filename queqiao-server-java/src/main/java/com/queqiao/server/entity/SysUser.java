package com.queqiao.server.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.queqiao.server.security.Sensitive;
import com.queqiao.server.security.SensitiveType;
import com.queqiao.server.security.SensitiveScope;

/** 系统用户（红娘/主管/管理员登录账号） */
@Data
@Entity
@Table(name = "t_sys_user")
public class SysUser {

    @Id
    @Column(length = 64)
    private String id;

    @Column(name = "username")
    private String username;

    private String password;

    private String name;

    private String role;

    private String avatar;

    private String avatarBg;

    @Sensitive(value = SensitiveType.PHONE, scope = SensitiveScope.STAFF)
    @Getter(onMethod_ = @Sensitive(value = SensitiveType.PHONE, scope = SensitiveScope.STAFF))
    private String phone;

    @Sensitive(value = SensitiveType.EMAIL, scope = SensitiveScope.STAFF)
    @Getter(onMethod_ = @Sensitive(value = SensitiveType.EMAIL, scope = SensitiveScope.STAFF))
    private String email;

    private String status;

    private String agentId;

    private LocalDateTime lastLoginAt;

    private LocalDateTime createdAt;

    // ========================================================
    // 体验账号额度（供意向客户体验全流程，次数 + 天数双限）
    // 说明：全部使用包装类型，老数据列为 NULL 时不会拆箱异常；
    //      NULL 语义 = 非体验账号 / 未设置，由 TrialLimitService 统一兜底。
    // ========================================================

    /** 是否体验账号：true 才受额度约束 */
    private Boolean isTrial;

    /** 体验总次数额度（可写操作次数上限） */
    private Integer trialQuota;

    /** 已消耗次数 */
    private Integer trialUsed;

    /** 体验截止时间（天数限制落成的绝对时间点） */
    private LocalDateTime trialExpireAt;

    /** 是否已锁定：次数用尽或已过期后置 true，需管理员重新开通 */
    private Boolean trialLocked;

}
