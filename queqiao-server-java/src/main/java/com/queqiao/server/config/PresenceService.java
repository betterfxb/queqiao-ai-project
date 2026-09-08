package com.queqiao.server.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 在线状态服务（红娘实时看板）。
 *
 * <p>红娘通过 WebSocket 接入并完成鉴权后，记录其在线会话；上线/下线均广播到 /topic/presence，
 * 工作台可据此实时刷新「红娘在线看板」。无 AI、无外部依赖，纯内存维护。</p>
 *
 * <p>依赖 SimpMessagingTemplate（由消息代理基础设施创建），不以任何方式反向依赖
 * WebSocketConfig，避免循环引用。</p>
 */
@Service
public class PresenceService {

    private static final Logger log = LoggerFactory.getLogger(PresenceService.class);

    private final SimpMessagingTemplate template;

    /** 红娘名 -> 在线会话数（>0 即在线） */
    private final ConcurrentHashMap<String, Integer> agentOnline = new ConcurrentHashMap<>();
    /** 会话 id -> 红娘名（用于断开时精确清理） */
    private final ConcurrentHashMap<String, String> sessionAgent = new ConcurrentHashMap<>();

    public PresenceService(SimpMessagingTemplate template) {
        this.template = template;
    }

    @EventListener
    public void onConnect(SessionConnectedEvent event) {
        AuthenticatedUser user = asUser(event.getUser());
        if (user == null || !user.isAgent()) return;
        String sid = sessionId(event);
        sessionAgent.put(sid, user.getAgentName());
        int n = agentOnline.merge(user.getAgentName(), 1, Integer::sum);
        broadcast(user.getAgentName(), n > 0, sid);
    }

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        String sid = event.getSessionId();
        String agent = sessionAgent.remove(sid);
        if (agent == null) {
            AuthenticatedUser user = asUser(event.getUser());
            if (user != null) agent = user.getAgentName();
        }
        if (agent == null) return;
        Integer n = agentOnline.computeIfPresent(agent, (k, v) -> Math.max(0, v - 1));
        boolean online = n != null && n > 0;
        if (n == null || n <= 0) agentOnline.remove(agent);
        broadcast(agent, online, sid);
    }

    /** 当前在线红娘快照 */
    public Map<String, Object> snapshot() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("onlineAgents", new ArrayList<>(agentOnline.keySet()));
        m.put("count", agentOnline.size());
        return m;
    }

    private void broadcast(String agent, boolean online, String sid) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("agent", agent);
        m.put("online", online);
        m.put("sessionId", sid);
        m.put("at", System.currentTimeMillis());
        m.put("onlineAgents", new ArrayList<>(agentOnline.keySet()));
        m.put("count", agentOnline.size());
        try {
            template.convertAndSend("/topic/presence", m);
        } catch (Exception e) {
            log.warn("[presence] broadcast failed: {}", e.getMessage());
        }
    }

    private static AuthenticatedUser asUser(java.security.Principal p) {
        return p instanceof AuthenticatedUser u ? u : null;
    }

    private static String sessionId(SessionConnectedEvent event) {
        try {
            return SimpMessageHeaderAccessor.wrap(event.getMessage()).getSessionId();
        } catch (Exception e) {
            return null;
        }
    }
}
