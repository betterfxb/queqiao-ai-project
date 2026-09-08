package com.queqiao.server.security;

/** 敏感字段类型，决定脱敏算法 */
public enum SensitiveType {
    PHONE,
    EMAIL,
    NAME,
    ID_CARD,
    /** 收入等数值区间信息：保留首位，其余掩码 */
    INCOME,
    /** 婚史 / 子女 / 房车等强隐私标签：一律掩码 */
    MARRIAGE,
    /** 通用隐私标签（子女 / 房产 / 车辆等） */
    DETAIL
}
