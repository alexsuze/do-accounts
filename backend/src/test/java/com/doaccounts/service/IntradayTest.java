package com.doaccounts.service;

import com.doaccounts.model.Account;
import com.doaccounts.model.IntradayQuote;
import com.doaccounts.model.Price;
import com.doaccounts.model.Security;
import com.doaccounts.service.InvestmentService.TradeRequest;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import static com.doaccounts.service.Fixture.ME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 盤中價：抓取規則、沒有成交、失敗時沿用、收盤價公布後不再用、哪些畫面用盤中價（SD 4.11）。 */
class IntradayTest {

    private final Fixture f = new Fixture();
    private final Security etf = sec("TW", "0050", "TWD");
    private final Security otc = sec("TWO", "6488", "TWD");
    private final Security voo = sec("US", "VOO", "USD");
    /** 週四（交易日） */
    private static final LocalDate DAY = LocalDate.of(2026, 11, 12);

    private Security sec(String market, String symbol, String currency) {
        Security s = new Security();
        s.market = market;
        s.symbol = symbol;
        s.name = symbol;
        s.currency = currency;
        s.assetType = "etf";
        return f.securityRepo.save(s);
    }

    private void at(LocalDate d, int h, int m) {
        f.now = ZonedDateTime.of(d, LocalTime.of(h, m), Dates.ZONE);
    }

    private static Map<String, Object> row(String code, String z, String t, String d) {
        return Map.of("c", code, "z", z, "t", t, "d", d);
    }

    private static void eq(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), () -> "expected " + expected + " but was " + actual);
    }

    @Test
    void savesTodayQuotesAndTreatsNoTradeAsNormal() {
        at(DAY, 10, 30);
        f.misFeed = Map.of("msgArray", List.of(row("0050", "97.45", "10:29:58", "20261112"), row("6488", "-", "10:29:00", "20261112")));
        var r = f.intradayService.refresh(List.of(etf, otc, voo));
        assertTrue(r.attempted());
        assertFalse(r.failed());
        assertEquals(1, r.updated());
        assertEquals(1, r.noTrade());
        assertTrue(r.message().contains("1 檔暫時沒有成交"), r.message());
        IntradayQuote q = f.intradayQuotes.getFirst();
        eq("97.45", q.price);
        assertEquals("10:29:58", q.time);
        assertEquals(1, f.intradayQuotes.size(), "美股不抓");

        // 同一天再抓是覆蓋
        at(DAY, 10, 45);
        f.misFeed = Map.of("msgArray", List.of(row("0050", "98.00", "10:44:50", "20261112")));
        f.intradayService.refresh(List.of(etf, otc));
        assertEquals(1, f.intradayQuotes.size());
        eq("98.00", f.intradayQuotes.getFirst().price);
    }

    @Test
    void failureKeepsEarlierQuoteOrFallsBackToClose() {
        at(DAY, 10, 0);
        f.misFeed = null;
        var r = f.intradayService.refresh(List.of(etf));
        assertTrue(r.failed());
        assertEquals("盤中價更新失敗（10:00），目前顯示收盤價", r.message());

        at(DAY, 10, 30);
        f.misFeed = Map.of("msgArray", List.of(row("0050", "97.45", "10:29:58", "20261112")));
        f.intradayService.refresh(List.of(etf));
        assertNull(f.intradayService.failureNotice(), "成功後清除失敗提示");

        at(DAY, 11, 5);
        f.misFeed = Map.of("rtmessage", "Empty Query.");
        r = f.intradayService.refresh(List.of(etf));
        assertEquals("盤中價更新失敗（11:05），目前顯示 10:30 更新的盤中價", r.message());
        assertEquals(r.message(), f.intradayService.failureNotice());
        eq("97.45", f.intradayQuotes.getFirst().price);
    }

    @Test
    void onlyFetchesOnTradingDaysAfterOpenAndBeforeOfficialClose() {
        f.misFeed = Map.of("msgArray", List.of(row("0050", "97.45", "10:29:58", "20261112")));
        at(DAY, 8, 50);
        assertFalse(f.intradayService.refresh(List.of(etf)).attempted(), "開盤前");
        at(LocalDate.of(2026, 11, 14), 10, 0);
        assertFalse(f.intradayService.refresh(List.of(etf)).attempted(), "週六");

        // 收盤後、正式收盤價公布前還會抓（拿到的是最後成交價）；公布後就不抓了
        at(DAY, 13, 50);
        f.misFeed = Map.of("msgArray", List.of(row("0050", "97.80", "13:30:00", "20261112")));
        assertEquals(1, f.intradayService.refresh(List.of(etf)).updated());
        Price close = new Price();
        close.securityId = etf.id;
        close.date = "2026-11-12";
        close.close = new BigDecimal("97.80");
        close.source = Price.TWSE;
        f.priceRepo.save(close);
        assertFalse(f.intradayService.refresh(List.of(etf)).attempted());

        // 假日回傳上一個交易日、或試撮時間的資料不算
        assertNull(IntradayService.parse(row("0050", "97.45", "13:30:00", "20261111"), "2026-11-12"));
        assertNull(IntradayService.parse(row("0050", "97.45", "08:59:00", "20261112"), "2026-11-12"));
    }

    @Test
    void currentPricePrefersTodaysIntradayUntilCloseArrives() {
        IntradayQuote q = new IntradayQuote();
        q.date = "2026-11-12";
        q.price = new BigDecimal("98");
        q.time = "10:30:00";
        Price yesterday = new Price();
        yesterday.date = "2026-11-11";
        yesterday.close = new BigDecimal("97");
        yesterday.source = "twse";
        Price today = new Price();
        today.date = "2026-11-12";
        today.close = new BigDecimal("99");
        today.source = "twse";
        assertEquals("intraday", InvestmentService.current(yesterday, q).kind());
        assertEquals("close", InvestmentService.current(today, q).kind());
        assertEquals("intraday", InvestmentService.current(null, q).kind());
        assertEquals("close", InvestmentService.current(yesterday, null).kind());
        assertNull(InvestmentService.current(null, null));
    }

    @Test
    void holdingsAndDashboardUseIntradayButNetWorthTrendUsesClose() {
        String today = Dates.today().toString();
        f.categoryService.ensureDefaults(ME);
        Account broker = f.account(ME, "元大證券", "investment", "TWD", "0", "2020-01-01");
        f.investmentService.create(ME, new TradeRequest("opening", broker.id, etf.id, "2020-01-02", new BigDecimal("1000"),
                new BigDecimal("90"), null, null, null, null, null, null, null));
        Price close = new Price();
        close.securityId = etf.id;
        close.date = Dates.today().minusDays(1).toString();
        close.close = new BigDecimal("97");
        close.source = Price.TWSE;
        f.priceRepo.save(close);
        IntradayQuote q = new IntradayQuote();
        q.securityId = etf.id;
        q.date = today;
        q.price = new BigDecimal("98.5");
        q.time = "10:30:00";
        f.intradayRepo.save(q);

        var h = f.investmentService.holdings(f.me, null).getFirst();
        assertEquals("intraday", h.priceKind());
        assertEquals("10:30:00", h.priceTime());
        eq("98500", h.marketValue());
        eq("98500", f.investmentService.summary(f.me, null).marketValue());
        eq("98500", f.dashboardService.get(f.me).investments());
        eq("97000", f.reportService.netWorth(f.me, 1).points().getLast().investments(), "淨資產趨勢一律用收盤價");
    }

    private static void eq(String expected, BigDecimal actual, String msg) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), msg);
    }
}
