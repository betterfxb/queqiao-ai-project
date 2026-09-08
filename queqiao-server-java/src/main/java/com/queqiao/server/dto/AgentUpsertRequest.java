package com.queqiao.server.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

/**
 * 红娘新增/编辑入参白名单（P0-4）。
 *
 * <p>{@code memberCount / monthMatch / successRate / monthRevenue / rating} 属于业绩统计口径，
 * 由牵线、订单、评价流程回写，禁止客户端直接提交，避免刷业绩与分佣造假。</p>
 */
@Data
public class AgentUpsertRequest {

    @Size(max = 30, message = "姓名不能超过 30 个字")
    private String name;

    @Size(max = 20)
    private String level;

    @Pattern(regexp = "在职|休假|离职", message = "状态仅支持 在职/休假/离职")
    private String status;

    @Pattern(regexp = "|1[3-9]\\d{9}", message = "手机号格式不正确")
    private String phone;

    @Size(max = 60)
    private String region;

    @Min(value = 1, message = "承载量需在 1~500 之间")
    @Max(value = 500, message = "承载量需在 1~500 之间")
    private Integer capacity;

    private LocalDate joinDate;

    @Size(max = 200)
    private String avatarBg;
}
