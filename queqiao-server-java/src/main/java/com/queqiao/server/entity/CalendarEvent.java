package com.queqiao.server.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 日程/约会安排 */
@Data
@Entity
@Table(name = "t_calendar_event")
public class CalendarEvent {

    @Id
    @Column(length = 64)
    private String id;

    private String title;

    @Column(name = "event_type")
    private String type;

    @Column(name = "event_date")
    private LocalDate date;

    private String startTime;

    private String endTime;

    private String place;

    private String memberIds;

    private String memberNames;

    private String pairId;

    private String agent;

    private String status;

    @Column(columnDefinition = "TEXT")
    private String remark;

    /** 幂等去重键（规则Id:会员Id:业务日期），防止重复执行制造重复任务 */
    @Column(name = "dedup_key")
    private String dedupKey;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

}
