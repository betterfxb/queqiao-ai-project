package com.queqiao.server.controller;

import com.queqiao.server.common.Money;
import com.queqiao.server.entity.*;
import com.queqiao.server.repo.*;
import com.queqiao.server.security.RequestContext;
import com.queqiao.server.service.ChannelService;
import com.queqiao.server.service.FunnelService;
import com.queqiao.server.service.PartnerService;
import com.queqiao.server.service.ReferralService;
import com.queqiao.server.service.RenewalService;
import com.queqiao.server.service.SettingService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 运营成效指标（只读聚合）：让「智能运营」从"看板"升级为"看得见商业结果的闭环"。
 * 全部基于既有 Repository，不新增业务表。需要主管及以上权限。
 */
@RestController
@RequestMapping("/api/ops")
public class OpsMetricController {

    private final MemberRepository members;
    private final FinanceOrderRepository orders;
    private final CrmLeadRepository leads;
    private final AgentRepository agents;
    private final SettingService settings;
    private final FunnelService funnelService;
    private final ReferralService referralService;
    private final ChannelService channelService;
    private final PartnerService partnerService;
    /** 1-G：续费提醒统一口径（真实订单同源） */
    private final RenewalService renewalService;

    public OpsMetricController(MemberRepository members, FinanceOrderRepository orders,
                               CrmLeadRepository leads, AgentRepository agents, SettingService settings,
                               FunnelService funnelService, ReferralService referralService,
                               ChannelService channelService, PartnerService partnerService,
                               RenewalService renewalService) {
        this.renewalService = renewalService;
        this.referralService = referralService;
        this.channelService = channelService;
        this.partnerService = partnerService;
        this.members = members;
        this.orders = orders;
        this.leads = leads;
        this.agents = agents;
        this.settings = settings;
        this.funnelService = funnelService;
    }

    private static final Set<String> TERMINAL = Set.of("已脱单", "已流失", "已终止", "已暂停");

    /** 运营成效总览 */
    @GetMapping("/metrics")
    public Map<String, Object> metrics() {
        RequestContext.requireManager();
        LocalDate today = LocalDate.now();
        int churnDays = settings.getInt("biz.churnDays", 14);
        int renewalDays = settings.getInt("biz.renewalDays", 15);

        // 流失预警数
        long churn = members.findAll().stream()
                .filter(m -> !TERMINAL.contains(nz(m.getStatus())))
                .filter(m -> {
                    LocalDate lf = m.getLastFollowUp() != null ? m.getLastFollowUp()
                            : (m.getUpdatedAt() != null ? m.getUpdatedAt().toLocalDate() : today);
                    long days = ChronoUnit.DAYS.between(lf, today);
                    return days >= churnDays || "差评".equals(m.getFeedback());
                }).count();

        // 续费提醒数（1-G 一致性：与 /api/insights/renewals 同源，均取自真实订单到期日）
        long renewalDue = renewalService.listOpen().size();

        // 近 30 天续费回收金额
        BigDecimal renewalRecovered = Money.sum(orders.findAll().stream()
                .filter(o -> o.getType() != null && o.getType().contains("续费"))
                .filter(o -> "已完成".equals(o.getStatus()))
                .filter(o -> o.getCreatedAt() != null && o.getCreatedAt().isAfter(LocalDateTime.now().minusDays(30)))
                .toList(), FinanceOrder::getAmount);

        // 转介绍率（来源含 介绍/推荐/转介绍）
        List<CrmLead> allLeads = leads.findAll();
        long referral = allLeads.stream()
                .filter(l -> l.getSource() != null && (l.getSource().contains("介绍")
                        || l.getSource().contains("推荐") || l.getSource().contains("转介绍")))
                .count();
        double referralRate = allLeads.isEmpty() ? 0 : Math.round(referral * 1000.0 / allLeads.size()) / 10.0;

        // 红娘人效
        List<Map<String, Object>> agentEff = agents.findAll().stream().map(a -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", a.getName());
            m.put("monthMatch", a.getMonthMatch());
            m.put("monthRevenue", a.getMonthRevenue());
            m.put("successRate", a.getSuccessRate());
            return m;
        }).collect(Collectors.toList());

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("churnDays", churnDays);
        res.put("renewalDays", renewalDays);
        res.put("churnCount", churn);
        res.put("renewalDueCount", renewalDue);
        res.put("renewalRecovered30d", Money.toDouble(renewalRecovered));
        res.put("referralCount", referral);
        res.put("referralRate", referralRate);
        res.put("leadTotal", allLeads.size());
        res.put("agentEfficiency", agentEff);
        // #111 增长与差异化：转介绍激励 / 产业链导流 概览并入运营总览
        res.put("referralProgram", referralService.summary());
        res.put("partnerProgram", partnerService.summary());
        res.put("updatedAt", LocalDateTime.now().toString());
        return res;
    }

    /** 渠道归因 ROI 别名（等价于 /api/channels/roi），便于运营大盘统一从 /api/ops 拉取 */
    @GetMapping("/channel-roi")
    public Map<String, Object> channelRoi(
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate to) {
        RequestContext.requireManager();
        return channelService.roi(from, to);
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    /** 转化获客漏斗：注册→认证→付费→牵线→约见→成功 六层去重人数与转化率 */
    @GetMapping("/funnel")
    public Map<String, Object> funnel(@RequestParam(required = false) Integer days) {
        RequestContext.requireManager();
        return funnelService.funnel(days);
    }
}
