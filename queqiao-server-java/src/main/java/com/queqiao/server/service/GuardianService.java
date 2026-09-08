package com.queqiao.server.service;

import com.queqiao.server.common.BizException;
import com.queqiao.server.common.Ids;
import com.queqiao.server.entity.Member;
import com.queqiao.server.entity.MemberGuardian;
import com.queqiao.server.repo.MemberGuardianRepository;
import com.queqiao.server.repo.MemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 家长参与关系（2-C）：绑定 / 授权 / 撤销 + 家长可见子女范围计算。
 * 权限边界：仅子女本人可绑定与授权/撤销；家长仅可在「已授权」后查看对应子女会话。
 */
@Service
public class GuardianService {

    public static final String PENDING = "待授权";
    public static final String AUTHORIZED = "已授权";
    public static final String REVOKED = "已撤销";

    private final MemberGuardianRepository repo;
    private final MemberRepository members;
    private final LogService logs;

    public GuardianService(MemberGuardianRepository repo, MemberRepository members, LogService logs) {
        this.repo = repo;
        this.members = members;
        this.logs = logs;
    }

    /** 子女绑定家长（自己操作）。同一手机重复绑定返回既有有效记录；已撤销可重新激活。 */
    @Transactional
    public MemberGuardian bind(String memberId, String guardianPhone, String guardianName, String relation) {
        if (guardianPhone == null || guardianPhone.isBlank()) throw new BizException("缺少家长手机号");
        Member m = members.findById(memberId).orElseThrow(() -> BizException.notFound("会员"));
        Optional<MemberGuardian> exist = repo.findByMemberIdAndGuardianPhone(memberId, guardianPhone);
        if (exist.isPresent()) {
            MemberGuardian g = exist.get();
            if (AUTHORIZED.equals(g.getAuthStatus()) || PENDING.equals(g.getAuthStatus())) return g;
            g.setAuthStatus(PENDING);              // 已撤销 → 重新走待授权
            g.setGuardianName(guardianName);
            g.setRelation(relation);
            g.setUpdatedAt(LocalDateTime.now());
            repo.save(g);
            return g;
        }
        MemberGuardian g = new MemberGuardian();
        g.setId(Ids.biz("GD"));
        g.setMemberId(memberId);
        g.setMemberName(m.getName());
        g.setGuardianPhone(guardianPhone);
        g.setGuardianName(guardianName);
        // 若家长手机号已对应某会员，记录其 memberId（用于家长登录态身份校验）
        List<Member> gm = members.findByPhone(guardianPhone);
        if (!gm.isEmpty()) g.setGuardianMemberId(gm.get(0).getId());
        g.setRelation(relation);
        g.setAuthStatus(PENDING);
        g.setRole("communicate");
        g.setCreatedAt(LocalDateTime.now());
        g.setUpdatedAt(LocalDateTime.now());
        repo.save(g);
        logs.record("绑定家长", memberId, m.getName() + " 绑定家长 " + guardianName + "(" + guardianPhone + ")");
        return g;
    }

    /** 子女查看自己绑定的家长列表 */
    public List<MemberGuardian> list(String memberId) {
        return repo.findByMemberIdOrderByCreatedAtDesc(memberId);
    }

    /** 子女授权某家长（仅本人） */
    @Transactional
    public MemberGuardian authorize(String memberId, String id) {
        MemberGuardian g = repo.findById(id).orElseThrow(() -> BizException.notFound("家长关系"));
        if (!memberId.equals(g.getMemberId())) throw BizException.forbidden("无权操作该家长关系");
        g.setAuthStatus(AUTHORIZED);
        g.setAuthorizedAt(LocalDateTime.now());
        g.setUpdatedAt(LocalDateTime.now());
        repo.save(g);
        logs.record("授权家长", memberId, g.getGuardianName());
        return g;
    }

    /** 子女撤销某家长（仅本人） */
    @Transactional
    public MemberGuardian revoke(String memberId, String id) {
        MemberGuardian g = repo.findById(id).orElseThrow(() -> BizException.notFound("家长关系"));
        if (!memberId.equals(g.getMemberId())) throw BizException.forbidden("无权操作该家长关系");
        g.setAuthStatus(REVOKED);
        g.setUpdatedAt(LocalDateTime.now());
        repo.save(g);
        logs.record("撤销家长授权", memberId, g.getGuardianName());
        return g;
    }

    /** 家长（按手机）当前可代管的子女 memberId 列表（已授权且未撤销） */
    public List<String> visibleChildren(String guardianPhone) {
        if (guardianPhone == null || guardianPhone.isBlank()) return List.of();
        return repo.findByGuardianPhoneAndAuthStatus(guardianPhone, AUTHORIZED).stream()
                .map(MemberGuardian::getMemberId).distinct().toList();
    }
}
