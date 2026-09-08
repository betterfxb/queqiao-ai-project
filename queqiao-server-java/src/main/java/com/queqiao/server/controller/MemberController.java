package com.queqiao.server.controller;

import com.queqiao.server.ai.AiClient;
import com.queqiao.server.common.BizException;
import com.queqiao.server.common.Csv;
import com.queqiao.server.common.Ids;
import com.queqiao.server.common.PageResult;
import com.queqiao.server.common.Specs;
import com.queqiao.server.dto.MemberUpsertRequest;
import com.queqiao.server.entity.Member;
import com.queqiao.server.entity.PointLog;
import com.queqiao.server.repo.FollowUpRepository;
import com.queqiao.server.repo.MemberRepository;
import com.queqiao.server.repo.PairRepository;
import com.queqiao.server.security.Masker;
import com.queqiao.server.security.RequestContext;
import com.queqiao.server.security.SensitiveScope;
import com.queqiao.server.security.SensitiveType;
import com.queqiao.server.service.LogService;
import com.queqiao.server.service.PointsService;
import jakarta.validation.Valid;
import org.springframework.beans.BeanUtils;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import javax.imageio.ImageIO;

/** 会员管理 */
@RestController
@RequestMapping("/api/members")
public class MemberController {

    private static final String[] AVATAR_BG = {
            "linear-gradient(135deg,#F4C6D0,#A8667D)",
            "linear-gradient(135deg,#B8C4D0,#5A7A9A)",
            "linear-gradient(135deg,#FDE8D8,#C8956C)",
            "linear-gradient(135deg,#C8D8C0,#5B8C5A)",
            "linear-gradient(135deg,#D9C4E8,#7A6AA8)",
    };

    private final MemberRepository members;
    private final PairRepository pairs;
    private final FollowUpRepository follows;
    private final AiClient ai;
    private final LogService logs;
    private final PointsService points;

    public MemberController(MemberRepository members, PairRepository pairs, FollowUpRepository follows,
                            AiClient ai, LogService logs, PointsService points) {
        this.members = members;
        this.pairs = pairs;
        this.follows = follows;
        this.ai = ai;
        this.logs = logs;
        this.points = points;
    }

    /** 列表：关键词 + 多维筛选 + 分页 */
    @GetMapping
    public PageResult<Member> list(@RequestParam(required = false) String keyword,
                                   @RequestParam(required = false) String gender,
                                   @RequestParam(required = false) String level,
                                   @RequestParam(required = false) String status,
                                   @RequestParam(required = false) String agent,
                                   @RequestParam(required = false) String city,
                                   @RequestParam(required = false) String education,
                                   @RequestParam(required = false) Integer ageMin,
                                   @RequestParam(required = false) Integer ageMax,
                                   @RequestParam(required = false) Integer heightMin,
                                   @RequestParam(required = false) Integer heightMax,
                                   @RequestParam(required = false) String income,
                                   @RequestParam(required = false) String marriage,
                                   @RequestParam(required = false) String house,
                                   @RequestParam(required = false) String car,
                                   @RequestParam(defaultValue = "1") int page,
                                   @RequestParam(defaultValue = "20") int size,
                                   @RequestParam(defaultValue = "createdAt") String sortBy,
                                   @RequestParam(defaultValue = "desc") String order) {
        var spec = Specs.<Member>of()
                .keyword(keyword, "name", "id", "phone", "job", "city")
                .eq("gender", gender)
                .eq("level", level)
                .eq("status", status)
                .eq("agent", scopeAgent(agent))
                .eq("education", education)
                .like("city", city)
                .between("age", ageMin, ageMax)
                .between("height", heightMin, heightMax)
                .eq("income", income)
                .eq("marriage", marriage)
                .eq("house", house)
                .eq("car", car)
                .build();
        Sort s = Sort.by("asc".equalsIgnoreCase(order) ? Sort.Direction.ASC : Sort.Direction.DESC, sortBy);
        var resultPage = members.findAll(spec, PageRequest.of(Math.max(0, page - 1), size, s));
        // 列表不下发 photo：单张 base64 照片可达数百 KB，一页 20 条即 MB 级，会拖垮移动端。
        // 照片仅在详情接口（GET /api/members/{id}）提供。
        // 注意：这里必须走「非托管副本」置空，绝不能直接对查出来的托管实体 setPhoto(null)——
        // OSIV 开启时实体仍受 EntityManager 管理，事务提交会把 null flush 回库，造成照片被永久清空。
        List<Member> safe = resultPage.getContent().stream().map(m -> {
            Member v = new Member();
            BeanUtils.copyProperties(m, v);
            v.setPhoto(null);                        // 列表不下发原图（可达数百 KB/条）
            v.setPhotoThumb(thumbOf(m.getPhoto()));  // 改为下发 120px 缩略图（几 KB）
            return v;
        }).toList();
        return new PageResult<>(safe, resultPage.getTotalElements(), resultPage.getNumber() + 1, resultPage.getSize());
    }

