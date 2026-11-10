package com.doaccounts.service;

import com.doaccounts.model.Candle;
import com.doaccounts.model.CandleMonth;
import com.doaccounts.model.Security;
import com.doaccounts.repository.CandleMonthRepository;
import com.doaccounts.repository.CandleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * 台股 K 線資料（SD 4.10）：個股每月的日行情，上市查證交所 STOCK_DAY、上櫃查櫃買中心 tradingStock（免金鑰）。
 * 只在開啟 K 線圖時補抓缺少的月份：過去的月份抓過一次就保存，本月 30 分鐘後才重抓。
 * 證交所限制查詢頻率（約每 5 秒 3 次），兩次查詢之間至少間隔 1.7 秒。
 */
@Component
public class CandleService {

    private static final Logger log = LoggerFactory.getLogger(CandleService.class);

    static final String TWSE_URL = "https://www.twse.com.tw/exchangeReport/STOCK_DAY?response=json&date=%s01&stockNo=%s";
    static final String TPEX_URL = "https://www.tpex.org.tw/www/zh-tw/afterTrading/tradingStock?response=json&code=%s&date=%s/01";
    static final long GAP_MS = 1700;
    static final Duration CURRENT_MONTH_TTL = Duration.ofMinutes(30);

    private final CandleRepository candles;
    private final CandleMonthRepository months;
    private final Function<String, Map<?, ?>> fetcher;
    private final Sleeper sleeper;
    private long lastFetch;

    interface Sleeper {
        void sleep(long ms) throws InterruptedException;
    }

    @Autowired
    public CandleService(CandleRepository candles, CandleMonthRepository months) {
        this(candles, months, url -> RestClient.create().get().uri(url).retrieve().body(Map.class), Thread::sleep);
    }

    CandleService(CandleRepository candles, CandleMonthRepository months, Function<String, Map<?, ?>> fetcher, Sleeper sleeper) {
        this.candles = candles;
        this.months = months;
        this.fetcher = fetcher;
        this.sleeper = sleeper;
    }

    /** complete = false 表示有月份暫時抓不到（交易所無法連線），只回傳已有的資料 */
    public record Result(List<Candle> candles, boolean complete) {}

    public static boolean supported(Security s) {
        return "TW".equals(s.market) || "TWO".equals(s.market);
    }

    /** from（含）到今天的日 K；必要時先向交易所補抓 */
    public synchronized Result load(Security s, LocalDate from) {
        if (!supported(s)) return new Result(List.of(), true);
        LocalDate today = Dates.today();
        boolean complete = true;
        for (YearMonth m = YearMonth.from(from); !m.isAfter(YearMonth.from(today)); m = m.plusMonths(1)) {
            if (!needsFetch(s.id, m, today)) continue;
            try {
                fetchMonth(s, m);
            } catch (RuntimeException e) {
                log.warn("K 線資料 {} {} 抓取失敗：{}", s.symbol, m, e.getMessage());
                complete = false;
                break; // 交易所連不到時不再繼續打
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                complete = false;
                break;
            }
        }
        return new Result(candles.findBySecurityIdAndDateGreaterThanEqualOrderByDateAsc(s.id, from.toString()), complete);
    }

    /** 沒抓過；或本月且超過 30 分鐘；或過去的月份但上次是在該月結束前抓的（資料不完整） */
    private boolean needsFetch(String securityId, YearMonth m, LocalDate today) {
        CandleMonth cm = months.findFirstBySecurityIdAndMonth(securityId, m.toString()).orElse(null);
        if (cm == null) return true;
        if (m.equals(YearMonth.from(today))) return cm.fetchedAt.isBefore(Instant.now().minus(CURRENT_MONTH_TTL));
        Instant monthEnd = m.plusMonths(1).atDay(1).atStartOfDay(Dates.ZONE).toInstant();
        return cm.fetchedAt.isBefore(monthEnd);
    }

    private void fetchMonth(Security s, YearMonth m) throws InterruptedException {
        long wait = lastFetch + GAP_MS - System.currentTimeMillis();
        if (wait > 0) sleeper.sleep(wait);
        boolean tw = "TW".equals(s.market);
        String url = tw ? String.format(TWSE_URL, m.toString().replace("-", ""), s.symbol)
                : String.format(TPEX_URL, s.symbol, m.toString().replace("-", "/"));
        Map<?, ?> body;
        try {
            body = fetcher.apply(url);
        } finally {
            lastFetch = System.currentTimeMillis();
        }
        for (Candle c : parse(body, s.id, !tw)) {
            Candle existing = candles.findFirstBySecurityIdAndDate(s.id, c.date).orElse(null);
            if (existing != null) c.id = existing.id;
            candles.save(c);
        }
        CandleMonth cm = months.findFirstBySecurityIdAndMonth(s.id, m.toString()).orElseGet(CandleMonth::new);
        cm.securityId = s.id;
        cm.month = m.toString();
        cm.fetchedAt = Instant.now();
        months.save(cm);
    }

    /**
     * 解析每日行情。列的格式：[日期（民國 yyy/MM/dd）, 成交量, 成交金額, 開盤, 最高, 最低, 收盤, …]。
     * 證交所在 data、成交量單位為股；櫃買中心在 tables[0].data（舊版 aaData），成交量單位為千股。
     * 沒有成交的日子（價格為 --）略過。查無資料（例如該月尚未上市）回空清單。
     */
    static List<Candle> parse(Map<?, ?> body, String securityId, boolean thousandShares) {
        List<Candle> out = new ArrayList<>();
        if (body == null) throw new IllegalStateException("回應是空的");
        Object rows = body.get("data");
        if (rows == null) rows = body.get("aaData");
        if (rows == null && body.get("tables") instanceof List<?> tables && !tables.isEmpty() && tables.getFirst() instanceof Map<?, ?> t0) {
            rows = t0.get("data");
        }
        if (!(rows instanceof List<?> list)) return out;
        for (Object r : list) {
            if (!(r instanceof List<?> row) || row.size() < 7) continue;
            String date = QuoteService.rocDate(String.valueOf(row.get(0)));
            BigDecimal open = QuoteService.number(String.valueOf(row.get(3)));
            BigDecimal high = QuoteService.number(String.valueOf(row.get(4)));
            BigDecimal low = QuoteService.number(String.valueOf(row.get(5)));
            BigDecimal close = QuoteService.number(String.valueOf(row.get(6)));
            if (date == null || open == null || high == null || low == null || close == null) continue;
            Candle c = new Candle();
            c.securityId = securityId;
            c.date = date;
            c.open = open;
            c.high = high;
            c.low = low;
            c.close = close;
            BigDecimal vol = QuoteService.number(String.valueOf(row.get(1)));
            c.volume = vol == null ? 0 : (thousandShares ? vol.multiply(BigDecimal.valueOf(1000)) : vol).longValue();
            out.add(c);
        }
        return out;
    }
}
