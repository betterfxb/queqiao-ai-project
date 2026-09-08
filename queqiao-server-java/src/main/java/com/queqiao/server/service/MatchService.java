package com.queqiao.server.service;

import com.queqiao.server.ai.AiClient;
import com.queqiao.server.entity.Member;
import com.queqiao.server.repo.MemberRepository;
import com.queqiao.server.service.SettingService;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 智能匹配：优先调用 Python AI 服务打分；AI 不可用时用本地多维加权规则兜底，
 * 保证「智能推荐」按钮任何时候都能出结果。
 */
@Service
public class MatchService {

    private static final Map<String, Integer> EDU_RANK = Map.of(
            "高中", 1, "大专", 2, "本科", 3, "硕士", 4, "博士", 5);
    private static final List<String> INCOME_ORDER = List.of(
            "20-30万", "30-50万", "50-80万", "80-120万", "120万以上");
    /** 候选池上限：控制单次 AI 请求体与打分开销 */
    private static final int POOL_LIMIT = 200;

    private final MemberRepository members;
    private final AiClient ai;
    private final SettingService settings;

    public MatchService(MemberRepository members, AiClient ai, SettingService settings) {
        this.members = members;
        this.ai = ai;
        this.settings = settings;
    }

    /** 为目标会员生成推荐列表（数据范围取自当前请求上下文） */
    public List<Map<String, Object>> recommend(String memberId, int limit) {
        return recommend(memberId, limit,
                com.queqiao.server.security.RequestContext.isAgent()
                        ? com.queqiao.server.security.RequestContext.agentName()
                        : null);
    }

    /**
     * 为目标会员生成推荐列表（显式传入红娘数据范围）。
     * 供异步 / 并行场景使用：线程池线程没有 RequestContext，必须由调用方在请求线程中取好范围传入，
     * 避免数据隔离失效。
     */
    public List<Map<String, Object>> recommend(String memberId, int limit, String agentScope) {
        Member target = members.findById(memberId).orElse(null);
        if (target == null) return List.of();

        // P0-10：候选池过滤下沉到 DB（异性 / 非暂停 / 红娘数据隔离），并限制候选规模，
        // 避免整表加载与超大 AI 请求体
        List<Member> pool = members.findMatchPool(target.getId(), target.getGender(), agentScope,
                org.springframework.data.domain.PageRequest.of(0, POOL_LIMIT));
        if (pool.isEmpty()) return List.of();

        // 先试 AI；响应结构：{ total, results: [{ member:{...}, score, highlights:[...] }] }
        Map<String, Object> aiRes = ai.match(toMap(target),
                pool.stream().map(this::toMap).toList());
        Map<String, Integer> aiScores = new HashMap<>();
        Map<String, List<String>> aiReasons = new HashMap<>();
        Map<String, Integer> aiSemantic = new HashMap<>();
        Map<String, List<String>> aiKeywords = new HashMap<>();
        String source = "local";
        if (aiRes != null && aiRes.get("results") instanceof List<?> results) {
            for (Object o : results) {
                if (o instanceof Map<?, ?> row) {
                    Object id = null;
                    if (row.get("member") instanceof Map<?, ?> mm) id = mm.get("id");
                    Object sc = row.get("score");
                    if (id != null && sc instanceof Number n) {
                        String sid = String.valueOf(id);
                        aiScores.put(sid, n.intValue());
                        if (row.get("highlights") instanceof List<?> h) {
                            List<String> rs = new ArrayList<>();
                            for (Object s : h) rs.add(String.valueOf(s));
                            aiReasons.put(sid, rs);
                        }
                        Object ssem = row.get("semanticScore");
                        if (ssem instanceof Number sn) aiSemantic.put(sid, sn.intValue());
                        if (row.get("semanticKeywords") instanceof List<?> kw) {
                            List<String> ks = new ArrayList<>();
                            for (Object k : kw) ks.add(String.valueOf(k));
                            aiKeywords.put(sid, ks);
                        }
                    }
                }
            }
            if (!aiScores.isEmpty()) source = "ai";
        }

        List<Map<String, Object>> out = new ArrayList<>();
        for (Member c : pool) {
            Scored local = score(target, c);
            int localSem = local.semanticScore();
            List<String> localKw = local.semanticKeywords();
            if (aiScores.containsKey(c.getId())) {
                int aiScore = aiScores.get(c.getId());
                List<String> reasons = aiReasons.containsKey(c.getId())
                        ? aiReasons.get(c.getId())
                        : local.reasons();
                Map<String, Integer> dims = local.dims();
                int semScore = aiSemantic.getOrDefault(c.getId(), localSem);
                List<String> semKw = aiKeywords.containsKey(c.getId())
                        ? aiKeywords.get(c.getId()) : localKw;
                dims.put("语义契合", semScore);
                Scored s = new Scored(aiScore, reasons, dims, semScore, semKw);
                rowWith(out, c, s, source, semScore, semKw);
            } else {
                rowWith(out, c, local, source, localSem, localKw);
            }
        }
        out.sort((a, b) -> Integer.compare((int) b.get("score"), (int) a.get("score")));
        // 业务规则：推荐匹配度阈值（来自系统设置·业务规则）。threshold<=0 表示不限制。
        int threshold = settings.getInt("biz.matchThreshold", 0);
        if (threshold > 0) {
            out = out.stream()
                    .filter(r -> (int) r.get("score") >= threshold)
                    .collect(java.util.stream.Collectors.toList());
        }
        return out.subList(0, Math.min(limit, out.size()));
    }

