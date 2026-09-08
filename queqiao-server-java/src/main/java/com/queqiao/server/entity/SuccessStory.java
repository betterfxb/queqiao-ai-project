package com.queqiao.server.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 成功案例（脱单故事）。
 * 安全约束：本表**只存别名**（如「王先生」「李女士」）与年龄段，
 * 绝不落库真实姓名 / 手机号，因此对外公开接口天然无 PII 泄露风险。
 */
@Data
@Entity
@Table(name = "t_success_story")
public class SuccessStory {

    @Id
    @Column(length = 64)
    private String id;

    /** 来源牵线 id（一键生成草稿时写入） */
    private String pairId;

    private String title;

    /** 男方别名，如「王先生」 */
    private String maleAlias;

    /** 女方别名，如「李女士」 */
    private String femaleAlias;

    /** 城市标签 */
    private String cityTag;

    /** 年龄段标签，如「30-34岁 · 28-32岁」 */
    private String ageTag;

    @Column(columnDefinition = "TEXT")
    private String content;

    /** 封面（渐变色或图片地址） */
    private String cover;

    /** 逗号分隔标签 */
    private String tags;

    /** 草稿 / 已发布 / 已下架 */
    private String status;

    /** 是否对外公开（/api/stories/public 仅返回 已发布 && true） */
    private Boolean publicVisible;

    private Integer views;

    private String author;

    private LocalDateTime publishedAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
