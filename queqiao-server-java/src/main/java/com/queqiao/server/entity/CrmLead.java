package com.queqiao.server.entity;

import com.queqiao.server.security.Sensitive;
import com.queqiao.server.security.SensitiveScope;
import com.queqiao.server.security.SensitiveType;
import jakarta.persistence.*;
import lombok.Data;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** CRM 线索/商机 */
@Data
@Entity
@Table(name = "t_crm_lead")
public class CrmLead {

    @Id
    @Column(length = 64)
    private String id;

    private String name;

    private Integer age;

    private String gender;

    private String level;

    /** 线索手机：红娘/管理员可见原文，其余角色脱敏 */
    @Sensitive(value = SensitiveType.PHONE, scope = SensitiveScope.MEMBER)
    @Getter(onMethod_ = @Sensitive(value = SensitiveType.PHONE, scope = SensitiveScope.MEMBER))
    private String phone;

    private String stage;

    private Integer stayDays;

    private String fresh;

    private String freshText;

    private String nextAction;

    private Boolean star;

    private String agent;

    private String source;

    /** 归因渠道（对齐 MarketChannel.code/name），#111 5.3.5 */
    private String channel;

    /** 投放活动 / 批次 id */
    private String campaignId;

    /** 单条线索获客成本（元） */
    private Double cost;

    /** 推荐人会员 id（老带新），#111 5.3.1 */
    private String referrerMemberId;

    private String referrerName;

    /** 转化后硬关联的会员 id（生产环境应通过转化写入，而非仅用 phone 关联），#111 5.3.5 边界 */
    private String memberId;

    private String memberName;

    @Column(columnDefinition = "TEXT")
    private String remark;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

}
