package com.queqiao.server.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 系统设置项（KV） */
@Data
@Entity
@Table(name = "t_setting")
public class SettingItem {

    @Id
    @Column(length = 64)
    private String id;

    private String category;

    @Column(columnDefinition = "TEXT", name = "setting_value")
    private String value;

    private String label;

    @Column(name = "setting_type")
    private String type;

    private LocalDateTime updatedAt;

}
