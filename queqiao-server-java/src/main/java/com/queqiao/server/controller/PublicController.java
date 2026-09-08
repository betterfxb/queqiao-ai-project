package com.queqiao.server.controller;

import com.queqiao.server.common.BizException;
import com.queqiao.server.common.Ids;
import com.queqiao.server.entity.Member;
import com.queqiao.server.repo.MemberRepository;
import com.queqiao.server.service.LogService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 公网入口（扫码登记 · 微信 H5 表单）。
 *
 * <p>面向 B2B 获客闭环：红娘把线下/线上渠道的潜在会员引导扫码，
 * 表单提交后在此建档（默认待审核 + 归属指定红娘），进入会员池待跟进。
 * 该接口无需登录，由 {@link com.queqiao.server.config.SecurityFilter} 白名单放行，
 * 手机号等敏感字段在响应时经 {@link com.queqiao.server.security.SensitiveSerializer} 自动脱敏。</p>
 */
@RestController
@RequestMapping("/api/public")
public class PublicController {

    private static final Pattern PHONE = Pattern.compile("1[3-9]\\d{9}");
    /** 称呼：2-30 字，以中文或字母开头，可含数字/·/空格（禁止纯数字/纯符号） */
    private static final Pattern NAME = Pattern.compile("^[\\u4e00-\\u9fa5a-zA-Z][\\u4e00-\\u9fa5a-zA-Z0-9·\\s]{1,29}$");
    private static final String[] AVATAR_BG = {
            "linear-gradient(135deg,#F4C6D0,#A8667D)",
            "linear-gradient(135deg,#B8C4D0,#5A7A9A)",
            "linear-gradient(135deg,#FDE8D8,#C8956C)",
            "linear-gradient(135deg,#C8D8C0,#5B8C5A)",
            "linear-gradient(135deg,#D9C4E8,#7A6AA8)",
    };

    /** 合法月收入档位白名单（与信息收集页下拉选项一致）—— 结构化字段，拒绝任意文本。 */
    private static final java.util.Set<String> VALID_INCOME = java.util.Set.of(
            "5k 以下","5k-1w","1w-2w","2w-5w","5w-10w","10w 以上"
    );

    /** 合法学历档位白名单（与信息收集页下拉选项一致）—— 结构化字段，拒绝任意文本。 */
    private static final java.util.Set<String> VALID_EDUCATION = java.util.Set.of(
            "高中及以下","大专","本科","硕士","博士"
    );

    /**
     * 全量行政区划数据（与信息收集页三级级联同源）：省 → 市 → [区/县...]。
     * 从 resources/cities.json 加载（民政部最新县级区划，覆盖 34 省 / 356 市 / 2859 区县）。
     * 不设区的地级市（东莞/中山/嘉峪关/儋州/三沙等）其区县列表为空。
     */
    private static final java.util.Map<String, java.util.Map<String, java.util.List<String>>> REGION =
            loadRegions();
    /** 全部合法地级市（含直辖市/特别行政区/台湾的省级直管市名）。 */
    private static final java.util.Set<String> ALL_CITIES = new java.util.HashSet<>();
    /** 城市 → 所属省份（用于省市一致性校验）。 */
    private static final java.util.Map<String, String> CITY_PROVINCE = new java.util.HashMap<>();
    /** 城市 → 其合法区/县列表（空列表表示不设区的市，允许 district 为空或与市同名）。 */
    private static final java.util.Map<String, java.util.List<String>> CITY_DISTRICTS = new java.util.HashMap<>();
    static {
        for (var e : REGION.entrySet()) {
            String prov = e.getKey();
            for (var c : e.getValue().entrySet()) {
                String city = c.getKey();
                ALL_CITIES.add(city);
                CITY_PROVINCE.put(city, prov);
                CITY_DISTRICTS.put(city, c.getValue());
            }
        }
    }

