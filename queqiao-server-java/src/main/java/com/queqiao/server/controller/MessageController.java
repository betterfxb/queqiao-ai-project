package com.queqiao.server.controller;

import com.queqiao.server.ai.AiClient;
import com.queqiao.server.common.BizException;
import com.queqiao.server.common.Ids;
import com.queqiao.server.entity.Conversation;
import com.queqiao.server.entity.Member;
import com.queqiao.server.entity.MessageItem;
import com.queqiao.server.repo.ConversationRepository;
import com.queqiao.server.repo.MemberRepository;
import com.queqiao.server.repo.MessageItemRepository;
import com.queqiao.server.security.RequestContext;
import com.queqiao.server.service.LogService;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 消息中心。
 * 前端契约：GET /api/conversations 返回 Conversation[]。
 */
@RestController
@RequestMapping("/api")
public class MessageController {

    private static final Logger log = LoggerFactory.getLogger(MessageController.class);

    private final ConversationRepository convRepo;
    private final MessageItemRepository msgRepo;
    private final MemberRepository memberRepo;
    private final LogService logService;
    private final AiClient ai;
    private final SimpMessagingTemplate broker;

    public MessageController(ConversationRepository convRepo, MessageItemRepository msgRepo,
                             MemberRepository memberRepo, LogService logService, AiClient ai,
                             SimpMessagingTemplate broker) {
        this.convRepo = convRepo;
        this.msgRepo = msgRepo;
        this.memberRepo = memberRepo;
        this.logService = logService;
        this.ai = ai;
        this.broker = broker;
    }

    /** 会话列表（kind=member|system 过滤，keyword 搜索） */
    @GetMapping("/conversations")
    public List<Map<String, Object>> conversations(@RequestParam(required = false) String kind,
                                                   @RequestParam(required = false) String keyword) {
        String me = RequestContext.isAgent() ? RequestContext.agentName() : null;
        return convRepo.findByOrderByLastAtDesc().stream()
                // 数据范围：红娘只看本人负责的会话（系统通知对所有人可见）
                .filter(c -> me == null || "system".equals(c.getKind()) || me.equals(c.getAgent()))
                .filter(c -> kind == null || kind.isBlank() || "all".equals(kind) || kind.equals(c.getKind()))
                .filter(c -> keyword == null || keyword.isBlank()
                        || (c.getName() != null && c.getName().contains(keyword))
                        || (c.getPreview() != null && c.getPreview().contains(keyword)))
                .map(MessageController::toVo)
                .collect(Collectors.toList());
    }

    /** 未读汇总（侧边栏红点） */
    @GetMapping("/conversations/unread")
    public Map<String, Object> unread() {
        List<Conversation> all = convRepo.findAll();
        int total = all.stream().mapToInt(c -> c.getUnread() == null ? 0 : c.getUnread()).sum();
        int member = all.stream().filter(c -> "member".equals(c.getKind()))
                .mapToInt(c -> c.getUnread() == null ? 0 : c.getUnread()).sum();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("total", total);
        m.put("member", member);
        m.put("system", total - member);
        m.put("conversations", all.size());
        return m;
    }

    /** 会话详情 + 消息记录（打开即已读） */
    @GetMapping("/conversations/{id}")
    public Map<String, Object> detail(@PathVariable String id) {
        Conversation c = convRepo.findById(id).orElseThrow(() -> BizException.notFound("会话不存在：" + id));
        assertOwned(c);
        List<MessageItem> msgs = msgRepo.findByConversationIdOrderByCreatedAtAsc(id);

        // 打开会话即清零未读
        if (c.getUnread() != null && c.getUnread() > 0) {
            c.setUnread(0);
            convRepo.save(c);
            msgs.stream().filter(m -> !Boolean.TRUE.equals(m.getReadFlag())).forEach(m -> {
                m.setReadFlag(true);
                msgRepo.save(m);
            });
        }

        Map<String, Object> vo = toVo(c);
        vo.put("memberId", c.getMemberId());
        vo.put("agent", c.getAgent());
        vo.put("messages", msgs);
        if (c.getMemberId() != null) {
            memberRepo.findById(c.getMemberId()).ifPresent(m -> vo.put("member", m));
        }
        return vo;
    }

    /** 消息记录 */
    @GetMapping("/conversations/{id}/messages")
    public List<MessageItem> messages(@PathVariable String id) {
        assertOwned(convRepo.findById(id).orElseThrow(() -> BizException.notFound("会话不存在：" + id)));
        return msgRepo.findByConversationIdOrderByCreatedAtAsc(id);
    }

