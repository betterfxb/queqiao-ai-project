package com.queqiao.server.service;

import com.queqiao.server.common.Ids;
import com.queqiao.server.entity.FinanceOrder;
import com.queqiao.server.entity.RenewalReminder;
import com.queqiao.server.repo.FinanceOrderRepository;
import com.queqiao.server.repo.RenewalReminderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 1-G 续费一致性：续费提醒直接引用真实交易订单（FinanceOrder），
 * 到期日由订单推导，与 finance 同源；续费成功后自动关闭对应提醒。
 */
@Service
public class RenewalService {

    private static final int RENEWAL_DAYS = 15;

    private final FinanceOrderRepository orderRepo;
    private final RenewalReminderRepository reminderRepo;
    private final SettingService settings;

    public RenewalService(FinanceOrderRepository orderRepo, RenewalReminderRepository reminderRepo,
                         SettingService settings) {
        this.orderRepo = orderRepo;
        this.reminderRepo = reminderRepo;
        this.settings = settings;
    }

    /**
     * 返回当前窗口内、状态为 open 的续费提醒（与 finance 同源）。
     * 调用时会确保符合条件的真实订单都已生成提醒（幂等：按 orderId 去重）。
     */
    @Transactional
    public List<RenewalReminder> listOpen() {
        int renewalDays = settings.getInt("biz.renewalDays", RENEWAL_DAYS);
        LocalDate today = LocalDate.now();
        LocalDate horizon = today.plusDays(renewalDays);

        for (FinanceOrder o : orderRepo.findAll()) {
            if (!"已完成".equals(o.getStatus())) continue;
            if (!List.of("会员续费", "牵线服务").contains(nz(o.getType()))) continue;
            LocalDate exp = deriveExpire(o);
            if (exp == null) continue;
            if (exp.isBefore(today) || exp.isAfter(horizon)) continue;
            if (reminderRepo.findByOrderId(o.getId()).isPresent()) continue;
            RenewalReminder r = new RenewalReminder();
            r.setId(Ids.biz("RR"));
            r.setOrderId(o.getId());
            r.setMemberId(o.getMemberId());
            r.setMemberName(o.getMember());
            r.setAgent(o.getAgent());
            r.setExpireDate(exp);
            r.setStatus("open");
            r.setCreatedAt(LocalDateTime.now());
            reminderRepo.save(r);
        }

        final LocalDate t = today, h = horizon;
        return reminderRepo.findByStatus("open").stream()
                .filter(r -> !r.getExpireDate().isBefore(t) && !r.getExpireDate().isAfter(h))
                .collect(Collectors.toList());
    }

    /** 续费成功后关闭对应订单的提醒（幂等：已关闭则跳过） */
    @Transactional
    public void closeReminder(String orderId) {
        reminderRepo.findByOrderId(orderId).ifPresent(r -> {
            if ("open".equals(r.getStatus())) {
                r.setStatus("closed");
                r.setClosedAt(LocalDateTime.now());
                reminderRepo.save(r);
            }
        });
    }

    private LocalDate deriveExpire(FinanceOrder o) {
        if (o.getExpireDate() != null) return o.getExpireDate();
        if (o.getPaidAt() != null) return o.getPaidAt().toLocalDate().plusYears(1);
        if (o.getCreatedAt() != null) return o.getCreatedAt().toLocalDate().plusYears(1);
        return null;
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
