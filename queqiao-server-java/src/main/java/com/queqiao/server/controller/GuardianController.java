package com.queqiao.server.controller;

import com.queqiao.server.common.BizException;
import com.queqiao.server.common.Ids;
import com.queqiao.server.entity.Conversation;
import com.queqiao.server.entity.Member;
import com.queqiao.server.entity.MemberGuardian;
import com.queqiao.server.entity.MessageItem;
import com.queqiao.server.repo.ConversationRepository;
import com.queqiao.server.repo.MemberRepository;
import com.queqiao.server.repo.MessageItemRepository;
import com.queqiao.server.security.RequestContext;
import com.queqiao.server.service.GuardianService;
import com.queqiao.server.service.LogService;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 家长参与（2-C）：会员端 /api/member/guardian
 *  - POST              子女绑定家长（自己操作）
 *  - GET               子女查看自己绑定的家长
 *  - POST /{id}/authorize  子女授权
 *  - POST /{id}/revoke     子女撤销
 *  - GET  /conversations   家长视角：查看已授权子女的会话
 *  - POST /conversations/{convId}/messages  家长代沟通（仅限本人已授权子女）
 */
@RestController
@RequestMapping("/api/member/guardian")
public class GuardianController {

    private final GuardianService svc;
    private final MemberRepository members;
    private final ConversationRepository convRepo;
    private final MessageItemRepository msgRepo;
    private final LogService logs;

    public GuardianController(GuardianService svc, MemberRepository members,
                              ConversationRepository convRepo, MessageItemRepository msgRepo,
                              LogService logs) {
        this.svc = svc;
        this.members = members;
        this.convRepo = convRepo;
        this.msgRepo = msgRepo;
        this.logs = logs;
    }

    private String me() {
        RequestContext.requireMember();
        return RequestContext.currentMemberId();
    }

    private String myPhone(String memberId) {
        return members.findById(memberId).map(Member::getPhone).orElse(null);
    }

    @PostMapping
    public MemberGuardian bind(@RequestBody Map<String, String> body) {
        String memberId = me();
        return svc.bind(memberId, body.get("guardianPhone"), body.get("guardianName"), body.get("relation"));
    }

    @GetMapping
    public List<MemberGuardian> list() {
        return svc.list(me());
    }

    @PostMapping("/{id}/authorize")
    public MemberGuardian authorize(@PathVariable String id) {
        return svc.authorize(me(), id);
    }

    @PostMapping("/{id}/revoke")
    public MemberGuardian revoke(@PathVariable String id) {
        return svc.revoke(me(), id);
    }

    /** 家长视角：列出我已授权代管的子女及其会话（仅 read，权限严格收敛） */
    @GetMapping("/conversations")
    public List<Map<String, Object>> guardianConversations() {
        String phone = myPhone(me());
        List<String> children = svc.visibleChildren(phone);
        List<Map<String, Object>> res = new ArrayList<>();
        for (String childId : children) {
            Member child = members.findById(childId).orElse(null);
            if (child == null) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("memberId", childId);
            m.put("memberName", child.getName());
            m.put("conversations", convRepo.findByMemberIdOrderByLastAtDesc(childId).stream()
                    .map(MessageController::toVo).collect(Collectors.toList()));
            res.add(m);
        }
        return res;
    }

    /** 家长代沟通：向已授权子女的会话发送消息（sender=guardian，便于前端区分） */
    @PostMapping("/conversations/{convId}/messages")
    public Map<String, Object> guardianSend(@PathVariable String convId, @RequestBody Map<String, String> body) {
        String phone = myPhone(me());
        List<String> children = svc.visibleChildren(phone);
        Conversation c = convRepo.findById(convId).orElseThrow(() -> BizException.notFound("会话不存在：" + convId));
        if (c.getMemberId() == null || !children.contains(c.getMemberId())) {
            logs.record("家长越权访问拦截", convId, "尝试访问非代管子女会话");
            throw BizException.forbidden("无权访问该会话");
        }
        String content = body.get("content");
        if (content == null || content.isBlank()) throw new BizException("消息内容不能为空");
        if (content.length() > 2000) throw new BizException("单条消息不能超过 2000 字");

        MessageItem msg = new MessageItem();
        msg.setConversationId(convId);
        msg.setSender("guardian");
        msg.setContent(content);
        msg.setType("text");
        msg.setReadFlag(true);
        msg.setCreatedAt(LocalDateTime.now());
        msgRepo.save(msg);

        c.setPreview(content.length() > 30 ? content.substring(0, 30) + "…" : content);
        c.setLastAt(LocalDateTime.now());
        c.setUnread(0);
        convRepo.save(c);
        logs.record("家长代沟通", convId, c.getName() + "：" + c.getPreview());
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("ok", true);
        r.put("message", msg);
        return r;
    }
}
