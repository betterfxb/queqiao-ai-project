package com.queqiao.server.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/** 会员测评作答记录（产出契合标签写入会员档案） */
@Data
@Entity
@Table(name = "t_member_quiz")
public class MemberQuiz {

    @Id
    @Column(length = 64)
    private String id;

    private String memberId;

    private String memberName;

    private String templateId;

    private String templateTitle;

    /** 作答 JSON：{questionId: optionKey} */
    @Column(columnDefinition = "TEXT")
    private String answers;

    /** 各维度得分 JSON：{dimensionKey: score} */
    @Column(columnDefinition = "TEXT")
    private String scores;

    /** 契合标签，逗号分隔（如 偏理性,重家庭） */
    private String tags;

    @Column(columnDefinition = "TEXT")
    private String summary;

    private LocalDateTime createdAt;
}
