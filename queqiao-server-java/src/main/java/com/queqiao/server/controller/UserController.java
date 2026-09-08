package com.queqiao.server.controller;

import com.queqiao.server.common.BizException;
import com.queqiao.server.common.Ids;
import com.queqiao.server.config.PasswordCodec;
import com.queqiao.server.entity.SysUser;
import com.queqiao.server.repo.SysUserRepository;
import com.queqiao.server.security.Masker;
import com.queqiao.server.security.RequestContext;
import com.queqiao.server.security.SensitiveType;
import com.queqiao.server.service.LogService;
import com.queqiao.server.service.TrialLimitService;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

/** 团队管理：系统账号（红娘/主管/管理员）的增删改查与启停。需要主管及以上权限。 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final SysUserRepository users;
    private final LogService logs;
    private final PasswordCodec passwords;
    private final TrialLimitService trial;

    public UserController(SysUserRepository users, LogService logs, PasswordCodec passwords,
                          TrialLimitService trial) {
        this.users = users;
        this.logs = logs;
        this.passwords = passwords;
        this.trial = trial;
    }

    private static final Set<String> ROLES = Set.of("ADMIN", "MANAGER", "AGENT");

    /** 账号列表（可按 role 过滤）。出参走 VO：不含口令哈希，手机/邮箱按角色脱敏。 */
    @GetMapping
    public List<Map<String, Object>> list(@RequestParam(required = false) String role) {
        RequestContext.requireManager();
        List<SysUser> all = (role == null || role.isBlank())
                ? users.findAll()
                : users.findByRole(role);
        return all.stream().map(this::toView).toList();
    }

    /** 新建账号（初始口令来自 queqiao.seed-password，首次登录应修改） */
    @PostMapping
    @Transactional
    public Map<String, Object> create(@RequestBody Map<String, Object> body) {
        RequestContext.requireAdmin();
        trial.assertNotTrial("新建系统账号");
        String username = str(body, "username");
        if (username.isBlank()) throw new BizException("用户名不能为空");
        if (users.existsByUsername(username)) throw new BizException("用户名已存在");
        String role = str(body, "role");
        if (!ROLES.contains(role)) throw new BizException("角色只能是 ADMIN / MANAGER / AGENT");
        String rawPwd = str(body, "password", passwords.seedPassword());
        if (rawPwd.length() < 6) throw new BizException("初始密码长度不能少于 6 位");
        SysUser u = new SysUser();
        u.setId(Ids.uid("U"));
        u.setUsername(username);
        u.setPassword(passwords.encode(rawPwd));
        u.setName(str(body, "name", username));
        u.setRole(role);
        u.setPhone(str(body, "phone"));
        u.setEmail(str(body, "email"));
        u.setAgentId(str(body, "agentId"));
        u.setStatus("启用");
        u.setAvatarBg("linear-gradient(135deg,#F4C6D0,#A8667D)");
        u.setCreatedAt(LocalDateTime.now());
        users.save(u);
        // 建号时可直接开通体验额度：{"isTrial":true,"trialQuota":50,"trialDays":30}
        if (bool(body, "isTrial")) {
            trial.grant(u, intOrNull(body, "trialQuota"), intOrNull(body, "trialDays"));
        }
        logs.record("新建账号", username, "角色 " + role
                + (bool(body, "isTrial") ? "（体验账号）" : ""));
        return toView(u);
    }

    /** 更新账号 */
    @PutMapping("/{id}")
    @Transactional
    public Map<String, Object> update(@PathVariable String id, @RequestBody Map<String, Object> body) {
        RequestContext.requireAdmin();
        trial.assertNotTrial("修改系统账号");
        SysUser u = users.findById(id).orElseThrow(() -> BizException.notFound("账号"));
        if (body.containsKey("name")) u.setName(str(body, "name"));
        if (body.containsKey("role")) {
            String role = str(body, "role");
            if (!ROLES.contains(role)) throw new BizException("角色只能是 ADMIN / MANAGER / AGENT");
            u.setRole(role);
        }
        if (body.containsKey("phone")) u.setPhone(str(body, "phone"));
        if (body.containsKey("email")) u.setEmail(str(body, "email"));
        if (body.containsKey("agentId")) u.setAgentId(str(body, "agentId"));
        if (body.containsKey("status")) u.setStatus(str(body, "status"));
        if (body.containsKey("password") && !str(body, "password").isBlank()) {
            String pwd = str(body, "password");
            if (pwd.length() < 6) throw new BizException("密码长度不能少于 6 位");
            u.setPassword(passwords.encode(pwd));
        }
        applyTrialFields(u, body);
        users.save(u);
        logs.record("更新账号", u.getUsername(), "角色 " + u.getRole());
        return toView(u);
    }

    /**
     * 开通 / 重置体验额度（管理员）。
     * body: {"quota":50,"days":30}；quota/days 缺省用服务端默认值（50 次 / 30 天），days<=0 表示不限天数。
     */
    @PostMapping("/{id}/trial-reset")
    @Transactional
    public Map<String, Object> resetTrial(@PathVariable String id, @RequestBody(required = false) Map<String, Object> body) {
        RequestContext.requireAdmin();
        trial.assertNotTrial("重置体验额度");
        SysUser u = users.findById(id).orElseThrow(() -> BizException.notFound("账号"));
        Map<String, Object> b = body == null ? Map.of() : body;
        trial.grant(u, intOrNull(b, "quota"), intOrNull(b, "days"));
        logs.record("重置体验额度", u.getUsername(),
                u.getTrialQuota() + " 次 / 至 " + u.getTrialExpireAt());
        return toView(u);
    }

    /** 解除体验限制，转为正式账号（管理员） */
    @PostMapping("/{id}/trial-revoke")
    @Transactional
    public Map<String, Object> revokeTrial(@PathVariable String id) {
        RequestContext.requireAdmin();
        trial.assertNotTrial("解除体验限制");
        SysUser u = users.findById(id).orElseThrow(() -> BizException.notFound("账号"));
        trial.revoke(u);
        logs.record("解除体验限制", u.getUsername(), "转为正式账号");
        return toView(u);
    }

    /** 删除账号（禁止删除当前登录账号） */
    @DeleteMapping("/{id}")
    @Transactional
    public Map<String, Object> delete(@PathVariable String id) {
        RequestContext.requireAdmin();
        trial.assertNotTrial("删除系统账号");
        SysUser u = users.findById(id).orElseThrow(() -> BizException.notFound("账号"));
        var ctx = RequestContext.get();
        if (ctx != null && u.getId().equals(ctx.userId())) {
            throw new BizException("不能删除当前登录账号");
        }
        users.delete(u);
        logs.record("删除账号", u.getUsername(), "");
        return Map.of("ok", true);
    }

    /**
     * 应用体验额度相关字段（仅在请求体显式携带时才动，避免误清）。
     *
     * <p>支持字段：
     * <ul>
     *   <li>{@code isTrial}：true 开通（同时按 trialQuota/trialDays 重置计数）；false 解除限制；</li>
     *   <li>{@code trialQuota}：调整次数额度（不重置已用次数）；</li>
     *   <li>{@code trialDays}：从当前时间起顺延的有效天数，&lt;=0 表示不限；</li>
     *   <li>{@code trialUsed}：手工修正已用次数；</li>
     *   <li>{@code trialLocked}：false 解锁（若额度已用尽会一并要求先提额，否则解锁即刻又被拦）。</li>
     * </ul>
     */
    private void applyTrialFields(SysUser u, Map<String, Object> body) {
        if (body.containsKey("isTrial")) {
            if (bool(body, "isTrial")) {
                trial.grant(u, intOrNull(body, "trialQuota"), intOrNull(body, "trialDays"));
                return;   // grant 已完整重置额度，其余字段无需再覆盖
            }
            trial.revoke(u);
        }
        if (body.containsKey("trialQuota")) {
            Integer q = intOrNull(body, "trialQuota");
            if (q != null && q <= 0) throw new BizException("体验次数必须大于 0");
            u.setTrialQuota(q);
        }
        if (body.containsKey("trialDays")) {
            Integer d = intOrNull(body, "trialDays");
            u.setTrialExpireAt(d != null && d > 0 ? LocalDateTime.now().plusDays(d) : null);
        }
        if (body.containsKey("trialUsed")) {
            Integer used = intOrNull(body, "trialUsed");
            u.setTrialUsed(used == null || used < 0 ? 0 : used);
        }
        if (body.containsKey("trialLocked")) {
            boolean locked = bool(body, "trialLocked");
            if (!locked && Boolean.TRUE.equals(u.getIsTrial())
                    && u.getTrialQuota() != null
                    && (u.getTrialUsed() == null ? 0 : u.getTrialUsed()) >= u.getTrialQuota()) {
                throw new BizException("次数已用尽，请同时提高体验次数或重置额度后再解锁");
            }
            u.setTrialLocked(locked);
        }
    }

    /** 出参 VO：剔除口令哈希；非管理员看到脱敏后的手机/邮箱 */
    private Map<String, Object> toView(SysUser u) {
        boolean full = RequestContext.isAdmin();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", u.getId());
        m.put("username", u.getUsername());
        m.put("name", u.getName());
        m.put("role", u.getRole());
        m.put("avatar", u.getAvatar());
        m.put("avatarBg", u.getAvatarBg());
        m.put("phone", Masker.mask(SensitiveType.PHONE, u.getPhone(), full));
        m.put("email", Masker.mask(SensitiveType.EMAIL, u.getEmail(), full));
        m.put("status", u.getStatus());
        m.put("agentId", u.getAgentId());
        m.put("lastLoginAt", u.getLastLoginAt());
        m.put("createdAt", u.getCreatedAt());
        m.put("trial", trial.snapshot(u));
        return m;
    }

    private static String str(Map<String, Object> b, String k) {
        Object v = b.get(k);
        return v == null ? "" : String.valueOf(v).trim();
    }

    private static String str(Map<String, Object> b, String k, String d) {
        String v = str(b, k);
        return v.isBlank() ? d : v;
    }

    private static boolean bool(Map<String, Object> b, String k) {
        Object v = b.get(k);
        if (v instanceof Boolean bb) return bb;
        return v != null && "true".equalsIgnoreCase(String.valueOf(v).trim());
    }

    private static Integer intOrNull(Map<String, Object> b, String k) {
        Object v = b.get(k);
        if (v == null) return null;
        if (v instanceof Number n) return n.intValue();
        String s = String.valueOf(v).trim();
        if (s.isEmpty()) return null;
        try {
            return Integer.valueOf(s);
        } catch (NumberFormatException e) {
            throw new BizException(k + " 必须是整数");
        }
    }
}
