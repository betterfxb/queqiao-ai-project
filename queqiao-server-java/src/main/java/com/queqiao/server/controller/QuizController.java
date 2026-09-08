package com.queqiao.server.controller;

import com.queqiao.server.common.BizException;
import com.queqiao.server.entity.Member;
import com.queqiao.server.repo.MemberRepository;
import com.queqiao.server.security.RequestContext;
import com.queqiao.server.service.QuizService;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 情感 / 性格测评接口 */
@RestController
@RequestMapping("/api")
public class QuizController {

    private final QuizService quiz;
    private final MemberRepository members;

    public QuizController(QuizService quiz, MemberRepository members) {
        this.quiz = quiz;
        this.members = members;
    }

    /** 测评模板列表（含题目与选项，供答题页渲染） */
    @GetMapping("/quiz/templates")
    public List<Map<String, Object>> templates() {
        return quiz.templates();
    }

    /** 单个模板详情 */
    @GetMapping("/quiz/templates/{id}")
    public Map<String, Object> template(@PathVariable String id) {
        return quiz.template(id);
    }

    /** 提交会员测评：代会员作答并产出契合标签（按会员归属鉴权） */
    @PostMapping("/members/{id}/quiz")
    @Transactional
    public Map<String, Object> submit(@PathVariable String id, @RequestBody Map<String, Object> body) {
        Member m = members.findById(id).orElseThrow(() -> BizException.notFound("会员"));
        assertOwned(m);
        String templateId = (String) body.get("templateId");
        if (templateId == null || templateId.isBlank()) throw new BizException("缺少 templateId");
        Map<String, String> answers = new LinkedHashMap<>();
        Object raw = body.get("answers");
        if (raw instanceof Map) {
            for (Map.Entry<?, ?> e : ((Map<?, ?>) raw).entrySet()) {
                answers.put(String.valueOf(e.getKey()), e.getValue() == null ? "" : String.valueOf(e.getValue()));
            }
        }
        return quiz.submit(id, templateId, answers);
    }

    /** 会员最新测评结果（无则返回 done=false） */
    @GetMapping("/members/{id}/quiz")
    public Map<String, Object> history(@PathVariable String id) {
        Member m = members.findById(id).orElseThrow(() -> BizException.notFound("会员"));
        assertOwned(m);
        return quiz.history(id);
    }

    /** 红娘只能操作本人负责的会员 */
    private void assertOwned(Member m) {
        String me = RequestContext.isAgent() ? RequestContext.agentName() : null;
        if (me != null && !me.equals(m.getAgent())) {
            throw BizException.forbidden("无权操作该会员");
        }
    }
}