    private static java.util.Map<String, java.util.Map<String, java.util.List<String>>> loadRegions() {
        java.util.Map<String, java.util.Map<String, java.util.List<String>>> out = new java.util.LinkedHashMap<>();
        try (java.io.InputStream in = PublicController.class.getClassLoader().getResourceAsStream("cities.json")) {
            if (in == null) throw new IllegalStateException("classpath 未找到 cities.json");
            byte[] bytes = in.readAllBytes();
            String txt = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
            // 轻量解析：依赖 org.springframework 的 Jackson（已在依赖中），避免手写 JSON 解析
            com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
            java.util.Map<String, Object> root = om.readValue(txt, java.util.Map.class);
            for (var pe : root.entrySet()) {
                String prov = pe.getKey();
                java.util.Map<String, Object> cities = (java.util.Map<String, Object>) pe.getValue();
                java.util.Map<String, java.util.List<String>> cityMap = new java.util.LinkedHashMap<>();
                for (var ce : cities.entrySet()) {
                    String city = ce.getKey();
                    java.util.List<Object> dists = (java.util.List<Object>) ce.getValue();
                    java.util.List<String> dl = new java.util.ArrayList<>();
                    for (Object d : dists) dl.add(d.toString());
                    cityMap.put(city, dl);
                }
                out.put(prov, cityMap);
            }
        } catch (Exception ex) {
            throw new IllegalStateException("加载 cities.json 失败：" + ex.getMessage(), ex);
        }
        return out;
    }

    private final MemberRepository members;
    private final LogService logs;

    public PublicController(MemberRepository members, LogService logs) {
        this.members = members;
        this.logs = logs;
    }

