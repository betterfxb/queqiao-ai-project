package com.queqiao.server.controller;

import com.queqiao.server.common.BizException;
import com.queqiao.server.config.JwtUtil;
import com.queqiao.server.config.PasswordCodec;
import com.queqiao.server.config.SecurityFilter;
import com.queqiao.server.entity.Member;
import com.queqiao.server.entity.SysUser;
import com.queqiao.server.repo.MemberRepository;
import com.queqiao.server.repo.SysUserRepository;
import com.queqiao.server.service.TrialLimitService;
import java.util.List;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/** 认证：登录 / 当前用户 / 登出 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final SysUserRepository users;
    private final MemberRepository members;
    private final JwtUtil jwt;
    private final PasswordCodec passwords;
    private final TrialLimitService trial;

    public AuthController(SysUserRepository users, MemberRepository members, JwtUtil jwt,
                          PasswordCodec passwords, TrialLimitService trial) {
        this.users = users;
        this.members = members;
        this.jwt = jwt;
        this.passwords = passwords;
        this.trial = trial;
    }

    /** 登录：账号 + 密码（滑块验证在前端完成，后端做失败计数保护） */
    @PostMapping("/login")
    @Transactional
    public Map<String, Object> login(@RequestBody Map<String, String> body) {
        String username = trim(body.get("username"));
        String password = body.get("password");
        if (username.isEmpty() || password == null || password.isBlank()) {
            throw new BizException("请输入账号和密码");
        }
        if (password.length() < 6) {
            throw new BizException("密码长度不能少于 6 位");
        }

        SysUser u = users.findByUsername(username).orElse(null);
        if (u == null) {
            throw BizException.unauthorized("账号不存在或密码错误");
        }
        if (!passwords.matches(username, password, u.getPassword())) {
            throw BizException.unauthorized("账号不存在或密码错误");
        }
        if ("停用".equals(u.getStatus())) {
            throw new BizException(403, "账号已停用，请联系管理员");
        }
        // 体验账号：次数用尽 / 到期 / 已锁定则不再发令牌（403，前端仅提示不登出）
        trial.checkLogin(u);

        // 历史 SHA-256 口令在校验通过后静默升级为 BCrypt
        if (passwords.needsUpgrade(u.getPassword())) {
            u.setPassword(passwords.encode(password));
            log.info("账号 {} 的口令已升级为 BCrypt", username);
        }
        u.setLastLoginAt(LocalDateTime.now());
        users.save(u);

        String token = jwt.sign(u.getId(), u.getName(), u.getRole());
        Map<String, Object> res = new HashMap<>();
        res.put("token", token);
        res.put("expiresIn", jwt.expireSeconds());
        res.put("user", toView(u));
        return res;
    }

    /** 当前登录用户 */
    @GetMapping("/me")
    public Map<String, Object> me(HttpServletRequest req) {
        String uid = (String) req.getAttribute(SecurityFilter.ATTR_USER_ID);
        if (uid == null) throw BizException.unauthorized("未登录");
        SysUser u = users.findById(uid).orElseThrow(() -> BizException.unauthorized("用户不存在"));
        return toView(u);
    }

    /** 更新当前登录用户资料（姓名 / 手机 / 邮箱） */
    @PutMapping("/profile")
    @Transactional
    public Map<String, Object> updateProfile(HttpServletRequest req, @RequestBody Map<String, String> body) {
        String uid = (String) req.getAttribute(SecurityFilter.ATTR_USER_ID);
        if (uid == null) throw BizException.unauthorized("未登录");
        SysUser u = users.findById(uid).orElseThrow(() -> BizException.unauthorized("用户不存在"));

        if (body.containsKey("name")) {
            String name = trim(body.get("name"));
            if (name.isEmpty()) throw new BizException("姓名不能为空");
            u.setName(name);
        }
        if (body.containsKey("phone")) u.setPhone(trim(body.get("phone")));
        if (body.containsKey("email")) u.setEmail(trim(body.get("email")));
        users.save(u);
        return toView(u);
    }

    /** 修改登录密码 */
    @PostMapping("/change-password")
    @Transactional
    public Map<String, Object> changePassword(HttpServletRequest req, @RequestBody Map<String, String> body) {
        String uid = (String) req.getAttribute(SecurityFilter.ATTR_USER_ID);
        if (uid == null) throw BizException.unauthorized("未登录");
        SysUser u = users.findById(uid).orElseThrow(() -> BizException.unauthorized("用户不存在"));

        String oldPwd = body.get("oldPassword");
        String newPwd = body.get("newPassword");
        if (oldPwd == null || oldPwd.isBlank()) throw new BizException("请输入原密码");
        if (newPwd == null || newPwd.length() < 6) throw new BizException("新密码长度不能少于 6 位");
        if (!passwords.matches(u.getUsername(), oldPwd, u.getPassword())) {
            throw new BizException("原密码不正确");
        }
        if (passwords.matches(u.getUsername(), newPwd, u.getPassword())) {
            throw new BizException("新密码不能与原密码相同");
        }
        u.setPassword(passwords.encode(newPwd));
        users.save(u);
        return Map.of("ok", true, "message", "密码修改成功");
    }

    /** 登出（无状态 JWT：前端清除即可，这里保留端点用于埋点/审计） */
    @PostMapping("/logout")
    public Map<String, Object> logout() {
        return Map.of("ok", true);
    }

    /** 刷新令牌 */
    @PostMapping("/refresh")
    public Map<String, Object> refresh(HttpServletRequest req) {
        String uid = (String) req.getAttribute(SecurityFilter.ATTR_USER_ID);
        if (uid == null) throw BizException.unauthorized("未登录");
        SysUser u = users.findById(uid).orElseThrow(() -> BizException.unauthorized("用户不存在"));
        return Map.of("token", jwt.sign(u.getId(), u.getName(), u.getRole()),
                "expiresIn", jwt.expireSeconds());
    }

    /** 滑块验证挑战（返回随机缺口位置，前端做拼图；服务端仅做形式校验） */
    @GetMapping("/captcha")
    public Map<String, Object> captcha() {
        int x = 60 + (int) (Math.random() * 180);
        return Map.of("challenge", java.util.UUID.randomUUID().toString(), "x", x, "tolerance", 8);
    }

    /** 会员端登录：手机号 或 微信 openid 换取会员身份令牌（MEMBER 角色）
     *  - 后端无独立会员账号体系，会员身份即 Member 档案；sub/name 均用 memberId，便于走 /user WS 个人目的地。 */
    @PostMapping("/member-login")
    @Transactional
    public Map<String, Object> memberLogin(@RequestBody Map<String, String> body) {
        String phone = trim(body.get("phone"));
        String openId = trim(body.get("wechatOpenId"));
        if (phone.isEmpty() && openId.isEmpty()) {
            throw new BizException("请输入手机号或通过微信授权登录");
        }
        Member m;
        if (!openId.isEmpty()) {
            m = members.findAll().stream()
                    .filter(x -> openId.equals(x.getWechatOpenId())).findFirst().orElse(null);
        } else {
            List<Member> byPhone = members.findByPhone(phone);
            m = byPhone.isEmpty() ? null : byPhone.get(0);
        }
        if (m == null) {
            throw BizException.unauthorized("未找到会员档案，请先由红娘为您建档或绑定微信");
        }
        // 微信登录时若尚未绑定 openid，则顺带绑定（幂等）
        if (!openId.isEmpty() && (m.getWechatOpenId() == null || !openId.equals(m.getWechatOpenId()))) {
            m.setWechatOpenId(openId);
            members.save(m);
        }
        String token = jwt.sign(m.getId(), m.getId(), "MEMBER");
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("token", token);
        res.put("expiresIn", jwt.expireSeconds());
        Map<String, Object> user = new LinkedHashMap<>();
        user.put("id", m.getId());
        user.put("name", m.getName());
        user.put("role", "MEMBER");
        user.put("gender", m.getGender());
        user.put("avatarBg", m.getAvatarBg());
        res.put("user", user);
        return res;
    }

    private Map<String, Object> toView(SysUser u) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", u.getId());
        m.put("username", u.getUsername());
        m.put("name", u.getName());
        m.put("role", u.getRole());
        m.put("avatar", u.getAvatar());
        m.put("avatarBg", u.getAvatarBg());
        m.put("phone", u.getPhone());
        m.put("email", u.getEmail());
        m.put("agentId", u.getAgentId());
        m.put("lastLoginAt", u.getLastLoginAt());
        // 体验账号状态：前端据此展示"剩余 N 次 / M 天"横幅；正式账号仅返回 isTrial=false
        m.put("trial", trial.snapshot(u));
        return m;
    }

    private static String trim(String s) {
        return s == null ? "" : s.trim();
    }
}
