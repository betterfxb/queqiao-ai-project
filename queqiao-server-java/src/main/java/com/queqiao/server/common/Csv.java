package com.queqiao.server.common;

/**
 * CSV 单元格安全输出（P0-13 防公式注入）。
 *
 * <p>Excel / WPS / Numbers 会把以 {@code = + - @ Tab CR} 开头的单元格当作公式执行，
 * 攻击者可借会员昵称等字段构造 {@code =HYPERLINK(...)} / {@code =cmd|...} 窃取数据。
 * 这里统一在危险前缀前补单引号，并按 RFC 4180 做引号转义。</p>
 */
public final class Csv {

    private Csv() {
    }

    /** 输出安全的 CSV 单元格（已含必要的双引号包裹） */
    public static String cell(Object value) {
        if (value == null) return "";
        String s = String.valueOf(value);
        if (s.isEmpty()) return "";
        char c = s.charAt(0);
        if (c == '=' || c == '+' || c == '-' || c == '@' || c == '\t' || c == '\r') {
            s = "'" + s;
        }
        if (s.indexOf(',') >= 0 || s.indexOf('"') >= 0 || s.indexOf('\n') >= 0 || s.indexOf('\r') >= 0) {
            s = "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    /** 拼接一整行（自动换行结尾） */
    public static String row(Object... cells) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(cell(cells[i]));
        }
        return sb.append('\n').toString();
    }
}
