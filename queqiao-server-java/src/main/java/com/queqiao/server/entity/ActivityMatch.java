package com.queqiao.server.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/** 活动现场互选 CP（1-E）：a 选 b，双向互选成功即生成牵线候选 */
@Data
@Entity
@Table(name = "t_activity_match")
public class ActivityMatch {

    @Id
    @Column(length = 64)
    private String id;

    @JsonProperty("activityId")
    private String activityId;

    @JsonProperty("aId")
    private String aId;

    @JsonProperty("aName")
    private String aName;

    @JsonProperty("bId")
    private String bId;

    @JsonProperty("bName")
    private String bName;

    /** 双向互选成功 */
    @JsonProperty("mutual")
    private Boolean mutual;

    @JsonProperty("createdAt")
    private LocalDateTime createdAt;
}
