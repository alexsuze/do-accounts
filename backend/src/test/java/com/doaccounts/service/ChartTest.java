package com.doaccounts.service;

import com.doaccounts.model.Account;
import com.doaccounts.model.Security;
import com.doaccounts.service.InvestmentService.TradeRequest;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

import static com.doaccounts.service.Fixture.ME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** K 線圖：向交易所補抓、只抓缺的月份、均價線、買賣標記、沒有行情來源時用收盤價（SD 4.10）。 */
class ChartTest {

    private final Fixture f = new Fixture();
    private final Account broker = f.account(ME, "元大證券", "investment", "TWD", "1000000", "2020-01-01");
    private final Security etf;
    private final Security voo;
    private final YearMonth now = YearMonth.from(Dates.today());

    ChartTest() {
        f.categoryService.ensureDefaults(ME);
        etf = sec("TW", "0050", "TWD");
        voo = sec("US", "VOO", "USD");
        // 每個月兩天行情（民國年）；第 3 列沒有成交，要略過
        for (int i = 0; i <= 17; i++) {
            YearMonth m = now.minusMonths(i);
            String roc = (m.getYear() - 1911) + "/" + String.format("%02d", m.getMonthValue());
            f.candleFeed.put(String.format(CandleService.TWSE_URL, m.toString().replace("-", ""), "0050"), Map.of("stat", "OK", "data", List.of(
                    List.of(roc + "/01", "1,234,000", "x", "100.00", "105.00", "99.50", "104.00", "+1", "10"),
                    List.of(roc + "/02", "2,000,000", "x", "104.00", "106.00", "103.00", "105.50", "+1", "10"),
                    List.of(roc + "/03", "0", "0", "--", "--", "--", "--", "0", "0"))));
        }
    }

    /** 抓取的月數：(今天 − months 個月 − 100 天) 所在月份到本月 */
    private int months(int m) {
        YearMonth start = YearMonth.from(Dates.today().minusMonths(m).minusDays(InvestmentService.WARMUP_DAYS));
        int n = 0;
        for (YearMonth x = start; !x.isAfter(now); x = x.plusMonths(1)) n++;
        return n;
    }

    private Security sec(String market, String symbol, String currency) {
        Security s = new Security();
        s.market = market;
        s.symbol = symbol;
        s.name = symbol;
        s.currency = currency;
        s.assetType = "etf";
        return f.securityRepo.save(s);
    }

    private void buy(String date, String qty, String price) {
        f.investmentService.create(ME, new TradeRequest("buy", broker.id, etf.id, date, new BigDecimal(qty), new BigDecimal(price),
                BigDecimal.ZERO, null, null, null, null, null, null));
    }

    @Test
    void fetchesMissingMonthsOnceAndReturnsAvgAndMarkers() {
        buy(now.minusMonths(2).atDay(1).toString(), "1000", "100");
        buy(now.atDay(1).toString(), "1000", "110");
        var c = f.investmentService.chart(f.me, etf.id, null, 6);
        assertEquals("candle", c.kind());
        assertTrue(c.complete());
        int expected = months(6);
        assertEquals(expected, f.candleRequests.size(), "6 個月前再往前 100 天（算均線用）到本月");
        assertTrue(c.bars().size() >= 2 * (expected - 1), "每月 2 天（第一個月可能只取到起始日以後）");
        // 每月 1 日那一列（不用 getFirst：起始日剛好是某月 2 日時，第一根是 2 日那列）
        var day1 = c.bars().stream().filter(b -> b.date().endsWith("-01")).findFirst().orElseThrow();
        assertEquals(1_234_000L, day1.volume());
        assertEquals(0, new BigDecimal("104.00").compareTo(day1.close()));
        assertTrue(c.bars().stream().anyMatch(b -> b.date().compareTo(c.from()) < 0), "含期間之前的資料（算均線用）");
        assertEquals(0, new BigDecimal("105").compareTo(c.avgCost()));
        assertEquals(0, new BigDecimal("2000").compareTo(c.quantity()));
        assertEquals(2, c.markers().size());
        assertEquals("buy", c.markers().getFirst().tradeType());

        // 再開一次：過去的月份不再抓，本月 30 分鐘內也不重抓
        f.candleRequests.clear();
        f.investmentService.chart(f.me, etf.id, null, 6);
        assertEquals(0, f.candleRequests.size());
        // 期間拉長只補抓多出來的月份
        f.investmentService.chart(f.me, etf.id, null, 12);
        assertEquals(months(12) - expected, f.candleRequests.size());
    }

    @Test
    void unreachableExchangeReturnsPartialData() {
        f.candleFeed.remove(String.format(CandleService.TWSE_URL, now.minusMonths(3).toString().replace("-", ""), "0050"));
        var c = f.investmentService.chart(f.me, etf.id, null, 6);
        assertFalse(c.complete());
        assertEquals(months(6) - 3, f.candleRequests.size(), "失敗的那個月之後就停止，不再繼續打");
        assertNull(c.avgCost(), "沒有持股時沒有均價線");
        assertThrows(ResponseStatusException.class, () -> f.investmentService.chart(f.me, etf.id, null, 0));
    }

    @Test
    void securitiesWithoutExchangeDataUseClosingPrices() {
        f.investmentService.setPrice(ME, voo.id, Dates.today().minusDays(3).toString(), new BigDecimal("560.75"));
        f.investmentService.setPrice(ME, voo.id, Dates.today().minusYears(2).toString(), new BigDecimal("400"));
        var c = f.investmentService.chart(f.me, voo.id, null, 6);
        assertEquals("line", c.kind());
        assertEquals(1, c.closes().size(), "只取期間內");
        assertTrue(c.bars().isEmpty());
        assertTrue(f.candleRequests.isEmpty());
    }

    @Test
    void parsesTpexTablesInThousandShares() {
        var rows = CandleService.parse(Map.of("tables", List.of(Map.of("data", List.of(
                List.of("115/11/03", "1,500", "x", "450.0", "460.0", "448.0", "455.5", "+5", "100"))))), "s1", true);
        assertEquals(1, rows.size());
        assertEquals(1_500_000L, rows.getFirst().volume);
        assertEquals("2026-11-03", rows.getFirst().date);
        assertTrue(CandleService.parse(Map.of("stat", "很抱歉，沒有符合條件的資料!"), "s1", false).isEmpty());
    }
}
