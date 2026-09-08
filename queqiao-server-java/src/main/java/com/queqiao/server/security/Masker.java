package com.queqiao.server.security;

/**
 * 脱敏算子：集中实现各类敏感信息的掩码规则，便于审计与单测。
 * {@code full=true} 表示查看者有权查看原文（不脱敏）。
 */
public final class Masker {

    private Masker() {
    }

    public static String mask(SensitiveType type, String value, boolean full) {
        if (value == null) return null;
        if (full) return value;
        return switch (type) {
            case PHONE -> maskPhone(value);
            case EMAIL -> maskEmail(value);
            case NAME -> maskName(value);
            case ID_CARD -> maskId(value);
            case INCOME -> maskIncome(value);
            case MARRIAGE, DETAIL -> "***";
            default -> "***";
        };
    }

    /** 收入类（如「2万以上」「8000-12000」）：保留首位，其余掩码 */
    public static String maskIncome(String v) {
        if (v == null || v.isBlank()) return v;
        return v.charAt(0) + "***";
    }

    /** 手机号：保留前 3 后 4，中间 ****（兼容带分隔符/国家码的写法） */
    public static String maskPhone(String p) {
        String d = p.replaceAll("\\D", "");
        if (d.length() == 11) {
            return d.substring(0, 3) + "****" + d.substring(7);
        }
        if (p.length() <= 4) return "****";
        int keep = Math.max(1, p.length() / 4);
        return p.substring(0, keep) + "****" + p.substring(p.length() - keep);
    }

    public static String maskEmail(String e) {
        int at = e.indexOf('@');
        if (at <= 1) return "***@***";
        String u = e.substring(0, at);
        String head = u.length() <= 2 ? u.charAt(0) + "*" : u.substring(0, 2) + "***";
        return head + e.substring(at);
    }

    public static String maskName(String n) {
        if (n == null || n.length() <= 1) return n;
        if (n.length() == 2) return n.charAt(0) + "*";
        return n.charAt(0) + "**" + n.charAt(n.length() - 1);
    }

    public static String maskId(String id) {
        if (id == null || id.length() < 8) return "****";
        return id.substring(0, 4) + "********" + id.substring(id.length() - 4);
    }
}
