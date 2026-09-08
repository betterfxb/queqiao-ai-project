package com.queqiao.server.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;

import java.time.LocalDateTime;

/** 分层社群档位：免费群 → 轻会员(39.9) → VIP(299) */
@Data
@Entity
@Table(name = "t_community_tier")
public class CommunityTier {

    @Id
    @Column(length = 64)
    private String id;

    private String city;

    /** 免费群 / 轻会员 / VIP */
    private String level;

    private BigDecimal price;

    @Column(columnDefinition = "TEXT")
    private String benefits;

    private Integer capacity;

    private Integer memberCount;

    /** 关联服务产品 SKU（弱关联，可空） */
    private String productId;

    /** 在售 / 下架 */
    private String status;

    private Integer sortOrder;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
