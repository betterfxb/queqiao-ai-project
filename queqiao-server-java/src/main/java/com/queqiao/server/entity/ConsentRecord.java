package com.queqiao.server.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/** 合规同意留痕（注册 / 付费流程强制勾选） */
@Data
@Entity
@Table(name = "t_consent_record")
public class ConsentRecord {

    @Id
    @Column(length = 64)
    private String id;

    private String memberId;

    private String memberName;

    private String docType;

    private String version;

    /** 场景：注册 / 付费 */
    private String channel;

    private LocalDateTime acceptedAt;
}
