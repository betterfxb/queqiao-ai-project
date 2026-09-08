package com.queqiao.server.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记实体字段为敏感信息，序列化时按查看者角色与数据范围自动脱敏。
 * 配合 {@link SensitiveSerializer}（通过 {@link SensitiveModule} 注册）生效。
 */
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface Sensitive {
    SensitiveType value() default SensitiveType.PHONE;

    SensitiveScope scope() default SensitiveScope.STAFF;
}
