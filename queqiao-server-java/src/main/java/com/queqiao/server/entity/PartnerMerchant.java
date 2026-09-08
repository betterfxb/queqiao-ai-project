package com.queqiao.server.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/** 产业链合作商户：婚庆 / 婚纱 / 婚宴 / 情感咨询 等 */
@Data
@Entity
@Table(name = "t_partner_merchant")
public class PartnerMerchant {

    @Id
    @Column(length = 64)
    private String id;

    private String name;

    /** 婚庆策划 / 婚纱摄影 / 婚宴酒店 / 情感咨询 / 珠宝首饰 / 蜜月旅行 */
    private String category;

    private String city;

    private String contact;

    private String phone;

    /** 导流分佣比例（%） */
    private Double commissionRate;

    /** 合作中 / 已暂停 */
    private String status;

    @Column(columnDefinition = "TEXT")
    private String remark;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
