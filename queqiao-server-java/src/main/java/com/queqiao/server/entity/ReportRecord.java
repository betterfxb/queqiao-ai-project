package com.queqiao.server.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 报表导出记录 */
@Data
@Entity
@Table(name = "t_report_record")
public class ReportRecord {

    @Id
    @Column(length = 64)
    private String id;

    private String name;

    private String category;

    @Column(name = "file_format")
    private String format;

    @Column(name = "range_text")
    private String range;

    private String exportedAt;

    private String operator;

    private String status;

    @Column(name = "file_size")
    private String size;

    private String filePath;

    private LocalDateTime createdAt;

}
