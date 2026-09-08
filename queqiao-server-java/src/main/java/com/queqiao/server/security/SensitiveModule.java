package com.queqiao.server.security;

import com.fasterxml.jackson.databind.module.SimpleModule;
import org.springframework.stereotype.Component;

/**
 * 注册脱敏修饰器。Spring Boot 会自动发现并应用所有 {@code Module} Bean。
 */
@Component
public class SensitiveModule extends SimpleModule {

    public SensitiveModule() {
        setSerializerModifier(new SensitiveModifier());
    }
}