    /** 列表缩略图宽度（px）：够头像位清晰即可，再大就是浪费流量 */
    private static final int THUMB_WIDTH = 120;

    /**
     * 生成照片缩略图（base64 dataURL → 120px 宽 JPEG dataURL）。
     * 纯 JDK ImageIO，无第三方依赖；任何异常都吞掉返回 null——
     * 缩略图只是锦上添花，绝不能因为一张图坏了整个会员列表。
     */
    private static String thumbOf(String dataUrl) {
        if (dataUrl == null || !dataUrl.startsWith("data:image")) return null;
        try {
            int comma = dataUrl.indexOf(',');
            if (comma < 0) return null;
            byte[] src = Base64.getDecoder().decode(dataUrl.substring(comma + 1).trim());
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(src));
            if (img == null || img.getWidth() <= 0) return null;
            int w = THUMB_WIDTH;
            int h = Math.max(1, img.getHeight() * THUMB_WIDTH / img.getWidth());
            BufferedImage dst = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = dst.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(img, 0, 0, w, h, Color.WHITE, null);
            g.dispose();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            if (!ImageIO.write(dst, "jpg", out)) return null;
            return "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(out.toByteArray());
        } catch (Exception e) {
            return null;
        }
    }

    /** 单次导出最大条数（超出请按条件分批导出） */
    private static final int EXPORT_LIMIT = 5000;

    private static final List<String> VERIFYING_STATUS = List.of("认证中", "待审核");
    private static final List<String> VIP_LEVELS = List.of("VIP", "SVIP", "钻石VIP", "金卡");

    /** 统计卡片：总数 / 本月新增 / 认证中 / VIP 占比（全部走 DB 聚合，不整表加载） */
    @GetMapping("/stats")
    public Map<String, Object> stats() {
        String me = currentAgent();
        LocalDateTime monthStart = LocalDateTime.now().withDayOfMonth(1)
                .withHour(0).withMinute(0).withSecond(0).withNano(0);
        long total, newThisMonth, verifying, vip, male, female;
        if (me == null) {
            total = members.count();
            newThisMonth = members.countByCreatedAtAfter(monthStart);
            verifying = members.countByStatusIn(VERIFYING_STATUS);
            vip = members.countByLevelIn(VIP_LEVELS);
            male = members.countByGender("男");
            female = members.countByGender("女");
        } else {
            total = members.countByAgent(me);
            newThisMonth = members.countByAgentAndCreatedAtAfter(me, monthStart);
            verifying = members.countByAgentAndStatusIn(me, VERIFYING_STATUS);
            vip = members.countByAgentAndLevelIn(me, VIP_LEVELS);
            male = members.countByAgentAndGender(me, "男");
            female = members.countByAgentAndGender(me, "女");
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("total", total);
        m.put("newThisMonth", newThisMonth);
        m.put("verifying", verifying);
        m.put("vip", vip);
        m.put("vipRate", total == 0 ? 0 : Math.round(vip * 1000.0 / total) / 10.0);
        m.put("male", male);
        m.put("female", female);
        return m;
    }

    /** 详情（含牵线记录） */
    @GetMapping("/{id}")
    public Map<String, Object> detail(@PathVariable String id) {
        Member m = members.findById(id).orElseThrow(() -> BizException.notFound("会员"));
        assertOwned(m);
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("member", m);
        res.put("pairs", pairs.findByMaleIdOrFemaleId(id, id));
        res.put("followups", follows.findByMemberIdOrderByCreatedAtDesc(id));
        return res;
    }

    /** 会员积分概览（累计 / 等级 / 近 20 条流水） */
    @GetMapping("/{id}/points")
    public Map<String, Object> points(@PathVariable String id) {
        Member m = members.findById(id).orElseThrow(() -> BizException.notFound("会员"));
        assertOwned(m);
        return points.of(id);
    }

    /** 手动发放积分（管理员/红娘，按规则类型发放并留痕） */
    @PostMapping("/{id}/points/earn")
    @Transactional
    public Map<String, Object> earnPoints(@PathVariable String id, @RequestBody Map<String, String> body) {
        Member m = members.findById(id).orElseThrow(() -> BizException.notFound("会员"));
        assertOwned(m);
        String type = body.get("type");
        if (type == null || type.isBlank()) throw new BizException("缺少积分类型");
        PointLog log = points.earn(id, type, body.get("refId"), logs.currentUserName(), body.get("remark"));
        Map<String, Object> res = new LinkedHashMap<>();
        if (log == null) {
            res.put("ok", false);
            res.put("message", "本次未发放（重复发放或会员不存在）");
        } else {
            res.put("ok", true);
            res.put("log", Map.of("type", log.getType(), "typeLabel", log.getTypeLabel(), "delta", log.getDelta()));
        }
        return res;
    }

    /** 认证标签展示（产品化认证徽章数据源）：已实名 / 学历已核 / 资产已认证 */
    @GetMapping("/{id}/verify")
    public Map<String, Object> verify(@PathVariable String id) {
        Member m = members.findById(id).orElseThrow(() -> BizException.notFound("会员"));
        assertOwned(m);
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("realName", Boolean.TRUE.equals(m.getVerifyRealName()));
        res.put("education", Boolean.TRUE.equals(m.getVerifyEducation()));
        res.put("asset", Boolean.TRUE.equals(m.getVerifyAsset()));
        res.put("realPerson", Boolean.TRUE.equals(m.getVerifyRealPerson()));
        res.put("realNameAt", m.getVerifyRealNameAt());
        res.put("educationAt", m.getVerifyEducationAt());
        res.put("assetAt", m.getVerifyAssetAt());
        res.put("realPersonAt", m.getVerifyRealPersonAt());
        return res;
    }

    /** 新增（入参走 DTO 白名单，认证/统计等字段客户端不可写） */
    @PostMapping
    @Transactional
    public Member create(@Valid @RequestBody MemberUpsertRequest body) {
        if (body.getName() == null || body.getName().isBlank()) throw new BizException("请填写姓名");
        Member m = new Member();
        copyNonNull(body, m);
        m.setId(Ids.member());
        String me = currentAgent();
        if (me != null) m.setAgent(me); // 红娘新建会员强制归属本人，避免越权写入他人名下
        applyDefaults(m);
        m.setCreatedAt(LocalDateTime.now());
        m.setUpdatedAt(LocalDateTime.now());
        Member saved = members.save(m);
        logs.record("新增会员", saved.getId(), saved.getName());
        return saved;
    }

    /** 编辑（入参走 DTO 白名单） */
    @PutMapping("/{id}")
    @Transactional
    public Member update(@PathVariable String id, @Valid @RequestBody MemberUpsertRequest body) {
        Member m = members.findById(id).orElseThrow(() -> BizException.notFound("会员"));
        assertOwned(m);
        if (currentAgent() != null) body.setAgent(null); // 红娘不可自行改派归属
        boolean wasComplete = isProfileComplete(m);
        copyNonNull(body, m);
        m.setUpdatedAt(LocalDateTime.now());
        Member saved = members.save(m);
        logs.record("编辑会员", id, saved.getName());
        // 资料完善奖励：仅当资料从"不完整"变为"完整"时一次性发放
        if (!wasComplete && isProfileComplete(saved)) {
            points.earn(id, "PROFILE_COMPLETE", id, logs.currentUserName(), "资料完善奖励");
        }
        return saved;
    }

    /** 变更状态（认证通过 / 暂停 / 恢复等） */
    @PostMapping("/{id}/status")
    @Transactional
    public Member changeStatus(@PathVariable String id, @RequestBody Map<String, String> body) {
        Member m = members.findById(id).orElseThrow(() -> BizException.notFound("会员"));
        assertOwned(m);
        String status = body.get("status");
        if (status == null || status.isBlank()) throw new BizException("缺少 status");
        m.setStatus(status);
        m.setUpdatedAt(LocalDateTime.now());
        logs.record("变更会员状态→" + status, id, m.getName());
        return members.save(m);
    }

    /** 分配红娘 */
    @PostMapping("/{id}/assign")
    @Transactional
    public Member assign(@PathVariable String id, @RequestBody Map<String, String> body) {
        if (currentAgent() != null) throw BizException.forbidden("红娘无权改派会员归属");
        Member m = members.findById(id).orElseThrow(() -> BizException.notFound("会员"));
        String agent = body.get("agent");
        if (agent == null || agent.isBlank()) throw new BizException("缺少 agent");
        m.setAgent(agent);
        m.setUpdatedAt(LocalDateTime.now());
        logs.record("分配红娘→" + agent, id, m.getName());
        return members.save(m);
    }

    /** 删除 */
    @DeleteMapping("/{id}")
    @Transactional
    public Map<String, Object> delete(@PathVariable String id) {
        Member m = members.findById(id).orElseThrow(() -> BizException.notFound("会员"));
        assertOwned(m);
        members.deleteById(id);
        logs.record("删除会员", id, id);
        return Map.of("ok", true, "id", id);
    }

    /** 批量删除 */
    @PostMapping("/batch-delete")
    @Transactional
    public Map<String, Object> batchDelete(@RequestBody Map<String, List<String>> body) {
        List<String> ids = body.getOrDefault("ids", List.of());
        if (currentAgent() != null) {
            // 红娘批量删除前逐条校验归属，任一越权即整批拒绝
            members.findAllById(ids).forEach(this::assertOwned);
        }
        members.deleteAllById(ids);
        logs.record("批量删除会员", String.join(",", ids), ids.size() + " 人");
        return Map.of("ok", true, "count", ids.size());
    }

    /** AI 智能建档：自然语言 → 结构化会员 */
    @PostMapping("/parse")
    public Map<String, Object> parse(@RequestBody Map<String, String> body) {
        String text = body.getOrDefault("text", "");
        if (text.isBlank()) throw new BizException("请输入待解析的文本");
        Map<String, Object> r = ai.parse(text);
        if (r == null) {
            r = new LinkedHashMap<>();
            r.put("fields", localParse(text));
            r.put("source", "local-fallback");
            r.put("confidence", 0.6);
        }
        return r;
    }

    /** 导出 CSV */
    @GetMapping("/export")
    public ResponseEntity<byte[]> export(@RequestParam(required = false) String keyword,
                                         @RequestParam(required = false) String gender,
                                         @RequestParam(required = false) String level,
                                         @RequestParam(required = false) String status) {
        var b = Specs.<Member>of()
                .keyword(keyword, "name", "id", "phone")
                .eq("gender", gender).eq("level", level).eq("status", status);
        String me = currentAgent();
        if (me != null) b.eq("agent", me);
        // 导出上限，避免一次性把整表拉进内存打爆堆
        List<Member> list = members.findAll(b.build(), PageRequest.of(0, EXPORT_LIMIT,
                Sort.by(Sort.Direction.DESC, "createdAt"))).getContent();
        boolean full = RequestContext.isAdmin() || RequestContext.isAgent();
        StringBuilder sb = new StringBuilder("\uFEFF会员编号,姓名,性别,年龄,城市,学历,职业,等级,状态,负责红娘,牵线次数,成功率,手机\n");
        for (Member m : list) {
            sb.append(Csv.row(m.getId(), m.getName(), m.getGender(), m.getAge(), m.getCity(),
                    m.getEducation(), m.getJob(), m.getLevel(), m.getStatus(), m.getAgent(),
                    m.getMatchCount(), m.getSuccessRate(),
                    Masker.mask(SensitiveType.PHONE, m.getPhone(), full)));
        }
        byte[] data = sb.toString().getBytes(StandardCharsets.UTF_8);
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.parseMediaType("text/csv;charset=UTF-8"));
        h.setContentDispositionFormData("attachment", "members.csv");
        logs.record("导出会员", "-", list.size() + " 条");
        return new ResponseEntity<>(data, h, 200);
    }

    // —— 数据范围 / 权限辅助 ——
    private String currentAgent() {
        return RequestContext.isAgent() ? RequestContext.agentName() : null;
    }

    private String scopeAgent(String requested) {
        String me = currentAgent();
        return me != null ? me : requested;
    }

    /** 越权访问拦截：红娘只能访问本人负责的会员，未授权尝试计入审计日志 */
    private void assertOwned(Member m) {
        String me = currentAgent();
        if (me != null && !me.equals(m.getAgent())) {
            logs.record("越权访问拦截", m.getId(), "尝试访问 " + nz(m.getAgent()) + " 名下会员");
            throw BizException.forbidden("无权访问该会员");
        }
    }

    // —— 内部工具 ——

    private void applyDefaults(Member m) {
        if (m.getLevel() == null) m.setLevel("普通");
        if (m.getStatus() == null) m.setStatus("待审核");
        if (m.getMatchCount() == null) m.setMatchCount(0);
        if (m.getSuccessRate() == null) m.setSuccessRate(0);
        if (m.getVerified() == null) m.setVerified(false);
        if (m.getLastActive() == null) m.setLastActive("刚刚");
        if (m.getAvatarBg() == null) {
            m.setAvatarBg(AVATAR_BG[Math.abs(Objects.hashCode(m.getName())) % AVATAR_BG.length]);
        }
    }

    /** DTO → 实体：仅白名单字段，认证标签/牵线统计等服务端字段不参与拷贝 */
    private void copyNonNull(MemberUpsertRequest src, Member dst) {
        if (src.getName() != null) dst.setName(src.getName());
        if (src.getGender() != null) dst.setGender(src.getGender());
        if (src.getAge() != null) dst.setAge(src.getAge());
        if (src.getCity() != null) dst.setCity(src.getCity());
        if (src.getProvince() != null) dst.setProvince(src.getProvince());
        if (src.getDistrict() != null) dst.setDistrict(src.getDistrict());
        if (src.getEducation() != null) dst.setEducation(src.getEducation());
        if (src.getJob() != null) dst.setJob(src.getJob());
        if (src.getLevel() != null) dst.setLevel(src.getLevel());
        if (src.getStatus() != null) dst.setStatus(src.getStatus());
        if (src.getAgent() != null) dst.setAgent(src.getAgent());
        if (src.getPhone() != null) dst.setPhone(src.getPhone());
        if (src.getHeight() != null) dst.setHeight(src.getHeight());
        if (src.getIncome() != null) dst.setIncome(src.getIncome());
        if (src.getMarriage() != null) dst.setMarriage(src.getMarriage());
        if (src.getHasChild() != null) dst.setHasChild(src.getHasChild());
        if (src.getHometown() != null) dst.setHometown(src.getHometown());
        if (src.getHouse() != null) dst.setHouse(src.getHouse());
        if (src.getCar() != null) dst.setCar(src.getCar());
        if (src.getRequirement() != null) dst.setRequirement(src.getRequirement());
        if (src.getTags() != null) dst.setTags(src.getTags());
        if (src.getBio() != null) dst.setBio(src.getBio());
        if (src.getPhoto() != null) dst.setPhoto(src.getPhoto());
        if (src.getAvatarBg() != null) dst.setAvatarBg(src.getAvatarBg());
    }

    /** AI 不可用时的本地兜底解析（正则抽取） */
    private Map<String, Object> localParse(String text) {
        Map<String, Object> f = new LinkedHashMap<>();
        var age = java.util.regex.Pattern.compile("(\\d{2})\\s*岁").matcher(text);
        if (age.find()) f.put("age", Integer.parseInt(age.group(1)));
        var height = java.util.regex.Pattern.compile("(1[4-9]\\d)\\s*(cm|厘米|公分)?").matcher(text);
        if (height.find()) f.put("height", Integer.parseInt(height.group(1)));
        var phone = java.util.regex.Pattern.compile("1[3-9]\\d{9}").matcher(text);
        if (phone.find()) f.put("phone", phone.group());
        if (text.contains("女")) f.put("gender", "女");
        else if (text.contains("男")) f.put("gender", "男");
        for (String edu : new String[]{"博士", "硕士", "本科", "大专", "高中"}) {
            if (text.contains(edu)) {
                f.put("education", edu);
                break;
            }
        }
        for (String city : new String[]{"北京", "上海", "广州", "深圳", "杭州", "成都", "南京", "武汉", "西安"}) {
            if (text.contains(city)) {
                f.put("city", city);
                break;
            }
        }
        return f;
    }

    private static String nz(Object o) {
        if (o == null) return "";
        return String.valueOf(o).replace(",", "，").replace("\n", " ");
    }

    /** 资料完整判定（关键字段齐全才算完整，用于"完善资料"一次性积分发放） */
    private static boolean isProfileComplete(Member m) {
        return m != null
                && nz(m.getName()).length() > 0
                && nz(m.getGender()).length() > 0
                && m.getAge() != null
                && nz(m.getCity()).length() > 0
                && nz(m.getEducation()).length() > 0
                && nz(m.getJob()).length() > 0
                && nz(m.getPhone()).length() > 0;
    }
}
