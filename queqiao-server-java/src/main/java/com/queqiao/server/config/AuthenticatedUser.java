package com.queqiao.server.config;

import java.security.Principal;

/**
 * STOMP 会话身份（来自 JWT）。name=登录名，role=ADMIN/AGENT/MANAGER。
 */
public class AuthenticatedUser implements Principal {
    private final String name;
    private final String userId;
    private final String role;
    private final String agentName;

    public AuthenticatedUser(String userId, String name, String role, String agentName) {
        this.userId = userId;
        this.name = name;
        this.role = role;
        this.agentName = agentName;
    }

    @Override
    public String getName() {
        return name;
    }

    public String getUserId() {
        return userId;
    }

    public String getRole() {
        return role;
    }

    /** 该账号对应的红娘姓名（仅 AGENT 有值），用于在线状态展示 */
    public String getAgentName() {
        return agentName;
    }

    public boolean isAgent() {
        return "AGENT".equals(role);
    }
}
