package com.queqiao.server.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** P0-13：CSV 公式注入防护 */
public class CsvTest {

    @Test
    void dangerousPrefixIsQuoted() {
        assertEquals("'=cmd", Csv.cell("=cmd"));
        assertEquals("'+1", Csv.cell("+1"));
        assertEquals("'-1", Csv.cell("-1"));
        assertEquals("'@x", Csv.cell("@x"));
    }

    @Test
    void commaIsRfc4180Quoted() {
        assertEquals("\"a,b\"", Csv.cell("a,b"));
        assertEquals("\"a\"\"b\"", Csv.cell("a\"b"));
    }

    @Test
    void safeValueUntouched() {
        assertEquals("hello", Csv.cell("hello"));
        assertEquals("123", Csv.cell(123));
        assertEquals("", Csv.cell(null));
    }
}
