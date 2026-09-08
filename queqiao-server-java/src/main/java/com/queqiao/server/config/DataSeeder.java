package com.queqiao.server.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.CollectionType;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.queqiao.server.entity.*;
import com.queqiao.server.repo.*;
import com.queqiao.server.entity.QuizTemplate;
import com.queqiao.server.repo.QuizTemplateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.util.List;

/**
 * 种子数据初始化：库为空时从 classpath:seed/*.json 载入演示数据。
 * 已有数据则跳过，避免每次重启覆盖用户操作结果。
 */
@Component
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private final PasswordCodec passwords;

    private final SysUserRepository users;
    private final MemberRepository members;
    private final AgentRepository agents;
    private final PairRepository pairs;
    private final AuditTaskRepository audits;
    private final ActivityRepository activities;
    private final ActivitySignupRepository signups;
    private final FinanceOrderRepository orders;
    private final ReportRecordRepository reports;
    private final CrmLeadRepository leads;
    private final CrmFollowRepository follows;
    private final ConversationRepository conversations;
    private final MessageItemRepository messages;
    private final CalendarEventRepository events;
    private final SettingItemRepository settings;
    private final AutomationRuleRepository rules;
    private final ServiceProductRepository products;
    private final DocTemplateRepository docTemplates;
    private final MarketChannelRepository marketChannels;
    private final PartnerMerchantRepository merchants;
    private final CommunityTierRepository communityTiers;
    private final CityPartnerRepository cityPartners;
    private final QuizTemplateRepository quizTemplates;

    public DataSeeder(SysUserRepository users, MemberRepository members, AgentRepository agents,
                      PairRepository pairs, AuditTaskRepository audits, ActivityRepository activities,
                      ActivitySignupRepository signups, FinanceOrderRepository orders,
                      ReportRecordRepository reports, CrmLeadRepository leads, CrmFollowRepository follows,
                      ConversationRepository conversations, MessageItemRepository messages,
                      CalendarEventRepository events, SettingItemRepository settings, AutomationRuleRepository rules,
                      ServiceProductRepository products, DocTemplateRepository docTemplates,
                      MarketChannelRepository marketChannels, PartnerMerchantRepository merchants,
                      CommunityTierRepository communityTiers, CityPartnerRepository cityPartners,
                      QuizTemplateRepository quizTemplates, PasswordCodec passwords) {
        this.passwords = passwords;
        this.marketChannels = marketChannels;
        this.merchants = merchants;
        this.communityTiers = communityTiers;
        this.cityPartners = cityPartners;
        this.quizTemplates = quizTemplates;
        this.users = users;
        this.members = members;
        this.agents = agents;
        this.pairs = pairs;
        this.audits = audits;
        this.activities = activities;
        this.signups = signups;
        this.orders = orders;
        this.reports = reports;
        this.leads = leads;
        this.follows = follows;
        this.conversations = conversations;
        this.messages = messages;
        this.events = events;
        this.settings = settings;
        this.rules = rules;
        this.products = products;
        this.docTemplates = docTemplates;
    }

    @Override
    @Transactional
    public void run(String... args) {
        seedUsers();
        seed(agents, "agent", Agent.class);
        seed(members, "member", Member.class);
        seed(pairs, "pair", Pair.class);
        seed(audits, "audit_task", AuditTask.class);
        seed(activities, "activity", Activity.class);
        seed(signups, "activity_signup", ActivitySignup.class);
        seed(orders, "finance_order", FinanceOrder.class);
        seed(reports, "report_record", ReportRecord.class);
        seed(leads, "crm_lead", CrmLead.class);
        seed(follows, "crm_follow", CrmFollow.class);
        seed(conversations, "conversation", Conversation.class);
        seed(messages, "message", MessageItem.class);
        seed(events, "calendar_event", CalendarEvent.class);
        seed(settings, "setting", SettingItem.class);
        seed(rules, "automation_rule", AutomationRule.class);
        seed(products, "service_product", ServiceProduct.class);
        seed(docTemplates, "doc_template", DocTemplate.class);
        // #111 增长与差异化
        seed(marketChannels, "market_channel", MarketChannel.class);
        seed(merchants, "partner_merchant", PartnerMerchant.class);
        seed(communityTiers, "community_tier", CommunityTier.class);
        seed(cityPartners, "city_partner", CityPartner.class);
        // #111 1-B 情感性格测评模板
        seed(quizTemplates, "quiz_template", QuizTemplate.class);
        log.info("[seed] 数据初始化完成：会员 {} / 红娘 {} / 配对 {} / 活动 {} / 订单 {} / 产品 {} "
                        + "/ 渠道 {} / 商户 {} / 社群 {} / 城市合伙人 {}",
                members.count(), agents.count(), pairs.count(), activities.count(), orders.count(),
                products.count(), marketChannels.count(), merchants.count(),
                communityTiers.count(), cityPartners.count());
    }

    /**
     * 系统账号种子：口令不落库明文、也不使用可彩虹表化的 SHA-256。
     * 初始口令来自 {@code queqiao.seed-password}（环境变量 QQ_SEED_PASSWORD 覆盖），
     * 落库前统一 BCrypt 加密；生产环境必须覆盖该值并在首次登录后修改。
     */
    private void seedUsers() {
        try {
            if (users.count() > 0) return;
            ClassPathResource res = new ClassPathResource("seed/sys_user.json");
            if (!res.exists()) return;
            try (InputStream in = res.getInputStream()) {
                CollectionType type = mapper.getTypeFactory().constructCollectionType(List.class, SysUser.class);
                List<SysUser> list = mapper.readValue(in, type);
                for (SysUser u : list) {
                    u.setPassword(passwords.encodedSeedPassword());
                }
                users.saveAll(list);
            }
            log.warn("[seed] 已初始化 {} 个系统账号，初始口令取自 queqiao.seed-password，请立即修改", users.count());
        } catch (Exception e) {
            log.error("[seed] 载入 sys_user 失败: {}", e.getMessage(), e);
        }
    }

    private <T, R extends org.springframework.data.jpa.repository.JpaRepository<T, ?>> void seed(
            R repo, String file, Class<T> cls) {
        try {
            if (repo.count() > 0) return;
            ClassPathResource res = new ClassPathResource("seed/" + file + ".json");
            if (!res.exists()) return;
            try (InputStream in = res.getInputStream()) {
                CollectionType type = mapper.getTypeFactory().constructCollectionType(List.class, cls);
                List<T> list = mapper.readValue(in, type);
                repo.saveAll(list);
            }
        } catch (Exception e) {
            log.error("[seed] 载入 {} 失败: {}", file, e.getMessage(), e);
        }
    }
}
