package com.queqiao.server.service;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

/**
 * 会员端实时推送：会员令牌的 name==sub==memberId，故走 Spring 的 /user 个人目的地，
 * 服务端按身份强制路由，会员只能收到自己的消息。
 */
@Service
public class MemberPushService {

    private final SimpMessagingTemplate template;

    public MemberPushService(SimpMessagingTemplate template) {
        this.template = template;
    }

    /** 推送给指定会员：客户端订阅 /user/queue/member */
    public void push(String memberId, Object payload) {
        template.convertAndSendToUser(memberId, "/queue/member", payload);
    }
}
