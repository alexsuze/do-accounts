package com.doaccounts.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 支援的幣別與小數位數（SD 4.3）。前端 utils/money.js 的 CURRENCIES 須與此一致。
 */
public final class Currencies {

    private static final Map<String, Integer> DIGITS = Map.of("TWD", 0, "JPY", 0, "USD", 2);
    /** 金額上限（絕對值），擋掉打錯的天文數字 */
    private static final BigDecimal MAX = new BigDecimal("1000000000000");

    private Currencies() {}

    public static boolean supported(String code) {
        return code != null && DIGITS.containsKey(code);
    }

    public static int digits(String code) {
        Integer d = DIGITS.get(code);
        if (d == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不支援的幣別：" + code);
        return d;
    }

    /**
     * 檢查金額：非 null、小數位數不超過幣別規定、絕對值在上限內。
     * @param positive true 時必須大於 0
     */
    public static BigDecimal check(BigDecimal v, String currency, boolean positive, String label) {
        if (v == null) throw bad(label + "必填");
        if (positive && v.signum() <= 0) throw bad(label + "必須大於 0");
        if (v.abs().compareTo(MAX) >= 0) throw bad(label + "太大");
        int digits = digits(currency);
        if (Math.max(0, v.stripTrailingZeros().scale()) > digits) {
            throw bad(digits == 0 ? label + "（" + currency + "）不能有小數" : label + "（" + currency + "）最多 " + digits + " 位小數");
        }
        return v.stripTrailingZeros().scale() < 0 ? v.setScale(0) : v.stripTrailingZeros();
    }

    private static ResponseStatusException bad(String m) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, m);
    }
}
