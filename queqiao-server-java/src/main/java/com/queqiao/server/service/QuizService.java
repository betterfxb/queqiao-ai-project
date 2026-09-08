package com.queqiao.server.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.queqiao.server.common.BizException;
import com.queqiao.server.common.Ids;
import com.queqiao.server.entity.Member;
import com.queqiao.server.entity.MemberQuiz;
import com.queqiao.server.entity.QuizTemplate;
import com.queqiao.server.repo.MemberQuizRepository;
import com.queqiao.server.repo.MemberRepository;
import com.queqiao.server.repo.QuizTemplateRepository;
import com.queqiao.server.security.RequestContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 情感 / 性格测评（1-B）。
 *
 * 设计要点：
 *  - 模板（题目 / 选项 / 维度权重）存于 {@link QuizTemplate}，题面与维度以 JSON 文本列存储；
 *  - 会员作答落 {@link MemberQuiz}，算分得到各维度 pole（正/负极标签），合并写入会员 {@code tags}；
 *  - 契合标签供匹配算法在推荐解释中引用，提升匹配深度；
 *  - 完成测评经 {@link PointsService} 一次性发放 QUIZ_COMPLETE 积分（复用 1-A 积分体系）。
 */
@Service
public class QuizService {

    private final ObjectMapper json = new ObjectMapper();
    private final QuizTemplateRepository templates;
    private final MemberQuizRepository quizzes;
    private final MemberRepository members;
    private final PointsService points;
    private final LogService logs;

    public QuizService(QuizTemplateRepository templates, MemberQuizRepository quizzes,
                       MemberRepository members, PointsService points, LogService logs) {
        this.templates = templates;
        this.quizzes = quizzes;
        this.members = members;
        this.points = points;
        this.logs = logs;
    }

    /** 模板列表（含解析后的维度与题目） */
    public List<Map<String, Object>> templates() {
        return templates.findAll().stream().map(this::toTemplateView).collect(Collectors.toList());
    }

    public Map<String, Object> template(String id) {
        QuizTemplate t = templates.findById(id).orElseThrow(() -> BizException.notFound("测评模板"));
        return toTemplateView(t);
    }

    /** 提交测评：算分 → 产出契合标签 → 写入会员 tags → 发放积分 → 留痕 */
    @Transactional
    public Map<String, Object> submit(String memberId, String templateId, Map<String, String> answers) {
        Member m = members.findById(memberId).orElseThrow(() -> BizException.notFound("会员"));
        QuizTemplate t = templates.findById(templateId).orElseThrow(() -> BizException.notFound("测评模板"));
        if (answers == null || answers.isEmpty()) throw new BizException("请完成测评题目");

        List<Map<String, Object>> dims = parseList(t.getDimensions());
        List<Map<String, Object>> questions = parseList(t.getQuestions());

        // 逐题校验作答完整性
        Map<String, String> ans = new LinkedHashMap<>();
        for (Map<String, Object> q : questions) {
            String qid = String.valueOf(q.get("id"));
            String chosen = answers.get(qid);
            if (chosen == null || chosen.isBlank()) throw new BizException("题目「" + q.get("text") + "」未完成");
            ans.put(qid, chosen);
        }

        // 各维度累计得分
        Map<String, Integer> scores = new LinkedHashMap<>();
        for (Map<String, Object> d : dims) scores.put(String.valueOf(d.get("key")), 0);
        for (Map<String, Object> q : questions) {
            String qid = String.valueOf(q.get("id"));
            String chosen = ans.get(qid);
            List<Map<String, Object>> opts = (List<Map<String, Object>>) q.get("options");
            for (Map<String, Object> o : opts) {
                if (chosen.equals(String.valueOf(o.get("key")))) {
                    String dim = String.valueOf(o.get("dimension"));
                    scores.put(dim, scores.getOrDefault(dim, 0) + toInt(o.get("score")));
                    break;
                }
            }
        }

        // 维度 → 两极标签（得分 >0 取正极，否则取负极）
        List<String> tags = new ArrayList<>();
        List<Map<String, Object>> scoreView = new ArrayList<>();
        for (Map<String, Object> d : dims) {
            String key = String.valueOf(d.get("key"));
            int s = scores.getOrDefault(key, 0);
            String tag = s > 0 ? String.valueOf(d.get("positive")) : String.valueOf(d.get("negative"));
            tags.add(tag);
            Map<String, Object> sv = new LinkedHashMap<>();
            sv.put("key", key);
            sv.put("name", d.get("name"));
            sv.put("score", s);
            sv.put("tag", tag);
            scoreView.add(sv);
        }

        String summary = buildSummary(t.getTitle(), tags);

        MemberQuiz mq = new MemberQuiz();
        mq.setId(Ids.biz("MQ"));
        mq.setMemberId(memberId);
        mq.setMemberName(m.getName());
        mq.setTemplateId(templateId);
        mq.setTemplateTitle(t.getTitle());
        try {
            mq.setAnswers(json.writeValueAsString(ans));
            mq.setScores(json.writeValueAsString(scores));
        } catch (Exception e) {
            throw new BizException("作答记录序列化失败");
        }
        mq.setTags(String.join(",", tags));
        mq.setSummary(summary);
        mq.setCreatedAt(LocalDateTime.now());
        quizzes.save(mq);

        // 契合标签增量合并写入会员档案
        mergeTags(m, tags);
        members.save(m);

        // 完成测评一次性积分（复用 1-A）
        points.earn(memberId, "QUIZ_COMPLETE", mq.getId(), logs.currentUserName(), "完成情感性格测评");

        logs.record("会员测评", memberId, m.getName() + " 完成「" + t.getTitle() + "」→ " + String.join("/", tags));

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("quizId", mq.getId());
        res.put("templateId", templateId);
        res.put("templateTitle", t.getTitle());
        res.put("scores", scoreView);
        res.put("tags", tags);
        res.put("summary", summary);
        res.put("memberTags", splitTags(m.getTags()));
        return res;
    }