    /**
     * 扫码登记建档（信息收集页提交入口）。
     * 入参与红娘管理端会员维护格式对齐：基础字段（name/gender/phone）必填校验，
     * 其余择偶与条件字段（age/height/city/education/job/income/marriage/hasChild/
     * house/requirement）按 Member 实体约束写入，缺失则留空，不会覆盖服务端流程字段。
     * remark 作为「给红娘的留言」写入 bio。
     */
    @PostMapping("/register")
    public Map<String, Object> register(@RequestBody Map<String, Object> body) {
        String name = trim(body.get("name"));
        String gender = trim(body.get("gender"));
        String phone = trim(body.get("phone"));
        String agent = trim(body.get("agent"));

        if (name.isEmpty()) throw new BizException("请填写称呼");
        if (name.length() > 30) throw new BizException("称呼不能超过 30 个字");
        if (!NAME.matcher(name).matches()) throw new BizException("称呼需为 2-30 位，以中文或字母开头，勿用纯数字");
        if (!gender.isEmpty() && !("男".equals(gender) || "女".equals(gender))) {
            throw new BizException("性别仅支持「男」或「女」");
        }
        if (!phone.isEmpty() && !PHONE.matcher(phone).matches()) {
            throw new BizException("手机号格式不正确");
        }
        // 数值字段边界校验（与 Member 实体 @Min/@Max 对齐），非法则拒绝而非静默丢弃
        Integer age = toBoundedInt(body.get("age"), 18, 100, "年龄需在 18~100 之间");
        Integer height = toBoundedInt(body.get("height"), 120, 230, "身高需在 120~230cm 之间");
        String marriage = trim(body.get("marriage"));
        if (!marriage.isEmpty() && !("未婚".equals(marriage) || "离异".equals(marriage) || "丧偶".equals(marriage))) {
            throw new BizException("婚姻状况仅支持「未婚 / 离异 / 丧偶」");
        }
        String hasChild = trim(body.get("hasChild"));
        if (!hasChild.isEmpty() && !("无".equals(hasChild) || "有".equals(hasChild))) {
            throw new BizException("是否育有子女仅支持「无 / 有」");
        }
        // 城市：必填且必须为合法地级市（三级级联兜底，拒绝任意脏文本）
        String city = trim(body.get("city"));
        if (city.isEmpty()) throw new BizException("请选择所在城市");
        if (!ALL_CITIES.contains(city)) throw new BizException("城市不在可选范围内，请从下拉中选择");
        // 区/县：必填；若该市设有区县则必须命中该市合法区县列表（同名区级重名按城市判定）；
        //         不设区的市（东莞/中山/嘉峪关/儋州/三沙等）允许为空或与市同名（前端补同名占位项）
        String district = trim(body.get("district"));
        java.util.List<String> legalDistricts = CITY_DISTRICTS.get(city);
        if (legalDistricts == null) legalDistricts = java.util.Collections.emptyList();
        boolean noDistrictCity = legalDistricts.isEmpty();
        if (district.isEmpty()) {
            if (!noDistrictCity) throw new BizException("请选择所在区/县");
        } else {
            if (!noDistrictCity && !legalDistricts.contains(district)) {
                throw new BizException("区/县不在「" + city + "」的可选范围内，请从下拉中选择");
            }
        }
        // 省份：可选；若传则必须与该城市在行政区划中的所属省一致（拒绝伪造请求任意字符串 + 省市错位）
        String province = trim(body.get("province"));
        String cityProvince = CITY_PROVINCE.get(city);
        if (!province.isEmpty() && !province.equals(cityProvince)) {
            throw new BizException("省份与所选城市不一致，请从下拉中选择");
        }
        // 学历 / 月收入：结构化枚举字段，必须命中白名单（取代纯长度校验，杜绝任意文本脏数据）
        String education = trim(body.get("education"));
        if (!education.isEmpty() && !VALID_EDUCATION.contains(education)) {
            throw new BizException("学历取值不合法，请从下拉中选择");
        }
        String job = trim(body.get("job"));
        checkLen(job, 60, "职业");
        if (!job.isEmpty() && job.matches("\\d+")) throw new BizException("职业不能为纯数字");
        String income = trim(body.get("income"));
        if (!income.isEmpty() && !VALID_INCOME.contains(income)) {
            throw new BizException("月收入取值不合法，请从下拉中选择");
        }
        checkLen(body.get("house"), 40, "房车情况");
        checkLen(body.get("requirement"), 2000, "择偶要求");
        checkLen(body.get("remark"), 500, "留言");
        // 本人照片：选填；前端已压缩为 base64，这里仅做大小上限拦截（防超大请求体）
        String photo = trim(body.get("photo"));
        if (photo.length() > 700000) throw new BizException("照片过大，请压缩后上传");

        Member m = new Member();
        m.setId(Ids.member());
        m.setName(name);
        m.setGender(gender.isEmpty() ? null : gender);
        m.setPhone(phone.isEmpty() ? null : phone);
        m.setAgent(agent.isEmpty() ? "待分配" : agent);
        m.setAge(age);
        m.setHeight(height);
        m.setCity(city);
        m.setDistrict(district.isEmpty() ? null : district);
        m.setProvince(province.isEmpty() ? null : province);
        m.setEducation(education);
        m.setJob(job);
        m.setIncome(income);
        m.setMarriage(marriage.isEmpty() ? null : marriage);
        m.setHasChild(hasChild.isEmpty() ? null : hasChild);
        m.setHouse(trim(body.get("house")));
        m.setRequirement(trim(body.get("requirement")));
        m.setStatus("待审核");
        m.setLevel("普通");
        m.setMatchCount(0);
        m.setSuccessRate(0);
        m.setVerified(false);
        m.setLastActive("刚刚");
        m.setAvatarBg(AVATAR_BG[Math.abs(name.hashCode()) % AVATAR_BG.length]);
        m.setBio(trim(body.get("remark")));
        m.setPhoto(photo.isEmpty() ? null : photo);
        m.setCreatedAt(LocalDateTime.now());
        m.setUpdatedAt(LocalDateTime.now());

        Member saved = members.save(m);
        logs.record("扫码登记建档", saved.getId(), name + "（" + (agent.isEmpty() ? "待分配" : agent) + "）");

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("id", saved.getId());
        res.put("name", saved.getName());
        res.put("gender", saved.getGender());
        res.put("phone", saved.getPhone());
        res.put("status", saved.getStatus());
        res.put("agent", saved.getAgent());
        res.put("message", "登记成功，红娘将在 24 小时内与您联系确认");
        return res;
    }

    /** 字符串长度上限校验（空值跳过） */
    private void checkLen(Object v, int max, String label) {
        String s = v == null ? "" : v.toString().trim();
        if (s.length() > max) throw new BizException(label + "过长（上限 " + max + " 字）");
    }

    /** 数值字段解析并做边界校验；非数字 / 越界则按 throwOnInvalid 拒绝 */
    private Integer toBoundedInt(Object v, int min, int max, String msg) {
        if (v == null) return null;
        String s = v.toString().trim();
        if (s.isEmpty()) return null;
        int n;
        try { n = Integer.parseInt(s); } catch (NumberFormatException e) { throw new BizException(msg); }
        if (n < min || n > max) throw new BizException(msg);
        return n;
    }

    private static String trim(Object s) {
        return s == null ? "" : s.toString().trim();
    }
}
