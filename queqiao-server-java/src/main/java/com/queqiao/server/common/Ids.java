package com.queqiao.server.common;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

/** 业务编号生成器（与前端原型的编号风格保持一致，如 M2024088 / P20240815 / ORD202408150001） */
public final class Ids {

    /**
     * 序号初值随机化：避免服务重启后 SEQ 重新从固定值计数，
     * 与「秒级时间戳」叠加时仍可能产生的 id 冲突（H2 文件库跨重启持久化场景）。
     */
    private static final AtomicInteger SEQ = new AtomicInteger(new Random().nextInt(Integer.MAX_VALUE / 2));
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private Ids() {
    }

    private static int next() {
        return SEQ.incrementAndGet();
    }

    /** 会员编号 M + 年 + 3 位序号 */
    public static String member() {
        return "M" + LocalDate.now().getYear() + String.format("%03d", next() % 1000);
    }

    /** 通用：前缀 + 精确到秒的时间戳 + 4 位序号。
     *  时间戳含「秒」可避免服务重启后序号归零导致的 id 冲突（H2 文件库持久化场景下尤为关键）；
     *  同一秒内最多 1e4 个，远超业务峰值。 */
    public static String biz(String prefix) {
        return prefix + LocalDateTime.now().format(TS) + String.format("%04d", next() % 10000);
    }

    /** 高精度唯一串 */
    public static String uid(String prefix) {
        return prefix + LocalDateTime.now().format(TS) + next();
    }
}
