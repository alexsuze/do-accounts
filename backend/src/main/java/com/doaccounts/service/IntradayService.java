package com.doaccounts.service;

import com.doaccounts.model.IntradayQuote;
import com.doaccounts.model.IntradayStatus;
import com.doaccounts.model.Security;
import com.doaccounts.repository.IntradayQuoteRepository;
import com.doaccounts.repository.IntradayStatusRepository;
import com.doaccounts.repository.PriceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 台股盤中價（SD 4.11）：證交所「基本市況報導」網站的即時成交價（約 20 秒延遲，非正式公開 API）。
 * 只在使用者按「更新報價」時抓；平日 09:00 以後、當天正式收盤價還沒進來前才抓。
 * 抓不到時：當天抓過就沿用並記錄失敗時間；沒抓過就維持收盤價。某檔剛好沒有成交（價格為 -）不算失敗。
 */
@Component
public class IntradayService {

    private static final Logger log = LoggerFactory.getLogger(IntradayService.class);

    static final String MIS_URL = "https://mis.twse.com.tw/stock/api/getStockInfo.jsp?json=1&delay=0&ex_ch=%s";
    static final LocalTime OPEN = LocalTime.of(9, 0);
    /** 一次查詢最多幾檔 */
    static final int BATCH = 50;
    private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("HH:mm");

    private final IntradayQuoteRepository quotes;
    private final IntradayStatusRepository statuses;
    private final PriceRepository prices;
    private final Function<String, Map<?, ?>> fetcher;
    private final Supplier<ZonedDateTime> clock;

    @Autowired
    public IntradayService(IntradayQuoteRepository quotes, IntradayStatusRepository statuses, PriceRepository prices) {
        this(quotes, statuses, prices, url -> RestClient.create().get().uri(url).retrieve().body(Map.class),
                () -> ZonedDateTime.now(Dates.ZONE));
    }

    IntradayService(IntradayQuoteRepository quotes, IntradayStatusRepository statuses, PriceRepository prices,
                    Function<String, Map<?, ?>> fetcher, Supplier<ZonedDateTime> clock) {
        this.quotes = quotes;
        this.statuses = statuses;
        this.prices = prices;
        this.fetcher = fetcher;
        this.clock = clock;
    }

    /**
     * @param attempted 是否有去抓（沒開盤、或今天的收盤價都已公布時不抓）
     * @param failed    證交所連不到或回應格式不對
     */
    public record Result(boolean attempted, int updated, int noTrade, boolean failed, String message) {}

    /** 是否在可以抓盤中價的時間（平日 09:00 以後；收盤後到正式收盤價公布前也算，此時抓到的是最後成交價） */
    public boolean marketDay() {
        ZonedDateTime now = clock.get();
        DayOfWeek d = now.getDayOfWeek();
        return d != DayOfWeek.SATURDAY && d != DayOfWeek.SUNDAY && !now.toLocalTime().isBefore(OPEN);
    }

    public String today() {
        return clock.get().toLocalDate().toString();
    }

