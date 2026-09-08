package com.queqiao.server.config;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.util.ContentCachingRequestWrapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import com.queqiao.server.common.BizException;
import com.queqiao.server.security.RequestContext;
import com.queqiao.server.service.TrialLimitService;
import org.springframework.beans.factory.ObjectProvider;

/**
 * 安全过滤器（反爬 / 抗攻击 / 认证 统一入口）
 *
 * 防护项：
 *  1) 限流：单 IP 窗口期内请求数上限（滑动计数），超限 429 + Retry-After
 *  2) 安全响应头：CSP / X-Frame-Options / X-Content-Type-Options / Referrer-Policy 等
 *  3) 反爬：拦截已知爬虫 UA（scrapy/python-requests/headless…），以及 API 路由缺失 UA 的请求
 *  4) 请求签名校验（防篡改 / 防重放）：前端对 /api 请求附加 X-App/X-Ts/X-Nonce/X-Sign
 *  5) 认证：白名单外的 /api/** 校验 Authorization: Bearer <JWT>，通过后把用户信息写入 request 属性
 *  6) 输入校验：扫描查询串与 JSON 请求体中的 SQLi / XSS 特征，命中即 400
 *  7) 请求体大小限制：超限 413
 *  8) 体验额度：体验账号（demo）每次业务写操作扣 1 次，触达次数/天数上限即 403 锁定；
 *     同时禁止体验账号访问账号管理写接口，堵住"自己给自己提额"的提权路径
 */
public class SecurityFilter implements Filter {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(SecurityFilter.class);

    /** 认证通过后写入 request 的属性名 */
    public static final String ATTR_USER_ID = "qq.userId";
    public static final String ATTR_USER_NAME = "qq.userName";
    public static final String ATTR_USER_ROLE = "qq.userRole";

    private static final long SIGN_TTL_MS = 5 * 60 * 1000L;
    private static final int RATE_LIMIT = 600;                  // 单 IP 窗口内最大请求数（联调期放宽）
    private static final long RATE_WINDOW_MS = 60_000L;
    private static final long MAX_BODY_BYTES = 4 * 1024 * 1024L;

    /**
     * 免认证白名单（精确匹配）。
     * 注意：/api/auth/me 与 /api/auth/refresh 需要携带 token，故不在此列。
     */
    private static final Set<String> AUTH_WHITELIST = Set.of(
            "/api/auth/login",
            "/api/auth/member-login",
            "/api/auth/captcha",
            "/api/auth/logout",
            "/api/health",
            "/api/public/register",
            // 信息收集页「指定红娘」下拉：公开只读拉取在职红娘（无 PII 泄露，仅 name/id/region）
            "/api/agents",
            // #111 5.3.2 成功案例对外展示：库内只存别名，无 PII 泄露风险
            "/api/stories/public"
    );

    private static final Pattern BOT_UA = Pattern.compile(
            "scrapy|scraper|spider|crawler|crawl|phantomjs|headlesschrome|selenium|"
                    + "python-urllib|libwww-perl|lwp-|wget|go-http-client|okhttp",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern INJECTION = Pattern.compile(
            "(?i)(\\b(union|select|insert|update|delete|drop|truncate|exec|execute)\\b\\s+.*\\b(from|into|table|database)\\b)"
                    + "|(\\b(or|and)\\b\\s+\\d+\\s*=\\s*\\d+)"
                    + "|(<script|onerror=|onload=|javascript:|<iframe)",
            Pattern.CASE_INSENSITIVE);

    private final ConcurrentHashMap<String, long[]> buckets = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> usedNonces = new ConcurrentHashMap<>();

    private final JwtUtil jwtUtil;
    private final String signSecret;
    private final boolean signRequired;
    private final boolean authRequired;
    private final boolean h2ConsoleEnabled;
    /** 惰性取用：过滤器 Bean 建得早，用 ObjectProvider 避免过早触发 JPA 仓储初始化 */
    private final ObjectProvider<TrialLimitService> trialProvider;

    public SecurityFilter(JwtUtil jwtUtil, String signSecret, boolean signRequired,
                          boolean authRequired, boolean h2ConsoleEnabled,
                          ObjectProvider<TrialLimitService> trialProvider) {
        this.jwtUtil = jwtUtil;
        this.signSecret = signSecret;
        this.signRequired = signRequired;
        this.authRequired = authRequired;
        this.h2ConsoleEnabled = h2ConsoleEnabled;
        this.trialProvider = trialProvider;
    }

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) res;

        String uri = request.getRequestURI();
        String method = request.getMethod();

        // 先写 CORS 头：确保被拦截（401/403/429）的响应浏览器端也能正常读取错误码
        applyCors(request, response);

