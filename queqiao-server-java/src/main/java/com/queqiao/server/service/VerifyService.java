package com.queqiao.server.service;

import com.queqiao.server.common.BizException;
import com.queqiao.server.common.Ids;
import com.queqiao.server.config.VerifyConfig;
import com.queqiao.server.entity.Member;
import com.queqiao.server.entity.VerifyTask;
import com.queqiao.server.repo.MemberRepository;
import com.queqiao.server.repo.VerifyTaskRepository;
import com.queqiao.server.service.LogService;
import com.queqiao.server.service.MemberPushService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 第三方核验：发起核验任务（经 VerifyGateway 适配层）→ 运营录入结果 → 核验通过回写会员认证标签。
 * 核验任务全程可追溯；结果回写 Member 的 verifyRealName / verifyEducation / verifyAsset。
 */
@Service
public class VerifyService {

    private final VerifyTaskRepository tasks;
    private final MemberRepository members;
    private final LogService logs;
    private final VerifyGateway gateway;
    private final MemberPushService push;
    private final VerifyConfig cfg;

    public VerifyService(VerifyTaskRepository tasks, MemberRepository members, LogService logs, VerifyGateway gateway,
                         MemberPushService push, VerifyConfig cfg) {
        this.tasks = tasks;
        this.members = members;
        this.logs = logs;
        this.gateway = gateway;
        this.push = push;
        this.cfg = cfg;
    }

    /** 发起核验任务（经实时核验网关）；网关直接判定已通过则即时回写认证标签 */
    public VerifyTask create(String memberId, String type, String channel, String rawProof) {
        if (memberId == null || memberId.isBlank()) throw new BizException("缺少会员");
        if (type == null || type.isBlank()) throw new BizException("缺少核验类型");
        Member m = members.findById(memberId).orElseThrow(() -> BizException.notFound("会员"));
        VerifyTask t = new VerifyTask();
        t.setId(Ids.biz("VT"));
        t.setMemberId(memberId);
        t.setMemberName(m.getName());
        t.setType(type);
        t.setChannel(channel);
        t.setRawProof(rawProof);
        VerifyGateway.VerifyResult r = gateway.submit(t);
        LocalDateTime now = LocalDateTime.now();
        t.setStatus(r.status());
        t.setResult(r.note());
        t.setOperator(gatewayOperator());
        t.setCreatedAt(now);
        t.setUpdatedAt(now);
        tasks.save(t);
        logs.record("发起核验", memberId, type + " · " + channel + " · 网关(" + r.status() + ")");
        if ("已通过".equals(r.status())) writeBack(t); // 实时核验通过：即时回写认证标签
        return t;
    }

    /**
     * 自动实人认证（2-A）：实名公安 + 人脸活体 SDK。
     * authToken 为活体 SDK 一次性令牌，liveImage 为活体照（sandbox 下可传占位 / base64 预览）。
     * 密钥配置（real 模式且密钥齐全，或 sandbox 模拟）走自动链路；未配置（real 缺密钥 / offline）降级为人工审核留痕（待核验），
     * 待运营在审核页录入「已通过」后，由 applyResult → writeBack 按结果点亮「已实人」徽章。
     */
    public VerifyTask realPerson(String memberId, String authToken, String liveImage) {
        if (memberId == null || memberId.isBlank()) throw new BizException("缺少会员");
        Member m = members.findById(memberId).orElseThrow(() -> BizException.notFound("会员"));
        if ((authToken == null || authToken.isBlank()) && (liveImage == null || liveImage.isBlank())) {
            throw new BizException("缺少活体凭证（authToken 或 liveImage 至少其一）");
        }
        VerifyTask t = new VerifyTask();
        t.setId(Ids.biz("VT"));
        t.setMemberId(memberId);
        t.setMemberName(m.getName());
        t.setType("实人");
        t.setChannel("人脸活体实名");
        t.setRawProof(buildRealPersonProof(authToken, liveImage));
        LocalDateTime now = LocalDateTime.now();
        String status;
        String note;
        if (cfg.isReal() && !cfg.isConfigured()) {
            // 真实第三方密钥未配置：降级人工审核留痕，不抛错，待运营录入结果后点亮徽章
            status = "待核验";
            note = "实人认证密钥未配置，降级为人工审核留痕，请运营录入结果后点亮徽章";
        } else {
            VerifyGateway.VerifyResult r = gateway.submit(t);
            status = r.status();
            note = r.note();
        }
        t.setStatus(status);
        t.setResult(note);
        t.setOperator(realPersonOperator());
        t.setCreatedAt(now);
        t.setUpdatedAt(now);
        tasks.save(t);
        logs.record("发起实人认证", memberId, "人脸活体实名 · 网关(" + status + ")");
        if ("已通过".equals(status)) writeBack(t);
        return t;
    }