    /**
     * 发送消息（前置内容风控）。
     *
     * <p>两层防线：</p>
     * <ol>
     *   <li><b>硬拦截</b>：命中高危（诈骗/色情/违法）直接 403，内容不落库并记审计；</li>
     *   <li><b>二次确认</b>：命中中低风险（联系方式外泄/引流/辱骂/过度承诺）返回
     *       {@code {ok:false, blocked:true, moderation:{...}}}，前端提示后可带
     *       {@code confirmed=true} 重发，确认动作同样进审计日志。</li>
     * </ol>
     * AI 不可用时自动降级到本地最小规则集，绝不因风控不可用而放弃校验。
     */
    @PostMapping("/conversations/{id}/messages")
    public Map<String, Object> send(@PathVariable String id, @RequestBody Map<String, Object> body) {
        Conversation c = convRepo.findById(id).orElseThrow(() -> BizException.notFound("会话不存在：" + id));
        assertOwned(c);
        String content = str(body.get("content"));
        if (content.isBlank()) throw new BizException("消息内容不能为空");
        if (content.length() > 2000) throw new BizException("单条消息不能超过 2000 字");
        boolean confirmed = truthy(body.get("confirmed"));

        Map<String, Object> mod = ai.moderate(Map.of(
                "text", content, "scene", "message",
                "role", RequestContext.get() == null ? "AGENT" : n(RequestContext.get().role())));
        boolean degraded = mod == null;
        if (degraded) mod = AiController.localModerate(content);
        mod.put("source", degraded ? "local-fallback" : "ai");

        String action = String.valueOf(mod.getOrDefault("action", "pass"));
        if ("block".equals(action)) {
            logService.record("内容风控拦截", id, c.getName() + "：" + hitSummary(mod));
            throw BizException.forbidden("内容风控拦截 · " + hitSummary(mod));
        }
        if ("warn".equals(action) && !confirmed) {
            return Map.of("ok", false, "blocked", true, "moderation", mod);
        }

        MessageItem m = new MessageItem();
        m.setConversationId(id);
        m.setSender("agent");
        m.setContent(content);
        m.setType(str(body.getOrDefault("type", "text")));
        m.setReadFlag(true);
        m.setCreatedAt(LocalDateTime.now());
        msgRepo.save(m);

        c.setPreview(content.length() > 30 ? content.substring(0, 30) + "…" : content);
        c.setLastAt(LocalDateTime.now());
        c.setUnread(0);
        convRepo.save(c);

        if ("warn".equals(action)) {
            logService.record("风险内容确认发送", id, c.getName() + "：" + hitSummary(mod));
        } else {
            logService.record("发送消息", id, c.getName() + "：" + c.getPreview());
        }

        // 实时推送：订阅该会话的人（红娘/前端）即时收到新消息
        Map<String, Object> push = new LinkedHashMap<>();
        push.put("type", "message");
        push.put("conversationId", id);
        push.put("sender", "agent");
        push.put("message", m);
        push.put("moderation", mod);
        try {
            broker.convertAndSend("/topic/conversation/" + id, push);
        } catch (Exception e) {
            log.warn("[ws] 消息推送失败: {}", e.getMessage());
        }
        return Map.of("ok", true, "message", m, "moderation", mod);
    }

    @SuppressWarnings("unchecked")
    private static String hitSummary(Map<String, Object> mod) {
        Object hits = mod.get("hits");
        if (hits instanceof List<?> l && !l.isEmpty()) {
            return l.stream().limit(3)
                    .map(h -> h instanceof Map<?, ?> mm ? String.valueOf(mm.get("label")) : String.valueOf(h))
                    .distinct().reduce((a, b) -> a + "、" + b).orElse("命中风险规则");
        }
        return "命中风险规则";
    }

