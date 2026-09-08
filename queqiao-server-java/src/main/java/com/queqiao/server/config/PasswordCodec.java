package com.queqiao.server.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * 口令编解码器（P0-1）。
 *
 * <p>存储一律使用 BCrypt（自带随机盐 + 可调 cost），不再使用可彩虹表化的 SHA-256。
 * 为兼容历史库中的 SHA-256 口令，{@link #matches} 保留旧算法校验；命中旧算法时
 * {@link #needsUpgrade} 返回 true，调用方应在登录成功后静默改写为 BCrypt。</p>
 */
@Component
public class PasswordCodec {

    /** BCrypt 强度，默认 10；高并发场景可通过配置下调 */
    private final PasswordEncoder encoder;

    /** 种子/新建账号的初始口令，生产必须通过环境变量 QQ_SEED_PASSWORD 覆盖 */
    private final String seedPassword;

    public PasswordCodec(@Value("${queqiao.bcrypt-strength:10}") int strength,
                         @Value("${queqiao.seed-password:123456}") String seedPassword) {
        this.encoder = new BCryptPasswordEncoder(strength);
        this.seedPassword = seedPassword;
    }

    /** BCrypt 加密 */
    public String encode(String raw) {
        if (raw == null || raw.isEmpty()) throw new IllegalArgumentException("口令不能为空");
        return encoder.encode(raw);
    }

    /** 新建账号 / 种子数据的初始口令（明文） */
    public String seedPassword() {
        return seedPassword;
    }

    /** 新建账号 / 种子数据的初始口令（BCrypt 密文） */
    public String encodedSeedPassword() {
        return encode(seedPassword);
    }

    /** 是否为旧版 SHA-256 口令（需要在下次登录成功后升级） */
    public boolean needsUpgrade(String stored) {
        return stored != null && !stored.isBlank() && !isBcrypt(stored);
    }

    /**
     * 校验口令：优先 BCrypt；旧库中的 SHA-256 走兼容分支。
     *
     * @param username 旧算法的盐依赖用户名，故必须传入
     */
    public boolean matches(String username, String raw, String stored) {
        if (raw == null || raw.isEmpty() || stored == null || stored.isBlank()) return false;
        if (isBcrypt(stored)) {
            return encoder.matches(raw, stored);
        }
        return constantTimeEquals(legacySha256(username, raw), stored);
    }

    private static boolean isBcrypt(String stored) {
        return stored.startsWith("$2a$") || stored.startsWith("$2b$") || stored.startsWith("$2y$");
    }

    /** 历史算法：SHA-256("qq$" + username + "$" + raw)，仅用于兼容校验，禁止用于新口令写入 */
    static String legacySha256(String username, String raw) {
        try {
            var md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest(("qq$" + username + "$" + raw).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) return false;
        byte[] ba = a.getBytes(StandardCharsets.UTF_8);
        byte[] bb = b.getBytes(StandardCharsets.UTF_8);
        if (ba.length != bb.length) return false;
        int r = 0;
        for (int i = 0; i < ba.length; i++) r |= ba[i] ^ bb[i];
        return r == 0;
    }
}
