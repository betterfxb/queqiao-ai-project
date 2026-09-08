package com.queqiao.server.service;

import com.queqiao.server.config.SecurityFilter;
import com.queqiao.server.entity.OperationLog;
import com.queqiao.server.repo.OperationLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.List;

/** 操作日志：所有写操作统一记录，供设置页「操作日志」展示与审计追溯 */
@Service
public class LogService {

    private static final Logger LOG = LoggerFactory.getLogger(LogService.class);

    private final OperationLogRepository repo;

    public LogService(OperationLogRepository repo) {
        this.repo = repo;
    }

    public void record(String action, String target, String detail) {
        try {
            OperationLog log = new OperationLog();
            log.setAction(action);
            log.setTarget(target);
            log.setDetail(detail);
            log.setCreatedAt(LocalDateTime.now());
            HttpServletRequest req = currentRequest();
            if (req != null) {
                log.setUserId((String) req.getAttribute(SecurityFilter.ATTR_USER_ID));
                log.setUserName((String) req.getAttribute(SecurityFilter.ATTR_USER_NAME));
                log.setIp(clientIp(req));
            }
            repo.save(log);
        } catch (Exception e) {
            LOG.warn("[log] 记录失败: {}", e.getMessage());
        }
    }

    public List<OperationLog> recent() {
        return repo.findTop100ByOrderByCreatedAtDesc();
    }

    /** 按操作对象过滤（会员档案变更时间轴等场景） */
    public List<OperationLog> byTarget(String target) {
        return repo.findTop100ByTargetOrderByCreatedAtDesc(target);
    }

    /** 当前登录用户名（无上下文时返回「系统」） */
    public String currentUserName() {
        HttpServletRequest req = currentRequest();
        if (req == null) return "系统";
        Object n = req.getAttribute(SecurityFilter.ATTR_USER_NAME);
        return n == null ? "系统" : String.valueOf(n);
    }

    private HttpServletRequest currentRequest() {
        var attrs = RequestContextHolder.getRequestAttributes();
        return attrs instanceof ServletRequestAttributes sra ? sra.getRequest() : null;
    }

    private String clientIp(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
        return req.getRemoteAddr();
    }
}
