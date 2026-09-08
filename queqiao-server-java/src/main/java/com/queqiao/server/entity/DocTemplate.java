package com.queqiao.server.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/** 合规文档模板：用户协议 / 隐私政策 / 退费规则 / 婚介服务合同 */
@Data
@Entity
@Table(name = "t_doc_template")
public class DocTemplate {

    @Id
    @Column(length = 64)
    private String id;

    /** 类型：用户协议 / 隐私政策 / 退费规则 / 婚介服务合同 */
    private String type;

    private String title;

    @Column(columnDefinition = "TEXT")
    private String content;

    private String version;

    private String updatedBy;

    private LocalDateTime updatedAt;
}
