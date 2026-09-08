package com.queqiao.server.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 牵线反馈（1-D 闭环）：每次约见/沟通后记录的轻问卷。
 * 与 Pair 的 maleResponse/femaleResponse 并存——后者保留历史兼容，
 * 本表为结构化反馈（满意度 + 卡点标签），用于沉淀会员偏好并重算匹配权重。
 */
@Data
@Entity
@Table(name = "t_pair_feedback")
public class PairFeedback {

    @Id
    @Column(length = 64)
    private String id;

    /** 关联牵线 */
    private String pairId;

    /** male / female / agent（红娘代记） */
    private String side;

    /** 满意度 1-5 */
    private Integer satisfaction;

    /** 卡点标签，逗号分隔（如：异地顾虑,节奏太快） */
    @Column(columnDefinition = "TEXT")
    private String blockers;

    /** 自由备注 */
    @Column(columnDefinition = "TEXT")
    private String note;

    /** 沉淀到会员的偏好标签（高满意度/明确偏好时写入 Member.tags），逗号分隔 */
    @Column(columnDefinition = "TEXT")
    private String preferenceTags;

    private LocalDateTime createdAt;

    public List<String> blockerList() {
        if (blockers == null || blockers.isBlank()) return new ArrayList<>();
        return Arrays.stream(blockers.split("[,，、]"))
                .map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toList());
    }

    public void setBlockerList(List<String> list) {
        this.blockers = list == null ? null : String.join(",", list);
    }

    public List<String> preferenceList() {
        if (preferenceTags == null || preferenceTags.isBlank()) return new ArrayList<>();
        return Arrays.stream(preferenceTags.split("[,，、]"))
                .map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toList());
    }
}
