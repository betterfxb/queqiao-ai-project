package com.queqiao.server.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** P0-1：BCrypt 口令编解码与旧 SHA-256 兼容升级 */
public class PasswordCodecTest {

    private final PasswordCodec codec = new PasswordCodec(10, "123456");

    @Test
    void bcryptEncodeThenMatch() {
        String hash = codec.encode("secret");
        assertTrue(hash.startsWith("$2a$") || hash.startsWith("$2b$"));
        assertTrue(codec.matches("u", "secret", hash));
        assertFalse(codec.matches("u", "wrong", hash));
    }

    @Test
    void needsUpgradeForNonBcrypt() {
        String legacy = PasswordCodec.legacySha256("alice", "123456");
        assertTrue(codec.needsUpgrade(legacy));
        assertFalse(codec.needsUpgrade(codec.encode("x")));
    }

    @Test
    void legacyShaStillMatches() {
        String legacy = PasswordCodec.legacySha256("alice", "123456");
        assertTrue(codec.matches("alice", "123456", legacy));
        assertFalse(codec.matches("alice", "bad", legacy));
    }

    @Test
    void seedPasswordEncryptedAndUsable() {
        String hash = codec.encodedSeedPassword();
        assertTrue(codec.matches("any", "123456", hash));
        assertFalse(codec.needsUpgrade(hash));
    }
}
