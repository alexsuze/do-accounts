package com.doaccounts.service;

import com.doaccounts.model.Price;
import com.doaccounts.model.Security;
import com.doaccounts.repository.PriceRepository;
import com.doaccounts.repository.SecurityRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * 台股報價（SD 4.5「報價來源」）：TWSE（上市）與 TPEx（上櫃）OpenAPI 的每日收盤行情，免金鑰。
 * 同一份清單也用來以代號查名稱（新增證券時自動帶出）。清單在記憶體快取 6 小時。
 * 排程：週一到週五 14:30、18:30（台北）更新所有台股證券的收盤價。美股、基金等沒有免費來源，手動輸入。
 */
@Component
public class QuoteService {

    private static final Logger log = LoggerFactory.getLogger(QuoteService.class);

    static final String TWSE_URL = "https://openapi.twse.com.tw/v1/exchangeReport/STOCK_DAY_ALL";
    static final String TPEX_URL = "https://www.tpex.org.tw/openapi/v1/tpex_mainboard_quotes";
    private static final long CACHE_MS = 6 * 3600_000L;

    /** 一筆行情：名稱、收盤價（可能沒有成交而為 null）、資料日期 */
    public record Quote(String market, String symbol, String name, BigDecimal close, String date) {}

    private final SecurityRepository securities;
    private final PriceRepository prices;
    private final Function<String, List<Map<String, Object>>> fetcher;
    private final Map<String, Map<String, Quote>> cache = new HashMap<>();
    private final Map<String, Long> cachedAt = new HashMap<>();

    @Autowired
    public QuoteService(SecurityRepository securities, PriceRepository prices) {
        this(securities, prices, url -> RestClient.create().get().uri(url).retrieve()
                .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {}));
    }

    QuoteService(SecurityRepository securities, PriceRepository prices, Function<String, List<Map<String, Object>>> fetcher) {
        this.securities = securities;
        this.prices = prices;
        this.fetcher = fetcher;
    }

    /** 以代號查台股（TW 或 TWO）；查不到或來源無法連線時回 null */
    public Quote lookup(String market, String symbol) {
        try {
            return directory(market).get(symbol);
        } catch (RuntimeException e) {
            log.warn("台股清單（{}）讀取失敗：{}", market, e.getMessage());
            return null;
        }
    }

    public record Result(int updated, int skipped, String message) {}

    @Scheduled(cron = "0 30 14,18 * * MON-FRI", zone = "Asia/Taipei")
    public void scheduled() {
        refresh(true);
    }

    /** 更新所有台股證券的收盤價；force = false 時沿用快取的清單 */
    public synchronized Result refresh(boolean force) {
        if (force) cache.clear();
        int updated = 0;
        int skipped = 0;
        StringBuilder errors = new StringBuilder();
        for (String market : List.of("TW", "TWO")) {
            Map<String, Quote> dir;
            try {
                dir = directory(market);
            } catch (RuntimeException e) {
                errors.append(market).append(" 來源無法連線；");
                log.warn("報價來源 {} 失敗：{}", market, e.getMessage());
                continue;
            }
            for (Security s : securities.findAll()) {
                if (!market.equals(s.market)) continue;
                Quote q = dir.get(s.symbol);
                if (q == null || q.close() == null) {
                    skipped++;
                    continue;
                }
                upsert(s.id, q.date(), q.close(), "TW".equals(market) ? Price.TWSE : Price.TPEX);
                updated++;
            }
        }
        String msg = errors.isEmpty() ? "已更新 " + updated + " 檔報價" : errors + "已更新 " + updated + " 檔";
        log.info("台股報價：{}", msg);
        return new Result(updated, skipped, msg);
    }

    private void upsert(String securityId, String date, BigDecimal close, String source) {
        Price p = prices.findFirstByUserIdIsNullAndSecurityIdAndDate(securityId, date).orElseGet(Price::new);
        p.securityId = securityId;
        p.date = date;
        p.close = close;
        p.source = source;
        p.userId = null;
        p.updatedAt = Instant.now();
        prices.save(p);
    }

    private synchronized Map<String, Quote> directory(String market) {
        Long at = cachedAt.get(market);
        if (at != null && System.currentTimeMillis() - at < CACHE_MS && cache.containsKey(market)) return cache.get(market);
        List<Map<String, Object>> rows = fetcher.apply("TW".equals(market) ? TWSE_URL : TPEX_URL);
        Map<String, Quote> dir = parse(market, rows, Dates.today());
        if (dir.isEmpty()) throw new IllegalStateException("清單是空的");
        cache.put(market, dir);
        cachedAt.put(market, System.currentTimeMillis());
        return dir;
    }

    /**
     * 解析 TWSE（Code、Name、ClosingPrice、Date）或 TPEx（SecuritiesCompanyCode、CompanyName、Close、Date）的資料。
     * Date 為民國年 yyyMMdd；沒有 Date 欄時用最近的平日（假日時抓到的是前一個交易日的資料）。
     */
    static Map<String, Quote> parse(String market, List<Map<String, Object>> rows, LocalDate today) {
        Map<String, Quote> out = new HashMap<>();
        if (rows == null) return out;
        String fallback = lastWeekday(today).toString();
        for (Map<String, Object> r : rows) {
            String code = str(r.get("Code"), r.get("SecuritiesCompanyCode"));
            String name = str(r.get("Name"), r.get("CompanyName"));
            if (code == null || name == null) continue;
            String date = rocDate(str(r.get("Date"), null));
            out.put(code.trim(), new Quote(market, code.trim(), name.trim(), number(str(r.get("ClosingPrice"), r.get("Close"))),
                    date == null ? fallback : date));
        }
        return out;
    }

    static LocalDate lastWeekday(LocalDate d) {
        while (d.getDayOfWeek() == DayOfWeek.SATURDAY || d.getDayOfWeek() == DayOfWeek.SUNDAY) d = d.minusDays(1);
        return d;
    }

    /** 1151107 → 2026-11-07；也接受 2026-11-07、20261107 */
    static String rocDate(String s) {
        if (s == null) return null;
        String v = s.trim().replace("/", "").replace("-", "");
        try {
            if (v.length() == 7) return LocalDate.of(Integer.parseInt(v.substring(0, 3)) + 1911, Integer.parseInt(v.substring(3, 5)), Integer.parseInt(v.substring(5))).toString();
            if (v.length() == 8) return LocalDate.of(Integer.parseInt(v.substring(0, 4)), Integer.parseInt(v.substring(4, 6)), Integer.parseInt(v.substring(6))).toString();
        } catch (RuntimeException e) {
            return null;
        }
        return null;
    }

    /** "1,085.00" → 1085.00；"--"、空白 → null（當天沒有成交） */
    static BigDecimal number(String s) {
        if (s == null) return null;
        String v = s.replace(",", "").trim();
        try {
            BigDecimal b = new BigDecimal(v);
            return b.signum() > 0 ? b : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String str(Object a, Object b) {
        Object v = a != null ? a : b;
        return v == null ? null : v.toString();
    }
}
