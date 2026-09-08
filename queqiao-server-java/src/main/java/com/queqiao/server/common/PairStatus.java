package com.queqiao.server.common;

/**
 * 牵线状态机：
 *   待确认 → 牵线中 → 双方同意 → 已约见 → 已成功
 *                        └──→ 已终止（任意非终态均可终止）
 * 已成功 / 已终止 为终态。非法流转由 {@link #canTransitTo(PairStatus)} 拒绝。
 */
public enum PairStatus {

    待确认,
    牵线中,
    进行中,
    双方同意,
    已约见,
    已成功,
    已终止;

    public boolean isTerminal() {
        return this == 已成功 || this == 已终止;
    }

    public boolean canTransitTo(PairStatus to) {
        if (to == null) return false;
        if (this == to) return true;
        return switch (this) {
            case 待确认 -> to == 牵线中 || to == 双方同意 || to == 已终止;
            case 牵线中, 进行中 -> to == 双方同意 || to == 已约见 || to == 已终止;
            case 双方同意 -> to == 已约见 || to == 已终止;
            case 已约见 -> to == 已成功 || to == 已终止;
            case 已成功, 已终止 -> false;
        };
    }

    public static PairStatus parse(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return PairStatus.valueOf(s.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