    /** 活体凭证留痕：不落库大图，仅存元数据（令牌 + 活体照前 48 字符预览 + 长度） */
    private static String buildRealPersonProof(String authToken, String liveImage) {
        String tok = authToken == null ? "" : authToken.replace("\"", "");
        String img = liveImage == null ? "" : liveImage.replace("\"", "");
        String preview = img.length() <= 48 ? img : img.substring(0, 48) + "...(len=" + img.length() + ")";
        return "{\"authToken\":\"" + tok + "\",\"liveImage\":\"" + preview + "\"}";
    }

    private String realPersonOperator() {
        if (gateway instanceof RealVerifyGateway r) {
            return "实时核验网关(" + r.getMode() + ")";
        }
        return "核验网关";
    }

    /** 录入核验结果；核验通过则回写认证标签 */
    public VerifyTask recordResult(String id, String status, String result, String operator) {
        if (status == null || status.isBlank()) throw new BizException("缺少核验状态");
        VerifyTask t = tasks.findById(id).orElseThrow(() -> BizException.notFound("核验任务"));
        return applyResult(t, status, result, operator);
    }

    /** 落定核验结果（create 实时判定与人工录入共用）；核验通过则回写认证标签 */
    private VerifyTask applyResult(VerifyTask t, String status, String result, String operator) {
        t.setStatus(status);
        t.setResult(result);
        t.setOperator(operator);
        t.setUpdatedAt(LocalDateTime.now());
        tasks.save(t);
        if ("已通过".equals(status)) writeBack(t);
        return t;
    }

    private String gatewayOperator() {
        String mode = (gateway instanceof RealVerifyGateway r) ? r.getMode() : "核验网关";
        return "实时核验网关(" + mode + ")";
    }

    /** 核验通过回写会员认证标签（产品化认证标签的权威来源之一） */
    private void writeBack(VerifyTask t) {
        Member m = members.findById(t.getMemberId()).orElse(null);
        if (m == null) return;
        LocalDateTime now = LocalDateTime.now();
        switch (nz(t.getType())) {
            case "实名" -> {
                m.setVerifyRealName(true);
                m.setVerifyRealNameAt(now);
                if (!Boolean.TRUE.equals(m.getVerified())) m.setVerified(true);
                m.setStatus("已认证");
            }
            case "学历" -> {
                m.setVerifyEducation(true);
                m.setVerifyEducationAt(now);
            }
            case "资产", "房车", "收入" -> {
                m.setVerifyAsset(true);
                m.setVerifyAssetAt(now);
            }
            case "实人" -> {
                m.setVerifyRealPerson(true);
                m.setVerifyRealPersonAt(now);
                // 实人认证隐含实名通过
                m.setVerifyRealName(true);
                if (m.getVerifyRealNameAt() == null) m.setVerifyRealNameAt(now);
                if (!Boolean.TRUE.equals(m.getVerified())) m.setVerified(true);
                m.setStatus("已认证");
            }
            default -> { /* 其他类型仅留痕，不写标签 */ }
        }
        members.save(m);
        logs.record("核验通过回写认证标签", t.getMemberId(), t.getType());
        push.push(m.getId(), Map.of("type", "verify-result", "verifyType", t.getType(), "passed", true));
    }

    public VerifyTask detail(String id) {
        return tasks.findById(id).orElseThrow(() -> BizException.notFound("核验任务"));
    }

    public List<VerifyTask> list(String memberId, String type, String status) {
        List<VerifyTask> all = tasks.findAll();
        return all.stream()
                .filter(t -> memberId == null || memberId.isBlank() || memberId.equals(t.getMemberId()))
                .filter(t -> type == null || type.isBlank() || type.equals(t.getType()))
                .filter(t -> status == null || status.isBlank() || status.equals(t.getStatus()))
                .sorted((a, b) -> b.getCreatedAt() == null ? 1 : a.getCreatedAt() == null ? -1
                        : b.getCreatedAt().compareTo(a.getCreatedAt()))
                .toList();
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
