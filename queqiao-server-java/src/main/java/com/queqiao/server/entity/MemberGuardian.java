package com.queqiao.server.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 家长参与关系（2-C）。
 * 子女会员(memberId) 绑定家长(guardianPhone)，经本人授权(authStatus=已授权)后，
 * 家长可以「家长视角」查看子女会话、并可代沟通(role=communicate)。
 * 权限完全可控：待授权 → 已授权 → 已撤销，撤销后立即可见性消失。
 */
@Data
@Entity
@Table(name = "t_member_guardian")
public class MemberGuardian {

    @Id
    @Column(length = 64)
    private String id;

    /** 子女会员 id */
    private String memberId;

    /** 子女姓名（冗余，便于展示） */
    private String memberName;

    /** 家长手机号（身份标识，用于家长登录态校验） */
    private String guardianPhone;

    /** 家长称呼 */
    private String guardianName;

    /** 家长若为会员，记录其 memberId（用于登录态身份校验） */
    private String guardianMemberId;

    /** 关系：父 / 母 / 其他 */
    private String relation;

    /** 授权状态：待授权 / 已授权 / 已撤销 */
    private String authStatus;

    /** 家长权限：view（仅查看）/ communicate（代沟通） */
    private String role;

    @Column(columnDefinition = "TEXT")
    private String remark;

    private LocalDateTime authorizedAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