    private static boolean truthy(Object v) {
        return v instanceof Boolean b ? b : "true".equalsIgnoreCase(String.valueOf(v));
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    /** 数据范围：红娘只能操作本人负责会员的会话 */
    private void assertOwned(Conversation c) {
        if (!RequestContext.isAgent()) return;
        String me = RequestContext.agentName();
        if (me != null && c.getAgent() != null && !me.equals(c.getAgent())) {
            logService.record("越权访问拦截", c.getId(), "尝试访问 " + n(c.getAgent()) + " 的会话");
            throw BizException.forbidden("无权访问该会话");
        }
    }

    /** 标记已读 */
    @PostMapping("/conversations/{id}/read")
    public Map<String, Object> read(@PathVariable String id) {
        Conversation c = convRepo.findById(id).orElseThrow(() -> BizException.notFound("会话不存在：" + id));
        assertOwned(c);
        c.setUnread(0);
        convRepo.save(c);
        msgRepo.findByConversationIdOrderByCreatedAtAsc(id).forEach(m -> {
            if (!Boolean.TRUE.equals(m.getReadFlag())) {
                m.setReadFlag(true);
                msgRepo.save(m);
            }
        });
        return Map.of("ok", true);
    }

    /** 新建会话（从会员详情发起聊天） */
    @PostMapping("/conversations")
    public Map<String, Object> create(@RequestBody Map<String, String> body) {
        String memberId = body.get("memberId");
        if (memberId == null || memberId.isBlank()) throw new IllegalArgumentException("memberId 不能为空");
        Optional<Conversation> exist = convRepo.findFirstByMemberIdOrderByCreatedAtAsc(memberId);
        if (exist.isPresent()) {
            assertOwned(exist.get());
            return toVo(exist.get());
        }

        Member mem = memberRepo.findById(memberId).orElseThrow(() -> BizException.notFound("会员不存在：" + memberId));
        // IDOR：红娘只能对自己负责的会员发起会话
        if (RequestContext.isAgent() && mem.getAgent() != null
                && !RequestContext.agentName().equals(mem.getAgent())) {
            logService.record("越权访问拦截", memberId, "尝试对 " + n(mem.getAgent()) + " 的会员发起会话");
            throw BizException.forbidden("无权对该会员发起会话");
        }
        Conversation c = new Conversation();
        c.setId(Ids.biz("CV"));
        c.setName(mem.getName());
        c.setMemberId(memberId);
        c.setPreview("");
        c.setUnread(0);
        c.setKind("member");
        c.setOnline(false);
        c.setAgent(mem.getAgent());
        c.setAvatarBg(mem.getAvatarBg());
        c.setLastAt(LocalDateTime.now());
        c.setCreatedAt(LocalDateTime.now());
        convRepo.save(c);
        logService.record("新建会话", c.getId(), mem.getName());
        return toVo(c);
    }

    @DeleteMapping("/conversations/{id}")
    public Map<String, Object> delete(@PathVariable String id) {
        Conversation c = convRepo.findById(id).orElseThrow(() -> BizException.notFound("会话不存在：" + id));
        assertOwned(c);
        msgRepo.deleteAll(msgRepo.findByConversationIdOrderByCreatedAtAsc(id));
        convRepo.deleteById(id);
        logService.record("删除会话", id, c.getName());
        return Map.of("ok", true);
    }

    /** AI 回复建议（基于最近对话上下文，AI 不可用时走本地模板） */
    @PostMapping("/conversations/{id}/reply-suggest")
    public Map<String, Object> replySuggest(@PathVariable String id) {
        Conversation c = convRepo.findById(id).orElseThrow(() -> BizException.notFound("会话不存在：" + id));
        assertOwned(c);
        List<MessageItem> msgs = msgRepo.findByConversationIdOrderByCreatedAtAsc(id);
        List<Map<String, String>> ctx = msgs.stream()
                .skip(Math.max(0, msgs.size() - 8))
                .map(m -> Map.of("sender", n(m.getSender()), "content", n(m.getContent())))
                .toList();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("name", c.getName());
        payload.put("kind", c.getKind());
        payload.put("messages", ctx);

        Map<String, Object> res = ai.replySuggest(payload);
        if (res != null && res.get("suggestions") != null) {
            res.put("source", "ai");
            return res;
        }

        String last = msgs.isEmpty() ? "" : n(msgs.get(msgs.size() - 1).getContent());
        List<String> sug = new ArrayList<>();
        if (last.contains("见面") || last.contains("约")) {
            sug.add("好的，我这边帮您协调时间，周末下午茶或晚餐都可以安排，您更偏好哪种？");
            sug.add("对方也表达了见面意愿，我先约在本周六 15:00，地点定在市中心的安静咖啡馆，您看合适吗？");
        } else if (last.contains("资料") || last.contains("照片")) {
            sug.add("资料我已经整理好，稍后发您。有任何想补充确认的条件随时告诉我。");
            sug.add("这边先发一份脱敏版资料，如果您感兴趣，我再安排双方进一步了解。");
        } else if (last.contains("谢谢") || last.contains("感谢")) {
            sug.add("不客气，这是我应该做的。后续有任何想法都可以直接找我。");
            sug.add("能帮到您就好，我会持续关注匹配进展，有合适人选第一时间同步。");
        } else {
            sug.add("收到，我先了解一下您的具体想法，方便的话简单说说您比较看重的三点条件？");
            sug.add("好的，我记录下来了。这两天我会筛选几位合适的人选发您参考。");
            sug.add("感谢反馈，我会据此调整推荐方向，稍后给您一个更新的方案。");
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("suggestions", sug);
        m.put("source", "local");
        return m;
    }

    static Map<String, Object> toVo(Conversation c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.getId());
        m.put("name", c.getName());
        m.put("timeAgo", ago(c.getLastAt()));
        m.put("preview", c.getPreview());
        m.put("unread", c.getUnread() == null ? 0 : c.getUnread());
        m.put("kind", c.getKind());
        m.put("online", Boolean.TRUE.equals(c.getOnline()));
        m.put("avatarBg", c.getAvatarBg());
        return m;
    }

    static String ago(LocalDateTime t) {
        if (t == null) return "";
        long min = Duration.between(t, LocalDateTime.now()).toMinutes();
        if (min < 1) return "刚刚";
        if (min < 60) return min + "分钟前";
        long h = min / 60;
        if (h < 24) return h + "小时前";
        long d = h / 24;
        return d < 30 ? d + "天前" : (d / 30) + "个月前";
    }

    private static String n(String s) { return s == null ? "" : s; }
}
