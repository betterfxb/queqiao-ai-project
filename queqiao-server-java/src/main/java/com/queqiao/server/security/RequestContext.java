package com.queqiao.server.security;

import com.queqiao.server.common.BizException;

/**
 * 请求级身份上下文（ThreadLocal）。
 * 由 {@link com.queqiao.server.config.SecurityFilter} 在认证通过后写入，
 * 响应序列化及业务层（数据范围、脱敏、权限判定）可无侵入读取当前用户。
 */
public final class RequestContext {

    private static final ThreadLocal<Ctx> TL = new ThreadLocal<>();

    private RequestContext() {
    }

    public record Ctx(String userId, String userName, String role) {
    }

    public static void set(String userId, String userName, String role) {
        TL.set(new Ctx(userId, userName, role));
    }

    public static Ctx get() {
        return TL.get();
    }

    public static void clear() {
        TL.remove();
    }

    public static boolean isAdmin() {
        Ctx c = TL.get();
        return c != null && "ADMIN".equals(c.role());
    }

    public static boolean isAgent() {
        Ctx c = TL.get();
        return c != null && "AGENT".equals(c.role());
    }

    /** 主管或管理员（用于运营分析/团队管理等只读或管理类接口） */
    public static boolean isManager() {
        Ctx c = TL.get();
        return c != null && ("ADMIN".equals(c.role()) || "MANAGER".equals(c.role()));
    }

    /** 当前登录红娘的展示名（用于数据范围过滤：仅本人负责会员） */
    public static String agentName() {
        Ctx c = TL.get();
        return c == null ? null : c.userName();
    }

    /** 当前登录会员的 id（会员令牌的 sub == memberId）。非会员返回 null */
    public static String currentMemberId() {
        Ctx c = TL.get();
        return c == null ? null : c.userId();
    }

    public static boolean isMember() {
        Ctx c = TL.get();
        return c != null && "MEMBER".equals(c.role());
    }

    /** 仅会员可访问。未登录→401，非会员→403 */
    public static void requireMember() {
        if (isMember()) return;
        if (TL.get() == null) {
            throw BizException.unauthorized("未登录");
        }
        throw BizException.forbidden("需要会员身份");
    }

    /** 非管理员一律拒绝（用于运营分析类接口）。未登录→401，已登录但角色不足→403 */
    public static void requireAdmin() {
        if (isAdmin()) return;
        if (TL.get() == null) {
            throw BizException.unauthorized("未登录");
        }
        throw BizException.forbidden("需要管理员权限");
    }

    /** 仅要求已登录（用于红娘本人视角接口）。未登录→401 */
    public static void requireAuth() {
        if (TL.get() == null) {
            throw BizException.unauthorized("未登录");
        }
    }

    /** 主管及以上权限（用于运营分析/团队管理等）。未登录→401，已登录但角色不足→403 */
    public static void requireManager() {
        if (isManager()) return;
        if (TL.get() == null) {
            throw BizException.unauthorized("未登录");
        }
        throw BizException.forbidden("需要主管及以上权限");
    }
}
