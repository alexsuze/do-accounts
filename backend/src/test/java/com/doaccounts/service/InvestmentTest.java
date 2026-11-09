package com.doaccounts.service;

import com.doaccounts.model.Account;
import com.doaccounts.model.Security;
import com.doaccounts.model.Transaction;
import com.doaccounts.service.InvestmentService.TradeRequest;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static com.doaccounts.service.Fixture.ME;
import static com.doaccounts.service.Fixture.OTHER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 投資：交易與交割分錄、移動平均成本、超賣檢查、報價與市值、淨資產、台股清單（SA FR-9、SD 4.5）。 */
class InvestmentTest {

    private final Fixture f = new Fixture();
    private final Account broker = f.account(ME, "元大證券", "investment", "TWD", "100000", "2026-01-01");
    private final Account bank = f.account(ME, "國泰", "bank", "TWD", "0", "2026-01-01");
    private final Account usdBank = f.account(ME, "玉山外幣", "bank", "USD", "1000", "2026-01-01");
    private final Account usBroker = f.account(ME, "複委託", "investment", "USD", "0", "2026-01-01");
    private final Security etf;
    private final Security voo;

    InvestmentTest() {
        f.categoryService.ensureDefaults(ME);
        f.quoteFeed.put(QuoteService.TWSE_URL, List.of(
                Map.of("Code", "0050", "Name", "元大台灣50", "ClosingPrice", "192.35", "Date", "1151106"),
                Map.of("Code", "2330", "Name", "台積電", "ClosingPrice", "1,085.00", "Date", "1151106"),
                Map.of("Code", "9999", "Name", "沒成交", "ClosingPrice", "--", "Date", "1151106")));
        f.quoteFeed.put(QuoteService.TPEX_URL, List.of(Map.of("SecuritiesCompanyCode", "6488", "CompanyName", "環球晶", "Close", "450.5", "Date", "1151106")));
        etf = f.investmentService.searchSecurities("0050", null).getFirst();
        voo = f.investmentService.createSecurity(new InvestmentService.SecurityRequest("US", "voo", "Vanguard S&P 500 ETF", null, "etf"));
    }

