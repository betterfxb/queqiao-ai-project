package com.queqiao.server.service;

import com.queqiao.server.entity.SettingItem;
import com.queqiao.server.repo.SettingItemRepository;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 系统设置读取服务（带缓存的 typed getter）。
 * 业务方（匹配阈值 / AI 开关 / 线索沉睡天数 / 会话时长 等）统一从此读取，
 * 让「系统设置」从"存了不生效"变为真正驱动业务。保存后由 SystemController 调用 evictAll() 失效缓存。
 */
@Service
public class SettingService {

    private final SettingItemRepository repo;
    private final Map<String, String> cache = new ConcurrentHashMap<>();
    private volatile boolean loaded = false;

    public SettingService(SettingItemRepository repo) {
        this.repo = repo;
    }

    private void ensure() {
        if (loaded) return;
        synchronized (this) {
            if (loaded) return;
            cache.clear();
            for (SettingItem s : repo.findAll()) cache.put(s.getId(), s.getValue());
            loaded = true;
        }
    }

    /** 读取设置值，缺失时返回默认值。 */
    public String get(String key, String def) {
        ensure();
        String v = cache.get(key);
        return v == null ? def : v;
    }

    public int getInt(String key, int def) {
        String v = get(key, null);
        if (v == null) return def;
        try {
            return Integer.parseInt(v.trim());
        } catch (Exception e) {
            return def;
        }
    }

    public boolean getBool(String key, boolean def) {
        String v = get(key, null);
        if (v == null) return def;
        v = v.trim();
        return "true".equalsIgnoreCase(v) || "1".equals(v) || "yes".equalsIgnoreCase(v);
    }

    /** 设置保存/新增后调用：失效缓存，下次读取自动回源。 */
    public void evictAll() {
        cache.clear();
        loaded = false;
    }
}
