package com.doaccounts.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.regex.Pattern;

/**
 * 交易日期以 YYYY-MM-DD 字串保存（SD 2.1-4、ADR-06）；「今天」以台北時間為準。
 */
public final class Dates {

    public static final ZoneId ZONE = ZoneId.of("Asia/Taipei");
    private static final Pattern DATE = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");

    private Dates() {}

    public static LocalDate today() {
        return LocalDate.now(ZONE);
    }

    /** 檢查格式並回傳原字串（保證是合法日期） */
    public static String check(String s, String label) {
        if (s == null || !DATE.matcher(s).matches()) throw bad(label + "格式錯誤，需為 YYYY-MM-DD");
        try {
            LocalDate.parse(s);
        } catch (DateTimeParseException e) {
            throw bad(label + "不是合法日期：" + s);
        }
        return s;
    }

    /** 本月第一天與最後一天（台北時間） */
    public static String[] monthRange(YearMonth m) {
        return new String[]{m.atDay(1).toString(), m.atEndOfMonth().toString()};
    }

    private static ResponseStatusException bad(String m) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, m);
    }
}
