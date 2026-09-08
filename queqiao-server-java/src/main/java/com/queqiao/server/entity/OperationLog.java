package com.queqiao.server.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 操作日志 */
@Data
@Entity
@Table(name = "t_operation_log")
public class OperationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String userId;

    private String userName;

    private String action;

    private String target;

    @Column(columnDefinition = "TEXT")
    private String detail;

    private String ip;

    private LocalDateTime createdAt;

}
