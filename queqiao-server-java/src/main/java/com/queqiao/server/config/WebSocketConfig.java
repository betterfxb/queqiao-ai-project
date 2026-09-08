package com.queqiao.server.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * WebSocket / STOMP 实时通道。
 *
 * <p>端点：</p>
 * <ul>
 *   <li>GET /ws（带 SockJS 回退）—— 浏览器管理端使用；</li>
 *   <li>GET /ws/stomp（原生 STOMP over WebSocket）—— 微信小程序 / 原生客户端使用（小程序不支持 SockJS）。</li>
 * </ul>
 * <ul>
 *   <li>/topic/conversation/{id}  新消息实时推送（发送方落库后由后端广播）</li>
 *   <li>/topic/presence           在线状态变化广播（红娘上线/下线）</li>
 *   <li>/user/queue/notice        点对点通知（预留：系统公告、催办）</li>
 * </ul>
 * 鉴权：STOMP CONNECT 必须携带 Authorization: Bearer &lt;JWT&gt;，由 AuthChannelInterceptor 校验，
 * 未携带或失效直接拒绝连接（不依赖 HTTP 层签名）。
 *
 * <p>注意：本配置类刻意不依赖 PresenceService，避免与 {@code SimpMessagingTemplate}
 * （由消息代理基础设施创建）形成循环依赖。握手拦截器仅解析 token，独立成 bean。</p>
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final AuthChannelInterceptor authInterceptor;

    public WebSocketConfig(AuthChannelInterceptor authInterceptor) {
        this.authInterceptor = authInterceptor;
    }

    /** 握手拦截器：把 URL 上的 ?token= 或 Authorization 头暂存到会话属性，供 CONNECT 校验读取 */
    @Bean
    public HandshakeInterceptor tokenHandshakeInterceptor() {
        return new HandshakeInterceptor() {
            @Override
            public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                            WebSocketHandler wsHandler, Map<String, Object> attributes) {
                String q = request.getURI().getQuery();
                if (q != null) {
                    for (String pair : q.split("&")) {
                        int idx = pair.indexOf('=');
                        if (idx > 0 && "token".equals(pair.substring(0, idx))) {
                            attributes.put("token", pair.substring(idx + 1));
                        }
                    }
                }
                String auth = request.getHeaders().getFirst("Authorization");
                if (auth != null && !auth.isBlank()) attributes.put("token", auth);
                return true;
            }

            @Override
            public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                        WebSocketHandler wsHandler, Exception exception) {
                // no-op
            }
        };
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // 浏览器管理端：SockJS 回退（兼容老旧环境）
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns(CorsConfig.allowedOrigins().toArray(String[]::new))
                .withSockJS()
                .setInterceptors(tokenHandshakeInterceptor());

        // 小程序 / 原生客户端：原生 STOMP over WebSocket（小程序不支持 SockJS，必须走这里）
        // 原生 WS 客户端可在握手阶段直接携带 Authorization 头，无需握手拦截器解析 ?token=
        registry.addEndpoint("/ws/stomp")
                .setAllowedOriginPatterns(CorsConfig.allowedOrigins().toArray(String[]::new));
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/user");
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(authInterceptor);
    }
}