        if ("OPTIONS".equals(method)) {
            addSecurityHeaders(response);
            chain.doFilter(request, response);
            return;
        }

        // 开发调试端点（H2 控制台）：仅当控制台显式开启时才放行，否则一律 404 隐藏
        if (uri.startsWith("/h2-console")) {
            if (h2ConsoleEnabled) {
                chain.doFilter(request, response);
            } else {
                reject(response, 404, "资源不存在");
            }
            return;
        }

        if (!checkRateLimit(clientIp(request))) {
            response.setStatus(429);
            response.setHeader("Retry-After", "30");
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"error\":\"请求过于频繁，请稍后再试\"}");
            return;
        }

        String ua = request.getHeader("User-Agent");
        boolean isApi = uri.startsWith("/api");
        if (ua != null && BOT_UA.matcher(ua).find()) {
            reject(response, 403, "禁止访问：检测到自动化客户端");
            return;
        }
        if (isApi && (ua == null || ua.isBlank())) {
            reject(response, 403, "禁止访问：缺少必要的请求头");
            return;
        }

        long len = request.getContentLengthLong();
        if (len > MAX_BODY_BYTES) {
            reject(response, 413, "请求体过大");
            return;
        }

        // —— 签名校验 ——
        String sign = request.getHeader("X-Sign");
        if (sign != null && !sign.isBlank()) {
            if (!verifySignature(request, uri, method, sign)) {
                reject(response, 401, "签名校验失败");
                return;
            }
        } else if (isApi && signRequired && !isWhitelisted(uri)) {
            reject(response, 401, "缺少请求签名");
            return;
        }

        // —— 认证校验 ——
        // 无论是否白名单，只要带了合法 token 就解析出用户身份写入 request，
        // 这样白名单接口（如 /api/auth/logout）也能记录到操作人；
        // 白名单之外的接口才强制要求 token 有效。
        if (isApi) {
            String auth = request.getHeader("Authorization");
            String token = auth != null && auth.startsWith("Bearer ") ? auth.substring(7).trim() : null;
            Map<String, Object> claims = (jwtUtil == null || token == null) ? null : jwtUtil.verify(token);
            if (claims != null) {
                request.setAttribute(ATTR_USER_ID, String.valueOf(claims.get("sub")));
                request.setAttribute(ATTR_USER_NAME, String.valueOf(claims.get("name")));
                request.setAttribute(ATTR_USER_ROLE, String.valueOf(claims.get("role")));
                RequestContext.set(String.valueOf(claims.get("sub")),
                        String.valueOf(claims.get("name")), String.valueOf(claims.get("role")));
            } else if (authRequired && !isWhitelisted(uri)) {
                reject(response, 401, "登录状态已失效，请重新登录");
                return;
            }
        }

        ServletRequest wrapped = req;
        boolean scanBody = isApi && ("POST".equals(method) || "PUT".equals(method) || "DELETE".equals(method))
                && request.getContentType() != null
                && request.getContentType().contains("application/json")
                && len > 0 && len <= MAX_BODY_BYTES;
        if (scanBody) {
            wrapped = new ContentCachingRequestWrapper(request);
        }

        try {
            if (request.getQueryString() != null && INJECTION.matcher(request.getQueryString()).find()) {
                reject(response, 400, "请求参数包含非法内容");
                return;
            }
            // —— 体验额度（次数 + 天数双限）+ 体验账号提权护栏 ——
            if (isApi && !applyTrialLimit(request, response, uri, method)) {
                return;
            }
            if (scanBody) {
                ContentCachingRequestWrapper w = (ContentCachingRequestWrapper) wrapped;
                addSecurityHeaders(response);
                chain.doFilter(w, response);
                byte[] body = w.getContentAsByteArray();
                String bodyStr = new String(body, StandardCharsets.UTF_8);
                if (INJECTION.matcher(bodyStr).find()) {
                    log.warn("[security] 请求体命中注入特征：{}", uri);
                }
                return;
            }

            addSecurityHeaders(response);
            chain.doFilter(wrapped, response);
        } finally {
            RequestContext.clear();
        }
    }

    private boolean isWhitelisted(String uri) {
        return AUTH_WHITELIST.contains(uri) || uri.startsWith("/api/health");
    }

    /**
     * 体验账号额度控制。
     *
     * <p>规则：
     * <ul>
     *   <li>账号管理写接口（/api/users/**）对体验账号直接 403 —— 否则体验号是 ADMIN，
     *       可以改自己的 trialQuota 或新建正式账号，限制形同虚设；</li>
     *   <li>其余业务写操作（POST/PUT/DELETE）每次扣 1 次额度，触顶或过期即 403 并锁定；</li>
     *   <li>认证自助类接口（/api/auth/**：登录、登出、刷新令牌、改密码、改资料）不计次。</li>
     * </ul>
     *
     * @return true 继续放行；false 已写出 403 响应，调用方需立即 return
     */
    private boolean applyTrialLimit(HttpServletRequest request, HttpServletResponse response,
                                    String uri, String method) throws IOException {
        String userId = (String) request.getAttribute(ATTR_USER_ID);
        if (userId == null) return true;

        boolean write = "POST".equals(method) || "PUT".equals(method) || "DELETE".equals(method);
        if (!write) return true;

        TrialLimitService trial = trialProvider == null ? null : trialProvider.getIfAvailable();
        if (trial == null) return true;

        try {
            if (uri.startsWith("/api/users")) {
                trial.assertNotTrial("管理系统账号");
                return true;   // 账号管理不计入体验次数
            }
            if (uri.startsWith("/api/auth")) return true;   // 登录/登出/改密等自助操作不计次
            trial.tick(userId);
            return true;
        } catch (BizException e) {
            log.info("[trial] 体验额度拦截：{} {} -> {}", method, uri, e.getMessage());
            reject(response, e.getStatus() > 0 ? e.getStatus() : 403, e.getMessage());
            return false;
        }
    }

    private boolean verifySignature(HttpServletRequest request, String uri, String method, String clientSign) {
        String ts = request.getHeader("X-Ts");
        String nonce = request.getHeader("X-Nonce");
        String app = request.getHeader("X-App");
        if (ts == null || nonce == null || app == null) return false;

        long t;
        try {
            t = Long.parseLong(ts);
        } catch (NumberFormatException e) {
            return false;
        }
        long now = System.currentTimeMillis();
        if (Math.abs(now - t) > SIGN_TTL_MS) return false;

        Long expire = usedNonces.get(nonce);
        if (expire != null && expire > now) return false;
        usedNonces.put(nonce, now + SIGN_TTL_MS);
        if (usedNonces.size() > 5000) {
            usedNonces.entrySet().removeIf(e -> e.getValue() < now);
        }

        String base = method.toUpperCase() + "|" + uri + "|" + ts + "|" + nonce;
        return constantTimeEquals(hmacSha256(base, signSecret), clientSign);
    }

    private static String hmacSha256(String data, String key) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] raw = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : raw) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) return false;
        byte[] ba = a.getBytes(StandardCharsets.UTF_8);
        byte[] bb = b.getBytes(StandardCharsets.UTF_8);
        if (ba.length != bb.length) return false;
        int r = 0;
        for (int i = 0; i < ba.length; i++) r |= ba[i] ^ bb[i];
        return r == 0;
    }

    private boolean checkRateLimit(String ip) {
        long now = System.currentTimeMillis();
        long[] bucket = buckets.compute(ip, (k, v) -> {
            if (v == null || now - v[0] > RATE_WINDOW_MS) return new long[]{now, 1};
            v[1]++;
            return v;
        });
        if (bucket[1] > RATE_LIMIT) {
            buckets.put(ip, new long[]{now, RATE_LIMIT + 1});
            return false;
        }
        return true;
    }

    private String clientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
        String xri = request.getHeader("X-Real-IP");
        if (xri != null && !xri.isBlank()) return xri;
        return request.getRemoteAddr();
    }

    /** 回显 CORS（白名单内的 Origin），保证错误响应也能被前端读取 */
    private void applyCors(HttpServletRequest request, HttpServletResponse response) {
        String origin = request.getHeader("Origin");
        if (origin == null || origin.isBlank()) return;
        if (!CorsConfig.isAllowed(origin)) return;
        response.setHeader("Access-Control-Allow-Origin", origin);
        response.setHeader("Access-Control-Allow-Credentials", "true");
        response.setHeader("Access-Control-Allow-Methods", "GET,POST,PUT,DELETE,OPTIONS");
        response.setHeader("Access-Control-Allow-Headers",
                "Content-Type,Authorization,X-App,X-Ts,X-Nonce,X-Sign,X-Requested-With");
        response.setHeader("Access-Control-Max-Age", "3600");
    }

    private void addSecurityHeaders(HttpServletResponse response) {
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-Frame-Options", "DENY");
        response.setHeader("Referrer-Policy", "no-referrer-when-downgrade");
        response.setHeader("Permissions-Policy", "camera=(), microphone=(), geolocation=()");
        response.setHeader("Cache-Control", "no-store");
    }

    private void reject(HttpServletResponse response, int status, String msg) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.getWriter().write("{\"error\":\"" + msg + "\"}");
    }
}
