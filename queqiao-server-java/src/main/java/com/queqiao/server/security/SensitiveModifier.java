package com.queqiao.server.security;

import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;
import com.fasterxml.jackson.databind.ser.BeanSerializerModifier;

import java.util.List;

/**
 * 序列化修饰器：扫描所有带 {@link Sensitive} 注解的 String 属性，
 * 用 {@link SensitiveSerializer} 替换默认序列化器，实现"按查看者角色"的自动脱敏。
 *
 * <p>说明：Jackson 在内省时会把同一逻辑属性上 field / getter 的注解合并，
 * 因此实体上 {@code @Sensitive} 标在字段即可生效。</p>
 */
public class SensitiveModifier extends BeanSerializerModifier {

    @Override
    @SuppressWarnings("unchecked")
    public List<BeanPropertyWriter> changeProperties(SerializationConfig config,
                                                     BeanDescription beanDesc,
                                                     List<BeanPropertyWriter> beanProperties) {
        for (BeanPropertyWriter writer : beanProperties) {
            Sensitive ann = writer.getAnnotation(Sensitive.class);
            if (ann == null) {
                continue;
            }
            Class<?> raw = writer.getType() == null ? null : writer.getType().getRawClass();
            if (raw != String.class) {
                // 仅支持字符串型敏感字段，避免误伤数值/对象属性
                continue;
            }
            JsonSerializer<?> ser = new SensitiveSerializer(ann.value(), ann.scope());
            writer.assignSerializer((JsonSerializer<Object>) ser);
        }
        return beanProperties;
    }
}
