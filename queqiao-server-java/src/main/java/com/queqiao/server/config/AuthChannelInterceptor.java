package com.queqiao.server.config;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * STOMP 入站鉴权拦截器。
 *
 * <p>在 CONNECT 阶段校验 JWT（来自 STOMP 原生头 Authorization，或握手时写入会话属性的 token）。
 * 校验失败直接抛异常，Spring 会回送 ERROR 帧并断开连接，避免匿名连接占用通道。</p>
 */
@Component
public class AuthChannelInterceptor implements ChannelInterceptor {

    private final JwtUtil jwtUtil;

    public AuthChannelInterceptor(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) return message;

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            String token = resolveToken(accessor);
            Map<String, Object> claims = (token == null) ? null : jwtUtil.verify(stripBearer(token));
            if (claims == null) {
                throw new IllegalArgumentException("WebSocket 未授权：缺少或无效的令牌");
            }
            String userId = str(claims.get("sub"));
            String username = str(claims.get("name"));
            String role = str(claims.get("role"));
            String agentName = "AGENT".equals(role) ? username : null;
            accessor.setUser(new AuthenticatedUser(userId, username, role, agentName));
        }
        return message;
    }

    @SuppressWarnings("unchecked")
    private String resolveToken(StompHeaderAccessor accessor) {
        // STOMP CONNECT 帧头携带的令牌（SockJS 与原生 WS 均生效：Spring 把 STOMP 帧头存入 nativeHeaders）
        String auth = accessor.getFirstNativeHeader("Authorization");
        if (auth != null && !auth.isBlank()) return auth;
        // 握手阶段可能把 token 放进会话属性（SockJS 升级无法自定义头时走这里）
        Object attr = accessor.getSessionAttributes() == null ? null
                : accessor.getSessionAttributes().get("token");
        return attr == null ? null : String.valueOf(attr);
    }

    private static String stripBearer(String token) {
        return token.startsWith("Bearer ") ? token.substring(7).trim() : token.trim();
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }
}
