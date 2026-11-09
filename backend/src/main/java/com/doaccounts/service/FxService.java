package com.doaccounts.service;

import com.doaccounts.model.ExchangeRate;
import com.doaccounts.model.User;
import com.doaccounts.repository.ExchangeRateRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 匯率查詢與手動覆寫（SA FR-8、SD 4.4）。抓取排程在 {@link FxUpdater}。
 */
@Service
public class FxService {

    /** 匯率上限，擋掉打錯的數字 */
    private static final BigDecimal MAX_RATE = new BigDecimal("100000");

    private final ExchangeRateRepository repo;

    public FxService(ExchangeRateRepository repo) {
        this.repo = repo;
    }

    /** 不是中介幣（TWD）的支援幣別：需要匯率的幣別 */
    public static List<String> quoted() {
        return Currencies.CODES.stream().filter(c -> !Currencies.PIVOT.equals(c)).toList();
    }

    public static String baseOf(User user) {
        return user.baseCurrency == null ? Currencies.PIVOT : user.baseCurrency;
    }

    /** 載入到 to（含）為止的匯率，換算成使用者的本位幣 */
    public RateTable table(User user, String to) {
        List<String> cur = quoted();
        return new RateTable(baseOf(user),
                repo.findByUserIdIsNullAndCurrencyInAndDateLessThanEqual(cur, to),
                repo.findByUserIdAndCurrencyInAndDateLessThanEqual(user.id, cur, to));
    }

    /** 今天為止的匯率（首頁等即時數值，FR-8.2） */
    public RateTable latest(User user) {
        return table(user, Dates.today().toString());
    }

    /** 1 currency = rate TWD；manual 表示這一筆是使用者自己的手動值 */
    public record LatestRate(String currency, BigDecimal rate, String date, String source, boolean manual) {}

    public List<LatestRate> latestRates(User user) {
        RateTable t = latest(user);
        List<LatestRate> out = new ArrayList<>();
        for (String c : quoted()) {
            ExchangeRate r = t.record(c, Dates.today().toString());
            if (r != null) out.add(new LatestRate(c, r.rate, r.date, r.source, r.userId != null));
        }
        return out;
    }

    public List<ExchangeRate> manual(String userId) {
        return repo.findByUserIdOrderByDateDesc(userId);
    }

    /** 歷史匯率（含手動值，同一天手動優先），舊到新 */
    public List<LatestRate> history(User user, String currency, String from, String to) {
        checkQuoted(currency);
        Dates.check(from, "開始日期");
        Dates.check(to, "結束日期");
        List<ExchangeRate> all = new ArrayList<>(repo.findByUserIdIsNullAndCurrencyInAndDateLessThanEqual(List.of(currency), to));
        all.addAll(repo.findByUserIdAndCurrencyInAndDateLessThanEqual(user.id, List.of(currency), to));
        java.util.TreeMap<String, ExchangeRate> byDate = new java.util.TreeMap<>();
        all.stream().sorted(Comparator.comparing(r -> r.userId == null ? 0 : 1)).forEach(r -> byDate.put(r.date, r));
        return byDate.subMap(from, true, to, true).values().stream()
                .map(r -> new LatestRate(r.currency, r.rate, r.date, r.source, r.userId != null)).toList();
    }

    /** 手動新增或覆寫某天的匯率（FR-8.4），只影響自己 */
    public ExchangeRate setManual(String userId, String date, String currency, BigDecimal rate) {
        checkQuoted(currency);
        Dates.check(date, "日期");
        if (rate == null || rate.signum() <= 0) throw bad("匯率必須大於 0");
        if (rate.compareTo(MAX_RATE) > 0) throw bad("匯率太大");
        if (rate.stripTrailingZeros().scale() > 8) throw bad("匯率最多 8 位小數");
        ExchangeRate r = repo.findFirstByUserIdAndCurrencyAndDate(userId, currency, date).orElseGet(ExchangeRate::new);
        r.userId = userId;
        r.currency = currency;
        r.date = date;
        r.base = Currencies.PIVOT;
        r.rate = rate.stripTrailingZeros();
        r.source = ExchangeRate.SOURCE_MANUAL;
        r.updatedAt = Instant.now();
        return repo.save(r);
    }

    public void deleteManual(String userId, String id) {
        ExchangeRate r = repo.findById(id).filter(x -> userId.equals(x.userId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "找不到這筆手動匯率"));
        repo.delete(r);
    }

    private static void checkQuoted(String currency) {
        if (currency == null || !quoted().contains(currency)) throw bad("不支援的幣別：" + currency);
    }

    private static ResponseStatusException bad(String m) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, m);
    }
}
