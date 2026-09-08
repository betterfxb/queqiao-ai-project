package com.queqiao.server.service;

import com.queqiao.server.common.BizException;
import com.queqiao.server.common.Ids;
import com.queqiao.server.entity.ConsentRecord;
import com.queqiao.server.entity.DocTemplate;
import com.queqiao.server.repo.ConsentRecordRepository;
import com.queqiao.server.repo.DocTemplateRepository;
import com.queqiao.server.service.LogService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 合规三件套：用户协议 / 隐私政策 / 退费规则 / 婚介服务合同 的可配置模板管理，
 * 以及注册/付费流程的「强制确认」同意留痕（ConsentRecord）。
 */
@Service
public class DocService {

    /** 必须完成同意的四类合规文档 */
    public static final List<String> REQUIRED = List.of("用户协议", "隐私政策", "退费规则", "婚介服务合同");

    private final DocTemplateRepository docs;
    private final ConsentRecordRepository consents;
    private final LogService logs;

    public DocService(DocTemplateRepository docs, ConsentRecordRepository consents, LogService logs) {
        this.docs = docs;
        this.consents = consents;
        this.logs = logs;
    }

    public List<DocTemplate> list() {
        return docs.findAllByOrderByTypeAsc();
    }

    public DocTemplate getByType(String type) {
        return docs.findByType(type).orElseThrow(() -> BizException.notFound("合规文档：" + type));
    }

    /** 保存模板（管理员），版本号自动 +0.1 */
    public DocTemplate save(String type, String title, String content, String operator) {
        if (type == null || type.isBlank()) throw new BizException("缺少文档类型");
        DocTemplate d = docs.findByType(type).orElse(null);
        boolean isNew = d == null;
        if (isNew) {
            d = new DocTemplate();
            d.setId(Ids.biz("DOC"));
            d.setType(type);
            d.setVersion("1.0");
        } else {
            try {
                double v = Double.parseDouble(d.getVersion());
                d.setVersion(String.format("%.1f", v + 0.1));
            } catch (Exception e) {
                d.setVersion("1.0");
            }
        }
        d.setTitle(title);
        d.setContent(content);
        d.setUpdatedBy(operator);
        d.setUpdatedAt(LocalDateTime.now());
        docs.save(d);
        logs.record("更新合规文档", type, title);
        return d;
    }

    /** 记录一次合规同意（注册 / 付费场景的强制勾选） */
    public ConsentRecord consent(String memberId, String memberName, String docType, String version, String channel) {
        if (memberId == null || memberId.isBlank()) throw new BizException("缺少会员");
        if (docType == null || docType.isBlank()) throw new BizException("缺少文档类型");
        ConsentRecord c = new ConsentRecord();
        c.setId(Ids.biz("CS"));
        c.setMemberId(memberId);
        c.setMemberName(memberName);
        c.setDocType(docType);
        c.setVersion(version);
        c.setChannel(channel);
        c.setAcceptedAt(LocalDateTime.now());
        logs.record("合规同意确认", memberId, docType + " · " + channel);
        return consents.save(c);
    }

    public List<ConsentRecord> consents(String memberId) {
        return consents.findByMemberIdOrderByAcceptedAtDesc(memberId);
    }

    /** 该会员是否已完成全部四类合规文档同意 */
    public boolean hasAllConsents(String memberId) {
        if (memberId == null || memberId.isBlank()) return false;
        List<ConsentRecord> list = consents.findByMemberIdOrderByAcceptedAtDesc(memberId);
        java.util.Set<String> done = new java.util.HashSet<>();
        for (ConsentRecord c : list) done.add(c.getDocType());
        return REQUIRED.stream().allMatch(done::contains);
    }
}