    /** 本地多维加权评分：年龄 25 + 学历 20 + 城市 20 + 收入 15 + 活跃度 10 + 等级 10 */
    public Scored score(Member a, Member b) {
        int total = 0;
        List<String> reasons = new ArrayList<>();

        int ageA = a.getAge() == null ? 30 : a.getAge();
        int ageB = b.getAge() == null ? 30 : b.getAge();
        int gap = Math.abs(ageA - ageB);
        int ageScore = gap <= 2 ? 25 : gap <= 4 ? 21 : gap <= 6 ? 16 : gap <= 9 ? 10 : 4;
        total += ageScore;
        if (ageScore >= 21) reasons.add("年龄差 " + gap + " 岁，处于理想区间");

        int ea = EDU_RANK.getOrDefault(nz(a.getEducation()), 3);
        int eb = EDU_RANK.getOrDefault(nz(b.getEducation()), 3);
        int eduGap = Math.abs(ea - eb);
        int eduScore = eduGap == 0 ? 20 : eduGap == 1 ? 15 : eduGap == 2 ? 8 : 3;
        total += eduScore;
        if (eduScore >= 15) reasons.add("学历层次相当（" + nz(a.getEducation()) + " / " + nz(b.getEducation()) + "）");

        String ca = cityOf(a.getCity());
        String cb = cityOf(b.getCity());
        int cityScore = Objects.equals(a.getCity(), b.getCity()) ? 20 : ca.equals(cb) ? 16 : 5;
        total += cityScore;
        if (cityScore >= 16) reasons.add("同城（" + ca + "），见面成本低");

        int ia = INCOME_ORDER.indexOf(nz(a.getIncome()));
        int ib = INCOME_ORDER.indexOf(nz(b.getIncome()));
        int incScore = 8;
        if (ia >= 0 && ib >= 0) {
            int g = Math.abs(ia - ib);
            incScore = g == 0 ? 15 : g == 1 ? 12 : g == 2 ? 7 : 3;
            if (incScore >= 12) reasons.add("经济条件匹配度高");
        }
        total += incScore;

        int act = activeScore(b.getLastActive());
        total += act;
        if (act >= 8) reasons.add("对方近期活跃，响应及时");

        int lvl = levelScore(b.getLevel());
        total += lvl;
        if (lvl >= 8) reasons.add("对方为" + nz(b.getLevel()) + "会员，服务意愿强");

        // 认证可信度加成（产品化认证标签：实名 / 学历 / 资产越完整，推荐权重越高）
        int vReal = Boolean.TRUE.equals(b.getVerifyRealName()) ? 6 : 0;
        int vEdu = Boolean.TRUE.equals(b.getVerifyEducation()) ? 4 : 0;
        int vAsset = Boolean.TRUE.equals(b.getVerifyAsset()) ? 4 : 0;
        int verifyScore = vReal + vEdu + vAsset;
        if (verifyScore > 0) {
            total = Math.min(100, total + verifyScore);
            List<String> vtags = new ArrayList<>();
            if (vReal > 0) vtags.add("实名");
            if (vEdu > 0) vtags.add("学历");
            if (vAsset > 0) vtags.add("资产");
            reasons.add("已完成认证（" + String.join("/", vtags) + "），可信度更高");
        }

        // 标签重合加成
        Set<String> ta = tagSet(a.getTags());
        Set<String> tb = tagSet(b.getTags());
        ta.retainAll(tb);
        int tagScore = 0;
        if (!ta.isEmpty()) {
            tagScore = Math.min(6, ta.size() * 3);
            total = Math.min(100, total + tagScore);
            reasons.add("共同标签：" + String.join("、", ta));
        }

        if (reasons.isEmpty()) reasons.add("基础条件可进一步沟通确认");

        // 各维度百分比（供前端 AI 匹配分析报告使用）
        Map<String, Integer> dims = new LinkedHashMap<>();
        dims.put("年龄匹配", pct(ageScore, 25));
        dims.put("学历匹配", pct(eduScore, 20));
        dims.put("地域便利", pct(cityScore, 20));
        dims.put("经济条件", pct(incScore, 15));
        dims.put("活跃程度", pct(act, 10));
        dims.put("会员等级", pct(lvl, 10));
        dims.put("认证可信", pct(verifyScore, 14));
        dims.put("兴趣重合", pct(tagScore, 6));

        // 语义契合：离线中文文本相似度（字 bigram TF 余弦，与 queqiao-ai 同源，AI 宕机仍可用）
        int semScore = semanticScore(a, b);
        List<String> semKw = semanticKeywords(a, b);
        dims.put("语义契合", semScore);

        return new Scored(Math.max(35, Math.min(99, total)), reasons, dims, semScore, semKw);
    }