    /** 会员最新一次测评结果 */
    public Map<String, Object> history(String memberId) {
        members.findById(memberId).orElseThrow(() -> BizException.notFound("会员"));
        Optional<MemberQuiz> latest = quizzes.findTopByMemberIdOrderByCreatedAtDesc(memberId);
        Map<String, Object> res = new LinkedHashMap<>();
        if (latest.isEmpty()) {
            res.put("done", false);
            return res;
        }
        MemberQuiz q = latest.get();
        res.put("done", true);
        res.put("quizId", q.getId());
        res.put("templateId", q.getTemplateId());
        res.put("templateTitle", q.getTemplateTitle());
        res.put("tags", splitTags(q.getTags()));
        res.put("summary", q.getSummary());
        res.put("createdAt", q.getCreatedAt());
        try {
            res.put("scores", json.readValue(q.getScores() == null ? "{}" : q.getScores(),
                    new TypeReference<List<Map<String, Object>>>() {}));
        } catch (Exception e) {
            res.put("scores", List.of());
        }
        return res;
    }

    // —— 内部辅助 ——

    private Map<String, Object> toTemplateView(QuizTemplate t) {
        List<Map<String, Object>> questions = parseList(t.getQuestions());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", t.getId());
        m.put("title", t.getTitle());
        m.put("description", t.getDescription());
        m.put("dimensions", parseList(t.getDimensions()));
        m.put("questions", questions);
        m.put("questionCount", questions.size());
        m.put("createdAt", t.getCreatedAt());
        return m;
    }

    private List<Map<String, Object>> parseList(String jsonStr) {
        if (jsonStr == null || jsonStr.isBlank()) return List.of();
        try {
            return json.readValue(jsonStr, new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private int toInt(Object o) {
        if (o == null) return 0;
        if (o instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(String.valueOf(o));
        } catch (Exception e) {
            return 0;
        }
    }

    private String buildSummary(String title, List<String> tags) {
        return "「" + title + "」测评结论：" + String.join(" / ", tags)
                + "。建议在牵线推荐时优先匹配同频对象，提升契合度与成交率。";
    }

    private void mergeTags(Member m, List<String> tags) {
        Set<String> set = new LinkedHashSet<>(splitTags(m.getTags()));
        set.addAll(tags);
        m.setTags(String.join(",", set));
    }

    private List<String> splitTags(String tags) {
        if (tags == null || tags.isBlank()) return List.of();
        return Arrays.stream(tags.split(",")).map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toList());
    }
}
