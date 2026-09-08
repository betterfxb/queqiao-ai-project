package com.queqiao.server.security;

/**
 * 敏感字段的数据归属范围，决定「非管理员」是否可见原文：
 *  - MEMBER：会员字段，红娘仅对自己负责的会员可见原文（数据范围已保证只能取到本人会员）；
 *  - STAFF ：员工字段，仅管理员可见原文，红娘之间互不暴露联系方式（个保法最小必要）；
 *  - ANY   ：一律仅管理员可见。
 */
public enum SensitiveScope {
    MEMBER,
    STAFF,
    ANY
}