    private static int pct(int v, int max) {
        return max <= 0 ? 0 : (int) Math.round(v * 100.0 / max);
    }

    private int activeScore(String lastActive) {
        String s = nz(lastActive);
        if (s.contains("刚刚") || s.contains("分钟")) return 10;
        if (s.contains("小时")) return 8;
        if (s.contains("1天") || s.contains("2天")) return 5;
        return 2;
    }

    private int levelScore(String level) {
        return switch (nz(level)) {
            case "钻石VIP" -> 10;
            case "SVIP" -> 9;
            case "VIP", "金卡" -> 8;
            default -> 4;
        };
    }

    private String cityOf(String city) {
        if (city == null) return "";
        int i = city.indexOf('·');
        return i > 0 ? city.substring(0, i) : city;
    }

    private Set<String> tagSet(String tags) {
        if (tags == null || tags.isBlank()) return new HashSet<>();
        return new HashSet<>(Arrays.asList(tags.split("[,，、]")));
    }

    // ---------------------------------------------------------------- 语义契合
    // 离线中文文本语义相似度：字 bigram + 拉丁词 的 TF 余弦（零外部依赖，与 queqiao-ai 同源）。
    // 两两比较时 IDF 退化为常数，故采用 TF 加权余弦（等同 IDF≡1 的 TF-IDF），
    // 保证 AI 服务不可用时本地仍能量化「语义契合」维度。
    private static final Set<String> SEM_STOP2 = Set.of(
            "的的", "了了", "是是", "我我", "你你", "他他", "她她", "们们", "在在", "和和",
            "也也", "都都", "就就", "不不", "人人", "一一", "有有", "个个", "这这", "那那",
            "着着", "说说", "到到", "会会", "要要", "去去", "上上", "下下", "里里", "后后",
            "前前", "能能", "对对", "很很", "给给", "等等", "与与", "及及", "或或", "让让",
            "把把", "被被", "从从", "向向", "为为", "以以", "之之", "而而", "于于", "其其",
            "此此", "该该", "自己", "我们", "你们", "他们", "她们", "一个", "可以", "这个",
            "那个", "没有", "因为", "所以", "但是", "如果", "希望", "喜欢", "觉得", "比较",
            "非常", "一样", "一些", "什么", "怎么", "这样", "那种", "的话"
    );

