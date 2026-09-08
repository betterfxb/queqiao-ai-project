package com.queqiao.server.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/** 社群档位实际开通记录：社群档位支付打通后由 FinanceService.pay 回调生成（与订单强关联）。 */
@Data
@Entity
@Table(name = "t_community_membership")
public class CommunityMembership {

    @Id
    @Column(length = 64)
    private String id;

    /** 关联社群档位 t_community_tier.id */
    private String tierId;

    /** 冗余档位名称，便于展示（免费群 / 轻会员 / VIP） */
    private String tierLevel;

    private String city;

    private String memberId;

    private String memberName;

    /** 关联支付订单 t_finance_order.id（支付回调时写入，幂等依据） */
    private String orderId;

    /** 有效 / 已退订 */
    private String status;

    private LocalDateTime activeFrom;

    private LocalDateTime activeTo;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
