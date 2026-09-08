package com.queqiao.server.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/** 情感 / 性格测评模板（题目、选项、维度权重） */
@Data
@Entity
@Table(name = "t_quiz_template")
public class QuizTemplate {

    @Id
    @Column(length = 64)
    private String id;

    /** 模板标题，如「情感性格测评」 */
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    /** 维度定义 JSON：[{key,name,positive,negative}]，positive/negative 为两极契合标签 */
    @Column(columnDefinition = "TEXT")
    private String dimensions;

    /** 题目 JSON：[{id,text,options:[{key,label,dimension,score}]}]，score 为对该维度的正负贡献 */
    @Column(columnDefinition = "TEXT")
    private String questions;

    private LocalDateTime createdAt;
}
