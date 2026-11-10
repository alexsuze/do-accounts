package com.doaccounts.service;

import com.doaccounts.model.ExchangeRate;
import com.doaccounts.repository.ExchangeRateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * 每日匯率排程（SD 4.4）：06:00、18:00（台北）抓取，主要來源失敗改用備援；都失敗只記錄日誌，查詢時沿用最近一筆。
 * 兩個來源都是 fawazahmed0 currency-api（免金鑰）：jsDelivr 與 Cloudflare Pages 鏡像。
 * （frankfurter.app 使用歐洲央行資料，沒有新台幣，不能當備援。）
 */
@Component
public class FxUpdater {

    private static final Logger log = LoggerFactory.getLogger(FxUpdater.class);

    static final String PRIMARY_URL = "https://cdn.jsdelivr.net/npm/@fawazahmed0/currency-api@latest/v1/currencies/twd.json";
    static final String BACKUP_URL = "https://latest.currency-api.pages.dev/v1/currencies/twd.json";

    /** 第一次部署還沒抓到匯率時使用的初始值（1 單位 = ? TWD） */
    static final Map<String, BigDecimal> SEED = Map.of("USD", new BigDecimal("32"), "JPY", new BigDecimal("0.21"));

    private final ExchangeRateRepository repo;
    /** 讀取網址並回傳 JSON（測試時替換） */
    private final Function<String, Map<?, ?>> fetcher;

    @Autowired
    public FxUpdater(ExchangeRateRepository repo) {
        this(repo, url -> RestClient.create().get().uri(url).retrieve().body(Map.class));
    }

    FxUpdater(ExchangeRateRepository repo, Function<String, Map<?, ?>> fetcher) {
        this.repo = repo;
        this.fetcher = fetcher;
    }

    public record Result(boolean ok, String source, String date, Map<String, BigDecimal> rates, String message) {}

    /** 一筆來源的抓取結果：日期與 1 幣別 = ? TWD */
    record Fetched(String date, Map<String, BigDecimal> rates) {}

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        Thread.ofVirtual().name("fx-init").start(() -> {
            try {
                seedIfEmpty();
                if (!repo.existsByUserIdIsNullAndDateAndSourceNot(Dates.today().toString(), ExchangeRate.SOURCE_SEED)) update();
            } catch (RuntimeException e) {
                log.warn("啟動時檢查匯率失敗（不影響啟動）：{}", e.getMessage());
            }
        });
    }

    @Scheduled(cron = "0 0 6,18 * * *", zone = "Asia/Taipei")
    public void scheduled() {
        update();
    }

    /** 抓取並保存今天的匯率；管理頁也可手動執行 */
    public Result update() {
        String error = null;
        for (String[] src : List.of(new String[]{ExchangeRate.SOURCE_PRIMARY, PRIMARY_URL}, new String[]{ExchangeRate.SOURCE_BACKUP, BACKUP_URL})) {
            try {
                Fetched f = parse(fetcher.apply(src[1]));
                f.rates().forEach((cur, rate) -> upsert(f.date(), cur, rate, src[0]));
                log.info("匯率已更新（{}，{}）：{}", src[0], f.date(), f.rates());
                return new Result(true, src[0], f.date(), f.rates(), "匯率已更新");
            } catch (RuntimeException e) {
                error = e.getMessage();
                log.warn("匯率來源 {} 失敗：{}", src[0], error);
            }
        }
        // 詳細原因只寫日誌（含網址等技術細節），畫面上顯示簡短說明
        return new Result(false, null, null, Map.of(), "匯率來源暫時無法連線，先沿用最近一次的匯率，請稍後再試");
    }

    /** 某個幣別完全沒有匯率時寫入初始值，確保第一次部署就能換算 */
    public void seedIfEmpty() {
        SEED.forEach((cur, rate) -> {
            if (!repo.existsByUserIdIsNullAndCurrency(cur)) upsert(Dates.today().toString(), cur, rate, ExchangeRate.SOURCE_SEED);
        });
    }

    private void upsert(String date, String currency, BigDecimal rate, String source) {
        ExchangeRate r = repo.findFirstByUserIdIsNullAndCurrencyAndDate(currency, date).orElseGet(ExchangeRate::new);
        r.date = date;
        r.currency = currency;
        r.base = "TWD";
        r.rate = rate;
        r.source = source;
        r.userId = null;
        r.updatedAt = Instant.now();
        repo.save(r);
    }

    /**
     * 解析 currency-api 的回應：{"date":"2026-11-09","twd":{"usd":0.0308,"jpy":4.67,...}}，
     * 內容是 1 TWD = ? 外幣，取倒數得到 1 外幣 = ? TWD（保留 8 位小數）。
     */
    static Fetched parse(Map<?, ?> body) {
        if (body == null) throw new IllegalStateException("回應是空的");
        Object date = body.get("date");
        if (!(date instanceof String d)) throw new IllegalStateException("回應缺少日期");
        Dates.check(d, "匯率日期");
        if (!(body.get("twd") instanceof Map<?, ?> twd)) throw new IllegalStateException("回應缺少 twd 匯率表");
        Map<String, BigDecimal> out = new LinkedHashMap<>();
        for (String cur : FxService.quoted()) {
            Object v = twd.get(cur.toLowerCase(Locale.ROOT));
            if (v == null) throw new IllegalStateException("回應缺少 " + cur);
            BigDecimal perTwd = new BigDecimal(v.toString());
            if (perTwd.signum() <= 0) throw new IllegalStateException(cur + " 匯率不合理：" + v);
            out.put(cur, BigDecimal.ONE.divide(perTwd, MathContext.DECIMAL64).setScale(8, RoundingMode.HALF_UP).stripTrailingZeros());
        }
        return new Fetched(d, out);
    }
}
