package com.queqiao.server.service;

import com.queqiao.server.common.BizException;
import com.queqiao.server.common.Ids;
import com.queqiao.server.entity.Member;
import com.queqiao.server.entity.Pair;
import com.queqiao.server.entity.SuccessStory;
import com.queqiao.server.repo.MemberRepository;
import com.queqiao.server.repo.PairRepository;
import com.queqiao.server.repo.SuccessStoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 成功案例营销（#111 5.3.2）。
 *
 * 安全设计：案例落库即「别名化」——只写「王先生 / 李女士」与年龄段，
 * 不存真实姓名与手机号。因此 {@link #publicList()} 对外公开时天然无 PII 泄露，
 * 不依赖运行时脱敏（比依赖序列化脱敏更稳妥）。
 */
@Service
public class StoryService {

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(StoryService.class);

    public static final String DRAFT = "草稿";
    public static final String PUBLISHED = "已发布";
    public static final String OFFLINE = "已下架";

    private final SuccessStoryRepository repo;
    private final PairRepository pairs;
    private final MemberRepository members;
    private final LogService logs;

    public StoryService(SuccessStoryRepository repo, PairRepository pairs,
                        MemberRepository members, LogService logs) {
        this.repo = repo;
        this.pairs = pairs;
        this.members = members;
        this.logs = logs;
    }

    /**
     * 旁路异步生成成功案例草稿（P0-11）：牵线状态更新属于主链路，草稿沉淀失败或变慢都不应影响它。
     * 由调用方在请求线程内取好 author 传入（异步线程无 RequestContext）。
     */
    @org.springframework.scheduling.annotation.Async("aiExecutor")
    public void draftFromPairAsync(String pairId, String author) {
        try {
            draftFromPair(pairId, author);
        } catch (Exception e) {
            LOG.warn("[story] 自动生成成功案例草稿失败 pair={} : {}", pairId, e.getMessage());
        }
    }

    /** 由「已成功」牵线一键生成脱敏草稿；幂等：同一 pairId 已有案例则返回原稿 */
    @Transactional
    public SuccessStory draftFromPair(String pairId, String author) {
        Pair p = pairs.findById(pairId).orElseThrow(() -> BizException.notFound("牵线"));
        if (!"已成功".equals(p.getStatus())) {
            throw new BizException("仅「已成功」的牵线可以生成成功案例");
        }
        return repo.findFirstByPairId(pairId).orElseGet(() -> {
            Member male = p.getMaleId() == null ? null : members.findById(p.getMaleId()).orElse(null);
            Member female = p.getFemaleId() == null ? null : members.findById(p.getFemaleId()).orElse(null);

            String maleAlias = alias(p.getMaleName(), "男");
            String femaleAlias = alias(p.getFemaleName(), "女");
            String city = male != null && male.getCity() != null ? male.getCity()
                    : (female != null ? female.getCity() : null);
            String ageTag = ageBand(male) + (female != null ? " · " + ageBand(female) : "");

            SuccessStory s = new SuccessStory();
            s.setId(Ids.biz("SS"));
            s.setPairId(pairId);
            s.setTitle(maleAlias + " 与 " + femaleAlias + " 的脱单故事");
            s.setMaleAlias(maleAlias);
            s.setFemaleAlias(femaleAlias);
            s.setCityTag(city);
            s.setAgeTag(ageTag);
            s.setContent(buildContent(maleAlias, femaleAlias, city, male, female, p));
            s.setCover("linear-gradient(135deg,#F6D5D5,#D9C4E8)");
            s.setTags(buildTags(city, male, female));
            s.setStatus(DRAFT);
            s.setPublicVisible(false);
            s.setViews(0);
            s.setAuthor(author);
            s.setCreatedAt(LocalDateTime.now());
            s.setUpdatedAt(LocalDateTime.now());
            repo.save(s);
            logs.record("生成成功案例草稿", s.getId(), s.getTitle());
            return s;
        });
    }

    /** 新建 / 编辑（编辑时忽略 status，状态只能通过 publish/offline 变更） */
    @Transactional
    public SuccessStory save(String id, Map<String, Object> body, String author) {
        SuccessStory s;
        if (id == null || id.isBlank()) {
            s = new SuccessStory();
            s.setId(Ids.biz("SS"));
            s.setStatus(DRAFT);
            s.setPublicVisible(false);
            s.setViews(0);
            s.setAuthor(author);
            s.setCreatedAt(LocalDateTime.now());
        } else {
            s = repo.findById(id).orElseThrow(() -> BizException.notFound("成功案例"));
        }
        if (body.get("title") != null) s.setTitle(str(body.get("title")));
        if (body.get("maleAlias") != null) s.setMaleAlias(str(body.get("maleAlias")));
        if (body.get("femaleAlias") != null) s.setFemaleAlias(str(body.get("femaleAlias")));
        if (body.get("cityTag") != null) s.setCityTag(str(body.get("cityTag")));
        if (body.get("ageTag") != null) s.setAgeTag(str(body.get("ageTag")));
        if (body.get("content") != null) s.setContent(str(body.get("content")));
        if (body.get("cover") != null) s.setCover(str(body.get("cover")));
        if (body.get("tags") != null) s.setTags(str(body.get("tags")));
        if (body.get("pairId") != null) s.setPairId(str(body.get("pairId")));
        if (body.get("publicVisible") != null) s.setPublicVisible(Boolean.TRUE.equals(body.get("publicVisible")));
        s.setUpdatedAt(LocalDateTime.now());
        repo.save(s);
        logs.record(id == null ? "新建成功案例" : "编辑成功案例", s.getId(), s.getTitle());
        return s;
    }

    /** 发布（管理员）：草稿/已下架 → 已发布 */
    @Transactional
    public SuccessStory publish(String id, Boolean publicVisible) {
        SuccessStory s = repo.findById(id).orElseThrow(() -> BizException.notFound("成功案例"));
        s.setStatus(PUBLISHED);
        s.setPublicVisible(publicVisible == null || publicVisible);
        s.setPublishedAt(LocalDateTime.now());
        s.setUpdatedAt(LocalDateTime.now());
        repo.save(s);
        logs.record("发布成功案例", id, s.getTitle());
        return s;
    }

    /** 下架（管理员） */
    @Transactional
    public SuccessStory offline(String id) {
        SuccessStory s = repo.findById(id).orElseThrow(() -> BizException.notFound("成功案例"));
        s.setStatus(OFFLINE);
        s.setPublicVisible(false);
        s.setUpdatedAt(LocalDateTime.now());
        repo.save(s);
        logs.record("下架成功案例", id, s.getTitle());
        return s;
    }

    public List<SuccessStory> list(String status) {
        return (status == null || status.isBlank())
                ? repo.findAllByOrderByCreatedAtDesc()
                : repo.findByStatusOrderByCreatedAtDesc(status);
    }

    /** 对外公开列表：仅「已发布 且 publicVisible」 */
    public List<SuccessStory> publicList() {
        return repo.findByStatusAndPublicVisibleOrderByPublishedAtDesc(PUBLISHED, true);
    }

    public SuccessStory detail(String id) {
        return repo.findById(id).orElseThrow(() -> BizException.notFound("成功案例"));
    }

    @Transactional
    public SuccessStory view(String id) {
        SuccessStory s = detail(id);
        s.setViews((s.getViews() == null ? 0 : s.getViews()) + 1);
        return repo.save(s);
    }

    @Transactional
    public void delete(String id) {
        SuccessStory s = repo.findById(id).orElseThrow(() -> BizException.notFound("成功案例"));
        repo.deleteById(id);
        logs.record("删除成功案例", id, s.getTitle());
    }

    public Map<String, Object> summary() {
        List<SuccessStory> all = repo.findAll();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("total", all.size());
        m.put("draftCount", all.stream().filter(s -> DRAFT.equals(s.getStatus())).count());
        m.put("publishedCount", all.stream().filter(s -> PUBLISHED.equals(s.getStatus())).count());
        m.put("offlineCount", all.stream().filter(s -> OFFLINE.equals(s.getStatus())).count());
        m.put("viewTotal", all.stream().mapToInt(s -> s.getViews() == null ? 0 : s.getViews()).sum());
        return m;
    }

    // —— 脱敏辅助 ——

    /** 真实姓名 → 姓氏 + 先生/女士；姓名缺失时用「这位先生/女士」 */
    static String alias(String name, String gender) {
        String suffix = "女".equals(gender) ? "女士" : "先生";
        if (name == null || name.isBlank()) return "这位" + suffix;
        String surname = name.trim().substring(0, 1);
        return surname + suffix;
    }

    /** 年龄 → 五岁一档的年龄段 */
    static String ageBand(Member m) {
        if (m == null || m.getAge() == null) return "年龄保密";
        int a = m.getAge();
        int lo = a / 5 * 5;
        return lo + "-" + (lo + 4) + "岁";
    }

    private static String buildContent(String maleAlias, String femaleAlias, String city,
                                       Member male, Member female, Pair p) {
        StringBuilder sb = new StringBuilder();
        sb.append(city == null ? "" : city + "，");
        sb.append(maleAlias).append("与").append(femaleAlias).append("在红娘");
        sb.append(p.getAgent() == null ? "团队" : p.getAgent()).append("的牵线下相识。");
        if (male != null && male.getJob() != null) {
            sb.append(maleAlias).append("从事").append(male.getJob()).append("工作，");
        }
        if (female != null && female.getJob() != null) {
            sb.append(femaleAlias).append("是一位").append(female.getJob()).append("，");
        }
        sb.append("两人在价值观与生活节奏上高度契合");
        if (p.getScore() != null) sb.append("（系统匹配度 ").append(p.getScore()).append(" 分）");
        sb.append("。经过数次深入交流与线下约见，他们确认了彼此的心意，正式走到了一起。");
        sb.append("愿有情人终成眷属。");
        return sb.toString();
    }

    private static String buildTags(String city, Member male, Member female) {
        StringBuilder sb = new StringBuilder();
        if (city != null) sb.append(city).append(",");
        sb.append("真实案例,");
        if (male != null && Boolean.TRUE.equals(male.getVerifyRealName())) sb.append("实名认证,");
        if (female != null && Boolean.TRUE.equals(female.getVerifyRealName())) sb.append("双向认证,");
        sb.append("红娘推荐");
        return sb.toString();
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }
}
