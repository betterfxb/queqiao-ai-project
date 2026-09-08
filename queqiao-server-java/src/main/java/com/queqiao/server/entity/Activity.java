package com.queqiao.server.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 线下活动 */
@Data
@Entity
@Table(name = "t_activity")
public class Activity {

    @Id
    @Column(length = 64)
    private String id;

    private String title;

    @Column(name = "act_date")
    private LocalDate date;

    private String weekday;

    @Column(name = "act_time")
    private String time;

    private String place;

    /** 所属城市（#111 5.3.3 同城运营聚合；为空时回退用 place 包含匹配） */
    private String city;

    private String status;

    private String tags;

    private Integer signed;

    private Integer capacity;

    private Integer female;

    private Integer male;

    private BigDecimal price;

    private BigDecimal revenue;

    private Integer expectedPairs;

    private String agent;

    private Boolean hero;

    @Column(columnDefinition = "TEXT")
    private String description;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

}
