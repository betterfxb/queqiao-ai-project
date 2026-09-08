package com.queqiao.server.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 会员新增/编辑入参白名单（P0-4 防 mass assignment）。
 *
 * <p>只暴露业务方允许填写的字段；{@code verified / verifyRealName / verifyEducation /
 * verifyAsset / matchCount / successRate / createdAt / id} 等由服务端流程（审核、核验、
 * 牵线统计）维护，客户端无法通过请求体篡改。</p>
 */
@Data
public class MemberUpsertRequest {

    @Size(max = 30, message = "姓名不能超过 30 个字")
    private String name;

    @Pattern(regexp = "男|女", message = "性别仅支持「男」或「女」")
    private String gender;

    @Min(value = 18, message = "年龄需在 18~100 之间")
    @Max(value = 100, message = "年龄需在 18~100 之间")
    private Integer age;

    @Size(max = 60)
    private String city;

    /** 省份（采集页省市级联的省级归属，用于红娘端按省筛选） */
    @Size(max = 40)
    private String province;

    /** 区/县（采集页三级级联的末级归属） */
    @Size(max = 60)
    private String district;

    @Size(max = 30)
    private String education;

    @Size(max = 60)
    private String job;

    @Size(max = 20)
    private String level;

    @Size(max = 20)
    private String status;

    /** 仅主管/管理员生效；红娘提交将被忽略 */
    @Size(max = 60)
    private String agent;

    @Pattern(regexp = "|1[3-9]\\d{9}", message = "手机号格式不正确")
    private String phone;

    @Min(value = 120, message = "身高需在 120~230cm 之间")
    @Max(value = 230, message = "身高需在 120~230cm 之间")
    private Integer height;

    @Size(max = 40)
    private String income;

    @Size(max = 20)
    private String marriage;

    @Size(max = 20)
    private String hasChild;

    @Size(max = 60)
    private String hometown;

    @Size(max = 40)
    private String house;

    @Size(max = 40)
    private String car;

    @Size(max = 2000)
    private String requirement;

    @Size(max = 500)
    private String tags;

    @Size(max = 2000)
    private String bio;

    /**
     * 本人照片：前端压缩后的 base64 dataURL（选填）。
     * 上限 700_000 字符（≈ 512KB 二进制），与 public/register 采集页口径一致，
     * 防止超大图片拖垮接口与数据库。
     */
    @Size(max = 700000, message = "照片过大，请压缩后再上传")
    private String photo;

    @Size(max = 200)
    private String avatarBg;
}
