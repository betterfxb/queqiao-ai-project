package com.queqiao.server.service;

import com.queqiao.server.common.BizException;
import com.queqiao.server.entity.SysUser;
import com.queqiao.server.repo.SysUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 体验账号额度服务（次数 + 天数双限）
 *
 * <p>定位：demo 之类的体验账号需要能走通「全流程功能」，因此角色给到 ADMIN，
 * 但通过额度把使用范围收住 —— 次数与天数任一触达即自动锁定，需管理员重新开通。
 *
 * <p>三个约束点：
 * <ol>
 *   <li>{@link #checkLogin(SysUser)}：登录时校验，已锁定 / 已过期 / 次数用尽直接 403，不发令牌；</li>
 *   <li>{@link #tick(String)}：由 SecurityFilter 在每个业务写操作（POST/PUT/DELETE）前调用，
 *       校验并累加次数，本次调用后若刚好触顶则立即锁定；</li>
 *   <li>{@link #assertNotTrial(String)}：账号管理等敏感操作调用，防止体验账号给自己提额/改角色。</li>
 * </ol>
 *
 * <p>并发说明：H2/单实例场景下用行级更新即可；计数存在极端并发下少算的可能，
 * 但体验限制属于业务软约束，不追求强一致（真正的越权仍由角色 + 归属校验兜住）。
 */
@Service
public class TrialLimitService {

    private static final Logger log = LoggerFactory.getLogger(TrialLimitService.class);

    /** 未显式设置时的默认体验额度 */
    public static final int DEFAULT_QUOTA = 50;
    /** 未显式设置时的默认体验天数 */
    public static final int DEFAULT_DAYS = 30;

    private final SysUserRepository users;

    public TrialLimitService(SysUserRepository users) {
        this.users = users;
    }

    // ============================================================
    // 判定
    // ============================================================

    /** 是否体验账号 */
    public boolean isTrial(SysUser u) {
        return u != null && Boolean.TRUE.equals(u.getIsTrial());
    }

    private static int used(SysUser u) {
        return u.getTrialUsed() == null ? 0 : u.getTrialUsed();
    }

    private static int quota(SysUser u) {
        return u.getTrialQuota() == null ? DEFAULT_QUOTA : u.getTrialQuota();
    }

    /** 剩余可用次数（不小于 0） */
    public int remain(SysUser u) {
        return Math.max(0, quota(u) - used(u));
    }

    /** 剩余天数（向上取整，已过期返回 0；未设置截止时间返回 -1 表示不限） */
    public long remainDays(SysUser u) {
        if (u.getTrialExpireAt() == null) return -1;
        LocalDateTime now = LocalDateTime.now();
        if (!u.getTrialExpireAt().isAfter(now)) return 0;
        long hours = ChronoUnit.HOURS.between(now, u.getTrialExpireAt());
        return (hours + 23) / 24;
    }

    private boolean expired(SysUser u) {
        return u.getTrialExpireAt() != null && !u.getTrialExpireAt().isAfter(LocalDateTime.now());
    }

    // ============================================================
    // 约束
    // ============================================================

    /**
     * 登录校验：体验账号已锁定 / 已过期 / 次数用尽时拒绝发令牌。
     * 过期属于「到点自动失效」，这里顺手把锁定状态落库，便于管理端一眼看出原因。
     */
    @Transactional
    public void checkLogin(SysUser u) {
        if (!isTrial(u)) return;

        // 种子/历史数据未设过期时间：首次登录自动开通默认体验期（补 expireAt）
        if (u.getTrialExpireAt() == null && !Boolean.TRUE.equals(u.getTrialLocked())) {
            grant(u, quota(u) <= 0 ? null : quota(u), null);
            log.info("[trial] 账号 {} 首次登录自动激活体验期（{} 次 / {} 天）",
                    u.getUsername(), quota(u), DEFAULT_DAYS);
        }

        if (Boolean.TRUE.equals(u.getTrialLocked())) {
            throw new BizException(403, lockedMessage(u));
        }
        if (expired(u)) {
            lock(u, "体验期已到期");
            throw new BizException(403, "体验期已于 " + fmt(u.getTrialExpireAt())
                    + " 到期，如需继续体验请联系我们重新开通");
        }
        if (remain(u) <= 0) {
            lock(u, "体验次数已用尽");
            throw new BizException(403, "体验次数已用尽（共 " + quota(u)
                    + " 次），如需继续体验请联系我们重新开通");
        }
    }

    /**
     * 业务写操作计数：由 SecurityFilter 在放行前调用。
     *
     * <p>用独立事务（REQUIRES_NEW）：过滤器不在业务事务内，且计数不应被后续业务回滚带走 —— 
     * 「用了一次机会」这件事本身就该落账。
     *
     * @param userId 当前登录用户 id
     * @throws BizException 403：额度耗尽 / 已过期 / 已锁定
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void tick(String userId) {
        if (userId == null || userId.isBlank()) return;
        Optional<SysUser> opt = users.findById(userId);
        if (opt.isEmpty()) return;
        SysUser u = opt.get();
        if (!isTrial(u)) return;

        if (Boolean.TRUE.equals(u.getTrialLocked())) {
            throw new BizException(403, lockedMessage(u));
        }
        if (expired(u)) {
            lock(u, "体验期已到期");
            throw new BizException(403, "体验期已于 " + fmt(u.getTrialExpireAt())
                    + " 到期，如需继续体验请联系我们重新开通");
        }
        if (remain(u) <= 0) {
            lock(u, "体验次数已用尽");
            throw new BizException(403, "体验次数已用尽（共 " + quota(u)
                    + " 次），如需继续体验请联系我们重新开通");
        }

        u.setTrialUsed(used(u) + 1);
        // 本次消耗后刚好触顶：立刻锁定，避免下一次请求才发现
        if (u.getTrialUsed() >= quota(u)) {
            u.setTrialLocked(true);
            log.info("[trial] 体验账号 {} 次数已用尽（{}/{}），自动锁定",
                    u.getUsername(), u.getTrialUsed(), quota(u));
        }
        users.save(u);
    }

    /**
     * 敏感操作护栏：体验账号一律禁止（防止自我提额、改角色、增删账号等提权路径）。
     *
     * @param action 用于错误提示的动作名，如「管理系统账号」
     */
    public void assertNotTrial(String action) {
        var ctx = com.queqiao.server.security.RequestContext.get();
        if (ctx == null || ctx.userId() == null) return;
        SysUser u = users.findById(ctx.userId()).orElse(null);
        if (isTrial(u)) {
            throw new BizException(403, "体验账号不可" + action + "，如需完整权限请联系我们开通正式账号");
        }
    }

    // ============================================================
    // 管理
    // ============================================================

    /**
     * 开通 / 重置体验额度（管理员操作）。
     *
     * @param u     目标账号
     * @param quota 次数额度，null 用默认值
     * @param days  有效天数，null 用默认值；&lt;=0 表示不限天数
     */
    @Transactional
    public SysUser grant(SysUser u, Integer quota, Integer days) {
        int q = quota == null ? DEFAULT_QUOTA : quota;
        if (q <= 0) throw new BizException("体验次数必须大于 0");
        int d = days == null ? DEFAULT_DAYS : days;

        u.setIsTrial(true);
        u.setTrialQuota(q);
        u.setTrialUsed(0);
        u.setTrialLocked(false);
        u.setTrialExpireAt(d > 0 ? LocalDateTime.now().plusDays(d) : null);
        users.save(u);
        log.info("[trial] 已为账号 {} 开通体验额度：{} 次 / {}", u.getUsername(), q,
                d > 0 ? d + " 天" : "不限天数");
        return u;
    }

    /** 关闭体验限制（转为正式账号） */
    @Transactional
    public SysUser revoke(SysUser u) {
        u.setIsTrial(false);
        u.setTrialLocked(false);
        users.save(u);
        log.info("[trial] 账号 {} 已解除体验限制", u.getUsername());
        return u;
    }

    private void lock(SysUser u, String reason) {
        if (!Boolean.TRUE.equals(u.getTrialLocked())) {
            u.setTrialLocked(true);
            users.save(u);
            log.info("[trial] 体验账号 {} 已锁定：{}", u.getUsername(), reason);
        }
    }

    private String lockedMessage(SysUser u) {
        if (expired(u)) {
            return "体验期已于 " + fmt(u.getTrialExpireAt()) + " 到期，如需继续体验请联系我们重新开通";
        }
        return "体验额度已用尽（共 " + quota(u) + " 次），如需继续体验请联系我们重新开通";
    }

    // ============================================================
    // 出参
    // ============================================================

    /** 体验状态快照：挂到登录响应 / 用户列表，前端可据此展示"剩余 N 次 / M 天"横幅 */
    public Map<String, Object> snapshot(SysUser u) {
        Map<String, Object> m = new LinkedHashMap<>();
        boolean trial = isTrial(u);
        m.put("isTrial", trial);
        if (!trial) return m;
        m.put("quota", quota(u));
        m.put("used", used(u));
        m.put("remain", remain(u));
        m.put("expireAt", u.getTrialExpireAt());
        long days = remainDays(u);
        m.put("remainDays", days < 0 ? null : days);
        m.put("locked", Boolean.TRUE.equals(u.getTrialLocked()));
        m.put("notice", trial
                ? "体验模式：剩余 " + remain(u) + " 次操作"
                + (days < 0 ? "" : "，" + days + " 天后到期")
                : null);
        return m;
    }

    private static String fmt(LocalDateTime t) {
        return t == null ? "" : t.toLocalDate().toString();
    }
}
