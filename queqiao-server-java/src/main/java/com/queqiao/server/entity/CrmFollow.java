package com.queqiao.server.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** CRM 跟进记录 */
@Data
@Entity
@Table(name = "t_crm_follow")
public class CrmFollow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String leadId;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(name = "follow_type")
    private String type;

    private String operator;

    private LocalDateTime createdAt;

}
