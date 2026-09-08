package com.queqiao.server.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 牵线配对 */
@Data
@Entity
@Table(name = "t_pair")
public class Pair {

    @Id
    @Column(length = 64)
    private String id;

    private String maleId;

    private String maleName;

    private String femaleId;

    private String femaleName;

    private Integer score;

    private String status;

    private String stage;

    private String agent;

    private String maleResponse;

    private String femaleResponse;

    private String maleResponseKind;

    private String femaleResponseKind;

    @Column(columnDefinition = "TEXT")
    private String note;

    @Column(columnDefinition = "TEXT")
    private String reason;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

}
