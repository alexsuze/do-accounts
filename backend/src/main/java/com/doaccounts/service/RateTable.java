package com.doaccounts.service;

import com.doaccounts.model.ExchangeRate;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * 一段期間的匯率，在記憶體中查找（SD 4.4）。報表一次載入，避免逐筆查資料庫。
 * 資料庫存「1 幣別 = ? TWD」；換算成本位幣時以 TWD 為中介：rate(c → base) = twd(c) / twd(base)。
 * 查某天的匯率：取該天或之前最近一天；更早都沒有時取最早的一筆（剛上線時歷史交易仍能換算）。
 * 同一天有手動值時，手動值優先。
 */
public final class RateTable {

    /** 換算結果保存的小數位數（SD 4.3） */
    public static final int SCALE = 4;
    private static final int RATE_SCALE = 10;

    private final String base;
    private final Map<String, TreeMap<String, ExchangeRate>> byCurrency = new HashMap<>();

    /** system 與 manual 都是「1 currency = rate TWD」 */
    public RateTable(String base, Collection<ExchangeRate> system, Collection<ExchangeRate> manual) {
        this.base = base;
        for (ExchangeRate r : system) put(r);
        for (ExchangeRate r : manual) put(r); // 後放的手動值覆蓋同一天的系統值
    }

    private void put(ExchangeRate r) {
        if (r.rate == null || r.rate.signum() <= 0) return;
        byCurrency.computeIfAbsent(r.currency, k -> new TreeMap<>()).put(r.date, r);
    }

    public String base() {
        return base;
    }

    /** 該天使用的匯率紀錄（1 currency = ? TWD）；TWD 本身或完全沒有資料時回 null */
    public ExchangeRate record(String currency, String date) {
        TreeMap<String, ExchangeRate> m = byCurrency.get(currency);
        if (m == null || m.isEmpty()) return null;
        Map.Entry<String, ExchangeRate> e = m.floorEntry(date);
        return (e != null ? e : m.firstEntry()).getValue();
    }

    private BigDecimal toPivot(String currency, String date) {
        if (Currencies.PIVOT.equals(currency)) return BigDecimal.ONE;
        ExchangeRate r = record(currency, date);
        if (r == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "缺少 " + currency + " 匯率，請到設定頁手動輸入");
        }
        return r.rate;
    }

    /** 1 currency = ? 本位幣 */
    public BigDecimal rate(String currency, String date) {
        if (base.equals(currency)) return BigDecimal.ONE;
        BigDecimal r = toPivot(currency, date);
        if (Currencies.PIVOT.equals(base)) return r;
        return r.divide(toPivot(base, date), RATE_SCALE, RoundingMode.HALF_UP);
    }

    /** 換算成本位幣，保留 4 位小數；顯示前再依本位幣位數四捨五入 */
    public BigDecimal toBase(BigDecimal amount, String currency, String date) {
        if (base.equals(currency)) return amount;
        return amount.multiply(rate(currency, date)).setScale(SCALE, RoundingMode.HALF_UP);
    }
}
