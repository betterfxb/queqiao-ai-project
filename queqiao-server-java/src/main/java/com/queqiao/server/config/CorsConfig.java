package com.queqiao.server.config;

import com.queqiao.server.service.TrialLimitService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * CORS 配置（严格白名单）+ 安全过滤器注册。
 *
 * <p>白名单由 {@code queqiao.cors.allowed-origins} 提供（逗号分隔），生产通过环境变量
 * {@code QQ_CORS_ORIGINS} 覆盖为真实域名。不再对 {@code http://localhost:*} 做通配放行，
 * 避免任意本地端口的恶意页面携带 Cookie 跨域访问。</p>
 */
@Configuration
public class CorsConfig {

    private static final Logger log = LoggerFactory.getLogger(CorsConfig.class);

    /** 运行期生效的白名单（由配置注入，SecurityFilter 静态读取） */
    private static volatile Set<String> allowedOrigins = Set.of();

    public static boolean isAllowed(String origin) {
        return origin != null && allowedOrigins.contains(origin);
    }

    public static Set<String> allowedOrigins() {
        return allowedOrigins;
    }

    @Value("${queqiao.cors.allowed-origins:}")
    private String allowedOriginsProp;

    @Value("${queqiao.sign-secret:queqiao@2026#sec}")
    private String signSecret;

    @Value("${queqiao.sign-required:false}")
    private boolean signRequired;

    @Value("${queqiao.auth-required:true}")
    private boolean authRequired;

    @Value("${spring.h2.console.enabled:false}")
    private boolean h2ConsoleEnabled;

    @PostConstruct
    void initOrigins() {
        Set<String> set = new LinkedHashSet<>();
        Arrays.stream(allowedOriginsProp.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .forEach(set::add);
        allowedOrigins = Set.copyOf(set);
        log.info("CORS 白名单：{}（签名强制={}，认证强制={}，H2 控制台={}）",
                allowedOrigins, signRequired, authRequired, h2ConsoleEnabled);
    }

    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                if (allowedOrigins.isEmpty()) return;
                registry.addMapping("/api/**")
                        .allowedOrigins(allowedOrigins.toArray(String[]::new))
                        .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                        .allowedHeaders("Content-Type", "Authorization",
                                "X-App", "X-Ts", "X-Nonce", "X-Sign", "X-Requested-With")
                        .allowCredentials(true)
                        .maxAge(3600);
            }
        };
    }

    /** 注册安全过滤器（反爬 / 限流 / 签名校验 / 认证 / 注入扫描），最高优先级 */
    @Bean
    public FilterRegistrationBean<SecurityFilter> securityFilter(
            JwtUtil jwtUtil, ObjectProvider<TrialLimitService> trialProvider) {
        FilterRegistrationBean<SecurityFilter> bean = new FilterRegistrationBean<>(
                new SecurityFilter(jwtUtil, signSecret, signRequired, authRequired,
                        h2ConsoleEnabled, trialProvider));
        bean.addUrlPatterns("/*");
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return bean;
    }

    /** 让嵌入式 Tomcat 接受未编码的中文查询参数，方便直接调试 */
    @Bean
    public TomcatServletWebServerFactory tomcatFactory() {
        TomcatServletWebServerFactory factory = new TomcatServletWebServerFactory();
        factory.addConnectorCustomizers(connector -> connector.setURIEncoding("UTF-8"));
        return factory;
    }
}
