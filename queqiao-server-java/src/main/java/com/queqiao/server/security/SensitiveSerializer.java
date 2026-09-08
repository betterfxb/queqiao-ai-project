package com.queqiao.server.security;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.ContextualSerializer;

import java.io.IOException;

/**
 * 敏感字段序列化器：根据当前查看者角色与字段归属范围决定脱敏。
 * 作为 {@link ContextualSerializer}，由 Jackson 在构建属性序列化器时读取
 * {@link Sensitive} 注解并生成独立实例（避免共享可变状态）。
 */
public class SensitiveSerializer extends JsonSerializer<String> implements ContextualSerializer {

    private final SensitiveType type;
    private final SensitiveScope scope;

    public SensitiveSerializer() {
        this(SensitiveType.PHONE, SensitiveScope.STAFF);
    }

    public SensitiveSerializer(SensitiveType type, SensitiveScope scope) {
        this.type = type;
        this.scope = scope;
    }

    @Override
    public JsonSerializer<?> createContextual(SerializerProvider prov, BeanProperty property) {
        Sensitive ann = property == null ? null : property.getAnnotation(Sensitive.class);
        if (ann == null) return this;
        return new SensitiveSerializer(ann.value(), ann.scope());
    }

    @Override
    public void serialize(String value, JsonGenerator gen, SerializerProvider provider) throws IOException {
        if (value == null) {
            gen.writeNull();
            return;
        }
        boolean full = false;
        RequestContext.Ctx ctx = RequestContext.get();
        if (ctx != null) {
            if ("ADMIN".equals(ctx.role())) {
                full = true;
            } else if ("AGENT".equals(ctx.role()) && scope == SensitiveScope.MEMBER) {
                // 数据范围已保证红娘只能取到本人负责会员，此处原文可见
                full = true;
            } else if ("MEMBER".equals(ctx.role())) {
                // 会员端只能查到本人资料（controller 已限定 memberId == 自己），本人可见原文
                full = true;
            }
        }
        gen.writeString(Masker.mask(type, value, full));
    }
}
