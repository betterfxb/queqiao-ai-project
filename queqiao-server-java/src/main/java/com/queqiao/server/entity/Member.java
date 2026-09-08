package com.queqiao.server.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.queqiao.server.security.Sensitive;
import com.queqiao.server.security.SensitiveType;
import com.queqiao.server.security.SensitiveScope;

/** 会员档案 */
@Data
@Entity
@Table(name = "t_member")
public class Member {

    @Id
    @Column(length = 64)
    private String id;

    private String name;

    private String gender;

    private Integer age;

    private String city;

    /** 省份（信息收集页省市级联的省级归属，用于红娘端按省筛选） */
    private String province;

    /** 区/县（信息收集页三级级联的末级归属） */
    private String district;

    private String education;

    private String job;

    private String level;

    private String status;

    private String agent;

    /** 微信 openid（会员端微信登录绑定，可空） */
    private String wechatOpenId;

    /** 会员专属推荐码（2-B 全民红娘裂变，C 端分享拉新） */
    private String referralCode;

    private Integer matchCount;

    private Integer successRate;

    private String lastActive;

    private String avatarBg;

    @Sensitive(value = SensitiveType.PHONE, scope = SensitiveScope.MEMBER)
    @Getter(onMethod_ = @Sensitive(value = SensitiveType.PHONE, scope = SensitiveScope.MEMBER))
    private String phone;

    private Integer height;

    @Sensitive(value = SensitiveType.INCOME, scope = SensitiveScope.MEMBER)
    private String income;

    @Sensitive(value = SensitiveType.MARRIAGE, scope = SensitiveScope.MEMBER)
    private String marriage;

    @Sensitive(value = SensitiveType.DETAIL, scope = SensitiveScope.MEMBER)
    private String hasChild;

    private String hometown;

    @Sensitive(value = SensitiveType.DETAIL, scope = SensitiveScope.MEMBER)
    private String house;

    @Sensitive(value = SensitiveType.DETAIL, scope = SensitiveScope.MEMBER)
    private String car;

    @Column(columnDefinition = "TEXT")
    private String requirement;

    private String tags;

    @Column(columnDefinition = "TEXT")
    private String bio;

    /** 本人照片（采集页上传，前端压缩后 base64 dataURL，选填；@Lob 存 CLOB/LONGTEXT） */
    @Lob
    private String photo;

    /**
     * 照片缩略图（120px 宽 JPEG dataURL）：仅列表接口实时生成并下发，不入库。
     * 原图 photo 体积可达数百 KB，一页 20 条即 MB 级；列表只给几 KB 的缩略图，
     * 点进详情才下发原图，兼顾「列表能看脸」与移动端流量/渲染性能。
     */
    @jakarta.persistence.Transient
    private String photoThumb;

    private Boolean verified;

    /** 认证标签（产品化，对外可信背书 + 匹配加权因子） */
    private Boolean verifyRealName;
    private Boolean verifyEducation;
    private Boolean verifyAsset;
    private LocalDateTime verifyRealNameAt;
    private LocalDateTime verifyEducationAt;
    private LocalDateTime verifyAssetAt;

    /** 实人认证徽章：实名公安 + 人脸活体（2-A 自动实人认证）通过后点亮 */
    private Boolean verifyRealPerson;
    private LocalDateTime verifyRealPersonAt;

    /** 最近一次跟进日期（用于流失预警） */
    private LocalDate lastFollowUp;

    /** 会员服务到期日（用于续费提醒） */
    private LocalDate expire;

    /** 客户反馈：好评 / 一般 / 差评 */
    private String feedback;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

}
