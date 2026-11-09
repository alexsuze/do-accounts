package com.doaccounts.service;

import com.doaccounts.model.ExchangeRate;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static com.doaccounts.service.Fixture.ME;
import static com.doaccounts.service.Fixture.OTHER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 匯率：查表規則、手動覆寫、跨本位幣、抓取與備援（SD 4.4）。 */
class FxTest {

    private final Fixture f = new Fixture();

    private static void eq(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), () -> "expected " + expected + " but was " + actual);
    }

    @Test
    void lookupUsesSameOrEarlierDayThenEarliestAsFallback() {
        f.rate("USD", "2026-11-01", "32");
        f.rate("USD", "2026-11-05", "33");
        RateTable t = f.fxService.table(f.me, "2026-11-30");
        eq("33", t.rate("USD", "2026-11-09"));
        eq("33", t.rate("USD", "2026-11-05"));
        eq("32", t.rate("USD", "2026-11-04"));
        eq("32", t.rate("USD", "2020-01-01")); // 更早都沒有：用最早的一筆
        eq("1", t.rate("TWD", "2026-11-09"));
        eq("330", t.toBase(new BigDecimal("10"), "USD", "2026-11-09"));
        assertThrows(ResponseStatusException.class, () -> t.rate("JPY", "2026-11-09"), "完全沒有匯率要明確報錯");
    }

    @Test
    void manualRateOverridesSameDayOnlyForThatUser() {
        f.rate("USD", "2026-11-09", "32.4");
        f.fxService.setManual(ME, "2026-11-09", "USD", new BigDecimal("32.5"));
        eq("32.5", f.fxService.table(f.me, "2026-11-09").rate("USD", "2026-11-09"));
        eq("32.4", f.fxService.table(Fixture.user(OTHER), "2026-11-09").rate("USD", "2026-11-09"));

        var latest = f.fxService.latestRates(f.me);
        assertTrue(latest.stream().anyMatch(r -> r.currency().equals("USD") && r.manual()));

        // 同一天再設一次是覆蓋，不是新增
        f.fxService.setManual(ME, "2026-11-09", "USD", new BigDecimal("32.6"));
        assertEquals(1, f.fxService.manual(ME).size());

        String id = f.fxService.manual(ME).getFirst().id;
        assertThrows(ResponseStatusException.class, () -> f.fxService.deleteManual(OTHER, id), "別人的手動匯率回 404");
        f.fxService.deleteManual(ME, id);
        eq("32.4", f.fxService.table(f.me, "2026-11-09").rate("USD", "2026-11-09"));

        assertThrows(ResponseStatusException.class, () -> f.fxService.setManual(ME, "2026-11-09", "TWD", BigDecimal.ONE), "TWD 是中介幣，不能設");
        assertThrows(ResponseStatusException.class, () -> f.fxService.setManual(ME, "2026-11-09", "USD", BigDecimal.ZERO));
        assertThrows(ResponseStatusException.class, () -> f.fxService.setManual(ME, "2026/11/09", "USD", BigDecimal.TEN));
    }

    @Test
    void crossRateWhenBaseCurrencyIsNotTwd() {
        f.rate("USD", "2026-11-09", "32");
        f.rate("JPY", "2026-11-09", "0.2");
        var u = Fixture.user(ME);
        u.baseCurrency = "USD";
        RateTable t = f.fxService.table(u, "2026-11-09");
        eq("1", t.rate("USD", "2026-11-09"));
        eq("0.03125", t.rate("TWD", "2026-11-09"));
        eq("0.00625", t.rate("JPY", "2026-11-09"));
        eq("10.0000", t.toBase(new BigDecimal("320"), "TWD", "2026-11-09"));
    }

    @Test
    void parseInvertsTwdTable() {
        var fetched = FxUpdater.parse(Map.of("date", "2026-11-09", "twd", Map.of("usd", 0.03125, "jpy", 4.0, "eur", 0.03)));
        assertEquals("2026-11-09", fetched.date());
        eq("32", fetched.rates().get("USD"));
        eq("0.25", fetched.rates().get("JPY"));
        assertThrows(IllegalStateException.class, () -> FxUpdater.parse(Map.of("date", "2026-11-09", "twd", Map.of("usd", 0.03))), "缺幣別");
        assertThrows(IllegalStateException.class, () -> FxUpdater.parse(Map.of("twd", Map.of())));
    }

    @Test
    void updaterFallsBackToMirrorAndKeepsOldRatesWhenBothFail() {
        Map<?, ?> ok = Map.of("date", "2026-11-09", "twd", Map.of("usd", 0.03125, "jpy", 4.0));
        FxUpdater u = new FxUpdater(f.fxRepo, url -> {
            if (url.equals(FxUpdater.PRIMARY_URL)) throw new IllegalStateException("timeout");
            return ok;
        });
        var r = u.update();
        assertTrue(r.ok());
        assertEquals(ExchangeRate.SOURCE_BACKUP, r.source());
        assertEquals(2, f.rates.size());

        // 再抓一次同一天是覆蓋
        u.update();
        assertEquals(2, f.rates.size());

        FxUpdater down = new FxUpdater(f.fxRepo, url -> { throw new IllegalStateException("down"); });
        assertFalse(down.update().ok());
        assertEquals(2, f.rates.size(), "都失敗時不動既有匯率");
    }

    @Test
    void seedOnlyWhenCurrencyHasNoRate() {
        f.rate("USD", "2026-11-01", "32.4");
        new FxUpdater(f.fxRepo, url -> Map.of()).seedIfEmpty();
        List<ExchangeRate> seeds = f.rates.stream().filter(r -> ExchangeRate.SOURCE_SEED.equals(r.source)).toList();
        assertEquals(1, seeds.size());
        assertEquals("JPY", seeds.getFirst().currency);
    }
}
