package com.queqiao.server.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;

import java.time.LocalDateTime;

/** 服务产品（SKU）：会员卡 / 一对一套餐 / 活动票 / 增值包 / 咨询课时 */
@Data
@Entity
@Table(name = "t_service_product")
public class ServiceProduct {

    @Id
    @Column(length = 64)
    private String id;

    /** 产品名称 */
    private String name;

    /** 分类：会员卡 / 一对一套餐 / 活动票 / 增值包 / 咨询课时 */
    private String category;

    /** 定价（元） */
    private BigDecimal price;

    /** 有效期天数（0 表示不限期） */
    private Integer durationDays;

    /** 权益说明 */
    @Column(columnDefinition = "TEXT")
    private String benefits;

    /** 状态：在售 / 下架 */
    private String status;

    private LocalDateTime createdAt;
}