    private static void eq(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), () -> "expected " + expected + " but was " + actual);
    }

    private TradeRequest trade(String type, Account acc, Security s, String date, String qty, String price, String fee, String tax, Account settle) {
        return new TradeRequest(type, acc.id, s.id, date, qty == null ? null : new BigDecimal(qty), price == null ? null : new BigDecimal(price),
                fee == null ? null : new BigDecimal(fee), tax == null ? null : new BigDecimal(tax), settle == null ? null : settle.id, null, null, null, null);
    }

    private BigDecimal balance(Account a) {
        return f.accountService.getWithBalance(ME, a.id).balance;
    }

    @Test
    void securitiesFromTaiwanListAndManual() {
        assertEquals("元大台灣50", etf.name);
        assertEquals("etf", etf.assetType, "00 開頭是 ETF");
        assertEquals("TWD", etf.currency);
        assertEquals(1, f.prices.size(), "建立時順便存入收盤價");
        assertEquals("2026-11-06", f.prices.getFirst().date, "民國年日期");
        Security twse = f.investmentService.searchSecurities("2330", "TW").getFirst();
        assertEquals("stock", twse.assetType);
        assertEquals("TWO", f.investmentService.searchSecurities("6488", null).getFirst().market);
        assertEquals("VOO", voo.symbol);
        assertEquals("USD", voo.currency);
        assertEquals(voo.id, f.investmentService.createSecurity(new InvestmentService.SecurityRequest("US", "VOO", "x", null, null)).id, "同代號不重複建立");
        assertTrue(f.investmentService.searchSecurities("vang", null).stream().anyMatch(s -> s.id.equals(voo.id)), "名稱搜尋");
        assertThrows(ResponseStatusException.class, () -> f.investmentService.createSecurity(new InvestmentService.SecurityRequest("XX", "A", "a", null, null)));
    }

    @Test
    void movingAverageCostRealizedDividendsSplitAndSettlement() {
        f.investmentService.create(ME, trade("buy", broker, etf, "2026-03-01", "1000", "150", "20", null, null));      // 成本 150,020
        f.investmentService.create(ME, trade("buy", broker, etf, "2026-04-01", "1000", "170", "20", null, bank));      // 成本 320,040，均價 160.02
        eq("-150020", balance(broker).subtract(new BigDecimal("100000")));
        eq("-170020", balance(bank)); // 交割帳戶可以是別的帳戶
        Transaction sell = f.investmentService.create(ME, trade("sell", broker, etf, "2026-05-01", "500", "200", "142", "100", null));
        // 已實現 = 500×200 − 142 − 100 − 500×160.02 = 19,748
        f.investmentService.create(ME, trade("cash_dividend", broker, etf, "2026-07-01", "1500", "1", "10", null, null));   // 股利 1,490
        f.investmentService.create(ME, trade("stock_dividend", broker, etf, "2026-08-01", "150", null, null, null, null));
        f.investmentService.create(ME, trade("split", broker, etf, "2026-09-01", "2", null, null, null, null));

        var r = HoldingCalculator.compute(f.ledger.investments(ME), null);
        var p = r.get(broker.id, etf.id);
        eq("3300", p.held);                       // (2000 − 500 + 150) × 2
        eq("240030", p.cost);                     // 320,040 − 500 × 160.02
        eq("19748", p.realized);
        eq("19748", r.realizedByTrade().get(sell.id));
        eq("1490", p.dividends);
        eq("320040", p.invested);

        // 股利是收入（投資損益分類），買賣交割不算收支
        var page = f.txService.list(ME, "2026-01-01", "2026-12-31", null, null, null, null, null, 0, 50);
        eq("1490", page.summary().get("TWD").income());
        eq("0", page.summary().get("TWD").expense());

        // 手動記帳 API 不能改投資交易
        assertThrows(ResponseStatusException.class, () -> f.txService.delete(ME, sell.id));
    }

    @Test
    void oversellIsRejectedIncludingEditsAndDeletesOfPastTrades() {
        Transaction buy = f.investmentService.create(ME, trade("buy", broker, etf, "2026-03-01", "100", "150", null, null, null));
        f.investmentService.create(ME, trade("sell", broker, etf, "2026-04-01", "60", "160", null, null, null));
        var ex = assertThrows(ResponseStatusException.class, () -> f.investmentService.create(ME, trade("sell", broker, etf, "2026-05-01", "41", "160", null, null, null)));
        assertTrue(ex.getReason().contains("持有 40 股"), ex.getReason());
        assertThrows(ResponseStatusException.class, () -> f.investmentService.delete(ME, buy.id), "刪掉買進後之前的賣出會超賣");
        var shrink = new TradeRequest("buy", broker.id, etf.id, "2026-03-01", new BigDecimal("50"), new BigDecimal("150"), null, null, null, null, null, buy.version, null);
        assertThrows(ResponseStatusException.class, () -> f.investmentService.update(ME, buy.id, shrink), "把買進改少也會超賣");
        var stale = new TradeRequest("buy", broker.id, etf.id, "2026-03-01", new BigDecimal("200"), new BigDecimal("150"), null, null, null, null, null, buy.version + 9, null);
        assertThrows(ResponseStatusException.class, () -> f.investmentService.update(ME, buy.id, stale), "版本不符");
        var grow = new TradeRequest("buy", broker.id, etf.id, "2026-03-01", new BigDecimal("200"), new BigDecimal("150"), null, null, null, null, null, buy.version, null);
        f.investmentService.update(ME, buy.id, grow);
        eq("140", HoldingCalculator.compute(f.ledger.investments(ME), null).get(broker.id, etf.id).held);
        assertThrows(ResponseStatusException.class, () -> f.investmentService.trade(OTHER, buy.id), "別人的交易 404");
    }

    @Test
    void validationRules() {
        assertThrows(ResponseStatusException.class, () -> f.investmentService.create(ME, trade("buy", bank, etf, "2026-03-01", "1", "1", null, null, null)), "不是投資帳戶");
        assertThrows(ResponseStatusException.class, () -> f.investmentService.create(ME, trade("buy", broker, etf, "2025-03-01", "1", "1", null, null, null)), "早於開戶日");
        assertThrows(ResponseStatusException.class, () -> f.investmentService.create(ME, trade("buy", broker, etf, "2026-03-01", "0", "1", null, null, null)), "股數 0");
        assertThrows(ResponseStatusException.class, () -> f.investmentService.create(ME, trade("buy", broker, etf, "2026-03-01", "1", "-1", null, null, null)), "負價格");
        assertThrows(ResponseStatusException.class, () -> f.investmentService.create(ME, trade("cash_dividend", broker, etf, "2026-03-01", "10", "1", "20", null, null)), "費用大於股利");
        assertThrows(ResponseStatusException.class, () -> f.investmentService.create(ME, trade("fee", broker, etf, "2026-03-01", null, null, null, null, null)), "費用必填");
        // 美股用台幣帳戶交割：必須填實際扣款金額
        assertThrows(ResponseStatusException.class, () -> f.investmentService.create(ME, trade("buy", usBroker, voo, "2026-03-01", "1.5", "508.2", "2", null, bank)));
        var cross = new TradeRequest("buy", usBroker.id, voo.id, "2026-03-01", new BigDecimal("1.5"), new BigDecimal("508.2"), new BigDecimal("2"),
                null, bank.id, new BigDecimal("24500"), null, null, null);
        f.investmentService.create(ME, cross);
        eq("-24500", balance(bank));
        // 同幣別：依幣別小數位四捨五入（6.5 × 560.75 = 3,644.875）
        f.investmentService.create(ME, trade("buy", usBroker, voo, "2026-03-02", "6.5", "560.75", null, null, usdBank));
        eq("-2644.88", balance(usdBank)); // 1,000 − 3,644.88
    }

    @Test
    void holdingsSummaryAndNetWorthUseLatestPricesAndRates() {
        String today = Dates.today().toString();
        String y = today.substring(0, 4);
        f.rate("USD", "2026-01-01", "30");
        f.investmentService.create(ME, trade("buy", broker, etf, y + "-01-02", "1000", "158.2", null, null, null));
        f.investmentService.create(ME, trade("buy", usBroker, voo, y + "-01-02", "2", "500", null, null, usdBank));
        // 0050 有 TWSE 報價 192.35（建立證券時存入）；VOO 沒有報價 → 以成本計，再手動輸入
        var hs = f.investmentService.holdings(f.me, null);
        var vooH = hs.stream().filter(h -> h.security().id.equals(voo.id)).findFirst().orElseThrow();
        assertNull(vooH.price());
        eq("1000.00", vooH.marketValue());
        f.investmentService.setPrice(ME, voo.id, y + "-01-03", new BigDecimal("560.75"));
        hs = f.investmentService.holdings(f.me, null);
        var etfH = hs.stream().filter(h -> h.security().id.equals(etf.id)).findFirst().orElseThrow();
        vooH = hs.stream().filter(h -> h.security().id.equals(voo.id)).findFirst().orElseThrow();
        eq("192350", etfH.marketValue());
        eq("34150", etfH.unrealized());
        eq("21.6", etfH.returnPct());
        eq("1121.50", vooH.marketValue());
        assertTrue(vooH.manualPrice());
        eq("33645", vooH.marketValueBase()); // 1,121.5 × 30
        assertFalse(f.investmentService.holdings(Fixture.user(OTHER), null).stream().findAny().isPresent());

        var s = f.investmentService.summary(f.me, null);
        eq("225995", s.marketValue());         // 192,350 + 33,645
        eq("188200", s.cost());                // 158,200 + 1,000 × 30
        eq("37795", s.unrealized());
        assertEquals(2, s.allocation().get("market").size());
        assertEquals("TW", s.allocation().get("market").getFirst().key());

        // 總覽與淨資產趨勢都包含投資市值；手動報價只影響自己
        var d = f.dashboardService.get(f.me);
        eq("225995", d.investments());
        var nw = f.reportService.netWorth(f.me, 1).points().getLast();
        eq("225995", nw.investments());
        eq(d.netWorth().toPlainString(), nw.netWorth());

        // 隱藏的投資帳戶不計入
        f.accountService.update(ME, usBroker.id, new AccountService.AccountRequest(usBroker.name, usBroker.kind, usBroker.currency,
                usBroker.openingBalance, usBroker.openingDate, false, null, null));
        eq("192350", f.dashboardService.get(f.me).investments());
    }

    @Test
    void quoteRefreshAndParsing() {
        Security tsmc = f.investmentService.searchSecurities("2330", null).getFirst();
        f.prices.clear();
        var r = f.quoteService.refresh(true);
        assertEquals(2, r.updated(), r.message()); // 0050、2330；VOO 不是台股
        eq("1085.00", f.prices.stream().filter(p -> p.securityId.equals(tsmc.id)).findFirst().orElseThrow().close);
        f.quoteService.refresh(true);
        assertEquals(2, f.prices.size(), "同一天再抓是覆蓋");
        assertNull(QuoteService.number("--"));
        assertEquals("2026-11-07", QuoteService.rocDate("1151107"));
        assertEquals("2026-11-07", QuoteService.rocDate("20261107"));
        assertEquals(LocalDate.of(2026, 11, 6), QuoteService.lastWeekday(LocalDate.of(2026, 11, 8)));
        f.quoteFeed.clear();
        assertTrue(f.quoteService.refresh(true).message().contains("無法連線"));
        assertNull(f.quoteService.lookup("TW", "0050"), "來源連不到時查不到，不丟例外");
    }

    @Test
    void backupIncludesInvestments() {
        f.investmentService.create(ME, trade("buy", broker, etf, "2026-03-01", "100", "150", null, null, null));
        var json = tools.jackson.databind.json.JsonMapper.builder()
                .propertyNamingStrategy(tools.jackson.databind.PropertyNamingStrategies.SNAKE_CASE).build();
        var copy = json.readValue(json.writeValueAsString(f.backupService.export(f.me)), BackupService.Backup.class);
        assertEquals(1, copy.securities().size());
        var other = Fixture.user(OTHER);
        f.users.add(other);
        f.backupService.restore(other, copy);
        var restored = HoldingCalculator.compute(f.ledger.investments(OTHER), null).positions().values().iterator().next();
        eq("100", restored.held);
        assertEquals(etf.id, restored.securityId, "同市場同代號沿用既有證券");
        assertTrue(f.accounts.stream().anyMatch(a -> a.id.equals(restored.accountId) && a.userId.equals(OTHER)));
        assertEquals(1, f.securities.stream().filter(s -> s.symbol.equals("0050")).count());
    }

    @Test
    void openingPositionHasNoCashAndUsesPrincipalForReturn() {
        String y = Dates.today().toString().substring(0, 4);
        // 開始記帳前持有 0050 1,000 股、均價 150；當初投入本金 120,000（之前有配息再投入）
        var opening = new TradeRequest("opening", broker.id, etf.id, y + "-01-02", new BigDecimal("1000"), new BigDecimal("150"),
                new BigDecimal("99"), null, null, null, null, null, new BigDecimal("120000"));
        Transaction t = f.investmentService.create(ME, opening);
        assertTrue(t.entries.isEmpty(), "不影響現金");
        eq("0", t.investment.fee);
        eq("100000", balance(broker));
        var p = HoldingCalculator.compute(f.ledger.investments(ME), null).get(broker.id, etf.id);
        eq("1000", p.held);
        eq("150000", p.cost);
        eq("120000", p.invested);
        eq("30000", p.priorGain);

        // 現價 192.35：未實現 42,350；報酬率 =（42,350 + 30,000）÷ 120,000 = 60.3%
        var h = f.investmentService.holdings(f.me, null).getFirst();
        eq("150", h.avgCost());
        eq("42350", h.unrealized());
        eq("60.3", h.returnPct());
        eq("60.3", f.investmentService.summary(f.me, null).returnPct());

        // 之後照常賣出，以期初均價計算已實現損益
        f.investmentService.create(ME, trade("sell", broker, etf, y + "-01-03", "100", "160", null, null, null));
        eq("1000", HoldingCalculator.compute(f.ledger.investments(ME), null).get(broker.id, etf.id).realized);

        // 沒填投入本金 → 等於股數 × 均價
        var noPrincipal = new TradeRequest("opening", broker.id, voo.id, y + "-01-02", new BigDecimal("2"), new BigDecimal("500"),
                null, null, null, null, null, null, null);
        f.investmentService.create(ME, noPrincipal);
        eq("1000", HoldingCalculator.compute(f.ledger.investments(ME), null).get(broker.id, voo.id).invested);
        assertThrows(ResponseStatusException.class, () -> f.investmentService.create(ME, new TradeRequest("opening", broker.id, etf.id, y + "-01-02",
                new BigDecimal("1"), new BigDecimal("1"), null, null, null, null, null, null, new BigDecimal("-1"))), "本金不能是負數");
    }
}