    public synchronized Result refresh(Collection<Security> all) {
        if (!marketDay()) return new Result(false, 0, 0, false, "還沒開盤，顯示收盤價");
        String today = today();
        // 今天的正式收盤價已經進來的就不用抓了
        List<Security> list = all.stream().filter(CandleService::supported)
                .filter(s -> prices.findFirstByUserIdIsNullAndSecurityIdAndDate(s.id, today).isEmpty()).toList();
        if (list.isEmpty()) return new Result(false, 0, 0, false, "今天的收盤價已公布");

        String now = clock.get().format(HM);
        int updated = 0;
        int noTrade = 0;
        try {
            for (int i = 0; i < list.size(); i += BATCH) {
                List<Security> batch = list.subList(i, Math.min(i + BATCH, list.size()));
                Map<String, Security> byKey = new HashMap<>();
                List<String> keys = new ArrayList<>();
                for (Security s : batch) {
                    String key = ("TW".equals(s.market) ? "tse_" : "otc_") + s.symbol + ".tw";
                    keys.add(key);
                    byKey.put(s.symbol, s);
                }
                Map<?, ?> body = fetcher.apply(String.format(MIS_URL, String.join("|", keys)));
                if (body == null || !(body.get("msgArray") instanceof List<?> rows)) throw new IllegalStateException("回應格式不對");
                java.util.Set<String> seen = new java.util.HashSet<>();
                for (Object o : rows) {
                    if (!(o instanceof Map<?, ?> r)) continue;
                    Security s = byKey.get(String.valueOf(r.get("c")));
                    if (s == null) continue;
                    seen.add(s.id);
                    Tick t = parse(r, today);
                    if (t == null) {
                        noTrade++;
                        continue;
                    }
                    IntradayQuote q = quotes.findFirstBySecurityIdAndDate(s.id, today).orElseGet(IntradayQuote::new);
                    q.securityId = s.id;
                    q.date = today;
                    q.price = t.price();
                    q.time = t.time();
                    q.fetchedAt = Instant.now();
                    quotes.save(q);
                    updated++;
                }
                noTrade += (int) batch.stream().filter(s -> !seen.contains(s.id)).count();
            }
        } catch (RuntimeException e) {
            log.warn("盤中價抓取失敗：{}", e.getMessage());
            IntradayStatus st = status(today);
            st.lastFailure = now;
            statuses.save(st);
            return new Result(true, updated, noTrade, true, failureMessage(st));
        }
        IntradayStatus st = status(today);
        st.lastSuccess = now;
        st.lastFailure = null;
        statuses.save(st);
        String msg = "已更新 " + updated + " 檔盤中價（" + now + "）" + (noTrade > 0 ? "；" + noTrade + " 檔暫時沒有成交，沿用先前的價格" : "");
        return new Result(true, updated, noTrade, false, msg);
    }

    /** 某天的盤中價（securityId → 報價） */
    public Map<String, IntradayQuote> quotes(Collection<String> securityIds, String date) {
        Map<String, IntradayQuote> out = new HashMap<>();
        if (securityIds.isEmpty()) return out;
        for (IntradayQuote q : quotes.findBySecurityIdInAndDate(securityIds, date)) out.put(q.securityId, q);
        return out;
    }

    /** 今天最後一次更新失敗、而且之後沒有成功過時的提示；其他情況回 null */
    public String failureNotice() {
        IntradayStatus st = statuses.findById(today()).orElse(null);
        return st == null || st.lastFailure == null ? null : failureMessage(st);
    }

    private static String failureMessage(IntradayStatus st) {
        return "盤中價更新失敗（" + st.lastFailure + "），"
                + (st.lastSuccess != null ? "目前顯示 " + st.lastSuccess + " 更新的盤中價" : "目前顯示收盤價");
    }

    private IntradayStatus status(String date) {
        return statuses.findById(date).orElseGet(() -> {
            IntradayStatus s = new IntradayStatus();
            s.id = date;
            return s;
        });
    }

    record Tick(BigDecimal price, String time) {}

    /**
     * 一檔的即時資料：c 代號、z 最近成交價（沒有成交時為 -）、t 成交時間 HH:mm:ss、d 日期 yyyyMMdd。
     * 不是今天的資料（假日回傳上一個交易日）、09:00 以前（試撮）或沒有成交時回 null。
     */
    static Tick parse(Map<?, ?> r, String today) {
        String d = String.valueOf(r.get("d"));
        String t = String.valueOf(r.get("t"));
        if (!today.replace("-", "").equals(d)) return null;
        if (t.length() < 5 || t.compareTo("09:00:00") < 0) return null;
        BigDecimal price = QuoteService.number(String.valueOf(r.get("z")));
        if (price == null) return null;
        return new Tick(price, t);
    }
}
