package com.queqiao.server.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/** 第三方核验任务（学历/实名/资产/房车/收入等核验留痕） */
@Data
@Entity
@Table(name = "t_verify_task")
public class VerifyTask {

    @Id
    @Column(length = 64)
    private String id;

    private String memberId;

    private String memberName;

    /** 核验类型：实名 / 学历 / 资产 / 房车 / 收入 */
    private String type;

    /** 核验渠道：学信网 / 实名公安 / 材料审核 / 人工 */
    private String channel;

    @Column(columnDefinition = "TEXT")
    private String rawProof;

    /** 状态：待核验 / 核验中 / 已通过 / 未通过 */
    private String status;

    @Column(columnDefinition = "TEXT")
    private String result;

    private String operator;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