    private static String textOf(Member m) {
        String[] parts = {m.getRequirement(), m.getTags(), m.getJob(), m.getCity(),
                m.getEducation(), m.getBio()};
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p != null && !p.isBlank()) {
                sb.append(p.replaceAll("[,，、]", " ")).append(" ");
            }
        }
        return sb.toString();
    }

    private static Map<String, Integer> semTokens(String text) {
        Map<String, Integer> tf = new HashMap<>();
        if (text == null || text.isBlank()) return tf;
        Matcher wm = Pattern.compile("[a-zA-Z][a-zA-Z0-9+#]*").matcher(text.toLowerCase());
        while (wm.find()) {
            tf.merge("w:" + wm.group(), 1, Integer::sum);
        }
        Matcher hm = Pattern.compile("[\\u4e00-\\u9fff]+").matcher(text);
        while (hm.find()) {
            String run = hm.group();
            for (int i = 0; i < run.length() - 1; i++) {
                String bg = run.substring(i, i + 2);
                if (SEM_STOP2.contains(bg)) continue;
                tf.merge("b:" + bg, 1, Integer::sum);
            }
        }
        return tf;
    }

    private static double cosineTf(Map<String, Integer> a, Map<String, Integer> b) {
        if (a.isEmpty() || b.isEmpty()) return 0.0;
        Map<String, Integer> small = a.size() <= b.size() ? a : b;
        Map<String, Integer> large = a.size() <= b.size() ? b : a;
        double dot = 0.0, na = 0.0;
        for (var e : small.entrySet()) {
            double c = e.getValue();
            na += c * c;
            Integer v = large.get(e.getKey());
            if (v != null) dot += c * v;
        }
        double nb = 0.0;
        for (int v : large.values()) nb += (double) v * v;
        if (na == 0.0 || nb == 0.0) return 0.0;
        return dot / (Math.sqrt(na) * Math.sqrt(nb));
    }

    private static int semanticScore(Member a, Member b) {
        return (int) Math.round(cosineTf(semTokens(textOf(a)), semTokens(textOf(b))) * 100);
    }

    private static List<String> semanticKeywords(Member a, Member b) {
        Map<String, Integer> ta = semTokens(textOf(a));
        Map<String, Integer> tb = semTokens(textOf(b));
        List<String[]> shared = new ArrayList<>();
        for (var e : ta.entrySet()) {
            Integer v = tb.get(e.getKey());
            if (v != null) {
                shared.add(new String[]{e.getKey(), String.valueOf(Math.min(e.getValue(), v))});
            }
        }
        shared.sort((x, y) -> Integer.compare(Integer.parseInt(y[1]), Integer.parseInt(x[1])));
        List<String> kws = new ArrayList<>();
        int n = 0;
        for (var s : shared) {
            if (n++ >= 6) break;
            String t = s[0];
            kws.add(t.startsWith("b:") ? t.substring(2) : t.substring(1));
        }
        return kws;
    }

    private static void rowWith(List<Map<String, Object>> out, Member c, Scored s, String source,
                               int semanticScore, List<String> semanticKeywords) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("member", c);
        row.put("score", s.score);
        row.put("reasons", s.reasons);
        row.put("dimensions", s.dims);
        row.put("source", source);
        row.put("semanticScore", semanticScore);
        row.put("semanticKeywords", semanticKeywords);
        out.add(row);
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private Map<String, Object> toMap(Member m) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", m.getId());
        map.put("name", m.getName());
        map.put("gender", m.getGender());
        map.put("age", m.getAge());
        map.put("city", m.getCity());
        map.put("education", m.getEducation());
        map.put("job", m.getJob());
        map.put("income", m.getIncome());
        map.put("height", m.getHeight());
        map.put("level", m.getLevel());
        map.put("tags", m.getTags());
        map.put("requirement", m.getRequirement());
        return map;
    }

    /** 评分结果（dims：各维度百分比，供前端匹配分析报告渲染；semantic* 为离线语义契合信号） */
    public record Scored(int score, List<String> reasons, Map<String, Integer> dims,
                         int semanticScore, List<String> semanticKeywords) {
        public Scored(int score, List<String> reasons) {
            this(score, reasons, Map.of(), 0, List.of());
        }
        public Scored(int score, List<String> reasons, Map<String, Integer> dims) {
            this(score, reasons, dims, 0, List.of());
        }
        public Scored(int score, List<String> reasons, Map<String, Integer> dims,
                      List<String> semanticKeywords) {
            this(score, reasons, dims, 0, semanticKeywords);
        }
    }
}
