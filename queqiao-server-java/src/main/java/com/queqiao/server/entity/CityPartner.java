package com.queqiao.server.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 城市合伙人：同城运营模板的「人」这一侧 */
@Data
@Entity
@Table(name = "t_city_partner")
public class CityPartner {

    @Id
    @Column(length = 64)
    private String id;

    private String city;

    private String ownerAgentId;

    private String ownerAgentName;

    /** 城市合伙人 / 城市代理 / 社群运营 */
    private String tier;

    /** 筹备中 / 运营中 / 已暂停 */
    private String status;

    private String phone;

    private LocalDate startDate;

    /** 分成比例（%） */
    private Double shareRate;

    @Column(columnDefinition = "TEXT")
    private String remark;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
