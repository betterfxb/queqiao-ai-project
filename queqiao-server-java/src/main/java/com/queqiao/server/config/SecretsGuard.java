package com.queqiao.server.config;

import com.queqiao.server.common.BizException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 密钥外部化校验（#158 E）：strict-secrets=true（生产默认）下，
 * 若仍使用默认占位密钥 / 空数据库密码，则启动失败（fail-fast），避免误投生产。
 *
 * 启用：设置环境变量 QQ_STRICT_SECRETS=true（application-prod 已默认开启）。
 * 覆盖默认密钥：QQ_JWT_SECRET / QQ_SIGN_SECRET / DB_PASSWORD。
 */
@Component
public class SecretsGuard implements ApplicationRunner {

    private final Environment env;

    @Value("${queqiao.jwt-secret:}")
    private String jwtSecret;

    @Value("${queqiao.sign-secret:}")
    private String signSecret;

    @Value("${spring.datasource.password:}")
    private String dbPassword;

    @Value("${queqiao.seed-password:}")
    private String seedPassword;

    @Value("${queqiao.sign-required:false}")
    private boolean signRequired;

    @Value("${spring.h2.console.enabled:false}")
    private boolean h2Console;

    @Value("${queqiao.cors.allowed-origins:}")
    private String corsOrigins;

    private static final String DEFAULT_JWT = "queqiao-jwt-secret-2026-change-in-prod";
    private static final String DEFAULT_SIGN = "queqiao@2026#sec";
    /** 通用占位/弱值黑名单 */
    private static final List<String> PLACEHOLDERS = List.of("change-me-in-prod", "CHANGE_ME", "changeme");
    private static final List<String> WEAK_PASSWORDS = List.of("123456", "12345678", "admin", "password", "000000");

    public SecretsGuard(Environment env) {
        this.env = env;
    }

    @Override
    public void run(ApplicationArguments args) {
        boolean strict = Boolean.parseBoolean(env.getProperty("qq.strict-secrets", "false"));
        boolean prod = List.of(env.getActiveProfiles()).contains("prod");
        if (!strict && !prod) return; // 本地联调默认放行

        List<String> problems = new ArrayList<>();
        if (DEFAULT_JWT.equals(jwtSecret) || isPlaceholder(jwtSecret)) {
            problems.add("queqiao.jwt-secret 仍为默认占位值，请通过环境变量 QQ_JWT_SECRET 覆盖");
        }
        if (DEFAULT_SIGN.equals(signSecret) || isPlaceholder(signSecret)) {
            problems.add("queqiao.sign-secret 仍为默认占位值，请通过环境变量 QQ_SIGN_SECRET 覆盖");
        }
        if (seedPassword != null && WEAK_PASSWORDS.contains(seedPassword)) {
            problems.add("queqiao.seed-password 为弱口令，请通过环境变量 QQ_SEED_PASSWORD 覆盖");
        }
        if (!signRequired) {
            problems.add("queqiao.sign-required=false，生产必须开启请求签名校验");
        }
        if (h2Console) {
            problems.add("spring.h2.console.enabled=true，生产必须关闭 H2 控制台");
        }
        if (corsOrigins == null || corsOrigins.isBlank()) {
            problems.add("queqiao.cors.allowed-origins 为空，请通过环境变量 QQ_CORS_ORIGINS 配置真实域名白名单");
        } else if (corsOrigins.contains("localhost") || corsOrigins.contains("127.0.0.1")) {
            problems.add("queqiao.cors.allowed-origins 含 localhost，生产不允许本地来源跨域");
        }
        // 仅当使用 MySQL（非 H2 文件库）时空库密码视为风险
        String url = env.getProperty("spring.datasource.url", "");
        if (url.contains("mysql") && (dbPassword == null || dbPassword.isBlank() || isPlaceholder(dbPassword))) {
            problems.add("MySQL 生产库密码为空或仍为占位值，请通过环境变量 QUEQIAO_DB_PASSWORD 设置");
        }
        if (!problems.isEmpty()) {
            throw new BizException("密钥校验失败（strict-secrets=true）：\n- " + String.join("\n- ", problems));
        }
    }

    private static boolean isPlaceholder(String v) {
        return v != null && PLACEHOLDERS.stream().anyMatch(p -> p.equalsIgnoreCase(v));
    }
}
