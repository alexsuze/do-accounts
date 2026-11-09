package com.doaccounts.service;

import com.doaccounts.model.Account;
import com.doaccounts.model.Category;
import com.doaccounts.service.BudgetService.BudgetRequest;
import com.doaccounts.service.ReportService.NetWorthPoint;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.YearMonth;

import static com.doaccounts.service.Fixture.ME;
import static com.doaccounts.service.Fixture.OTHER;
import static com.doaccounts.service.Fixture.expense;
import static com.doaccounts.service.Fixture.income;
import static com.doaccounts.service.Fixture.transfer;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 報表與總覽：淨資產趨勢、收支結構、每日收支、分類趨勢，外幣換算（SA FR-6、FR-7）。 */
class ReportServiceTest {

    private final Fixture f = new Fixture();
    private final YearMonth now = YearMonth.from(Dates.today());
    private final Category food;
    private final Category lunch;
    private final Category traffic;
    private final Category salary;

    ReportServiceTest() {
        f.categoryService.ensureDefaults(ME);
        food = cat("餐飲");
        lunch = cat("午餐");
        traffic = cat("交通");
        salary = cat("薪資");
    }

    private Category cat(String name) {
        return f.categories.stream().filter(c -> c.userId.equals(ME) && c.name.equals(name)).findFirst().orElseThrow();
    }

    private static void eq(String expected, BigDecimal actual) {
        eq(expected, actual, "");
    }

    private static void eq(String expected, BigDecimal actual, String msg) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), () -> msg + " expected " + expected + " but was " + actual);
    }

    private String day(YearMonth m, int d) {
        return m.atDay(d).toString();
    }

    @Test
    void netWorthByMonthEndWithForeignAccountsAndLiabilities() {
        YearMonth m0 = now.minusMonths(2);
        Account cash = f.account(ME, "錢包", "cash", "TWD", "1000", day(m0, 1));
        Account card = f.account(ME, "信用卡", "credit_card", "TWD", "0", day(m0, 1));
        Account usd = f.account(ME, "美元", "bank", "USD", "100", day(now.minusMonths(1), 1)); // 上個月才開戶
        f.rate("USD", day(m0, 1), "30");
        f.rate("USD", day(now, 1), "32");
        f.txService.create(ME, expense(card.id, food.id, "300", day(m0, 2)));
        f.txService.create(ME, income(cash.id, salary.id, "500", day(now.minusMonths(1), 2)));

        var nw = f.reportService.netWorth(f.me, 3).points();
        assertEquals(3, nw.size());
        NetWorthPoint p0 = nw.get(0);
        eq("1000", p0.assets());
        eq("-300", p0.liabilities());
        eq("700", p0.netWorth());
        // 上個月：錢包 1500 ＋ 美元 100 × 30（月底匯率）
        eq("4500", nw.get(1).assets());
        // 本月：美元用今天的匯率 32
        eq("4700", nw.get(2).assets());
        eq("4400", nw.get(2).netWorth());

        // 全部期間、或要求的月數比開戶以來還多：都從最早開戶月份起
        assertEquals(3, f.reportService.netWorth(f.me, 0).points().size());
        assertEquals(3, f.reportService.netWorth(f.me, 12).points().size());
        assertTrue(f.reportService.netWorth(Fixture.user(OTHER), 6).points().isEmpty(), "別人看不到我的資料");
    }

    @Test
    void breakdownGroupsChildrenUnderParentAndExcludesTransfers() {
        Account cash = f.account(ME, "錢包", "cash", "TWD", "0", "2020-01-01");
        Account usd = f.account(ME, "美元", "bank", "USD", "0", "2020-01-01");
        f.rate("USD", "2020-01-01", "30");
        String d = day(now, 1);
        f.txService.create(ME, expense(cash.id, food.id, "100", d));
        f.txService.create(ME, expense(cash.id, lunch.id, "200", d));
        f.txService.create(ME, expense(usd.id, traffic.id, "10", d)); // 300
        f.txService.create(ME, transfer(cash.id, usd.id, "300", "10", null, d));
        f.txService.create(ME, income(cash.id, salary.id, "1000", d));

        var b = f.reportService.breakdown(f.me, d, d, "expense");
        eq("600", b.total());
        assertEquals(2, b.items().size());
        var foodItem = b.items().stream().filter(i -> i.categoryId().equals(food.id)).findFirst().orElseThrow();
        eq("300", foodItem.amount());
        eq("50.0", foodItem.percentage());
        assertEquals(2, foodItem.children().size(), "子類午餐＋大類本身");
        assertEquals(lunch.id, foodItem.children().getFirst().categoryId());
        var trafficItem = b.items().stream().filter(i -> i.categoryId().equals(traffic.id)).findFirst().orElseThrow();
        assertTrue(trafficItem.children().isEmpty());

        var inc = f.reportService.breakdown(f.me, d, d, "income");
        eq("1000", inc.total());
    }

    @Test
    void dailyAndCategoryTrend() {
        Account cash = f.account(ME, "錢包", "cash", "TWD", "0", "2020-01-01");
        f.txService.create(ME, expense(cash.id, lunch.id, "120", day(now, 1)));
        f.txService.create(ME, expense(cash.id, food.id, "80", day(now, 1)));
        f.txService.create(ME, income(cash.id, salary.id, "5000", day(now, 1)));
        f.txService.create(ME, expense(cash.id, food.id, "900", day(now.minusMonths(1), 3)));
        f.txService.create(ME, expense(cash.id, traffic.id, "50", day(now.minusMonths(1), 3)));

        var daily = f.reportService.daily(f.me, now.toString());
        assertEquals(now.lengthOfMonth(), daily.days().size());
        eq("200", daily.days().getFirst().expense());
        eq("5000", daily.income());
        eq("200", daily.expense());
        assertEquals(day(now, 1), daily.maxExpenseDay().date());

        f.budgetService.upsert(f.me, new BudgetRequest(now.minusMonths(1).toString(), food.id, new BigDecimal("1000"), true));
        var trend = f.reportService.categoryTrend(f.me, food.id, 3);
        assertEquals(3, trend.points().size());
        eq("0", trend.points().get(0).amount());
        assertNull(trend.points().get(0).budget());
        eq("900", trend.points().get(1).amount());
        eq("1000", trend.points().get(1).budget());
        eq("200", trend.points().get(2).amount());
        eq("1000", trend.points().get(2).budget(), "循環預算沿用");
        eq("450", trend.average(), "平均不含本月：(0 + 900) / 2");
    }

    @Test
    void dashboardConvertsForeignAccountsAndShowsBudget() {
        Account cash = f.account(ME, "錢包", "cash", "TWD", "1000", "2020-01-01");
        Account usd = f.account(ME, "美元", "bank", "USD", "100", "2020-01-01");
        f.account(ME, "信用卡", "credit_card", "TWD", "-500", "2020-01-01");
        f.rate("USD", "2020-01-01", "30");
        f.rate("USD", day(now, 1), "32");
        f.txService.create(ME, expense(usd.id, food.id, "10", day(now, 1)));
        f.budgetService.upsert(f.me, new BudgetRequest(now.toString(), food.id, new BigDecimal("1000"), false));

        var d = f.dashboardService.get(f.me);
        eq("3880", d.assets()); // 1000 + 90 × 32
        eq("-500", d.liabilities());
        eq("3380", d.netWorth());
        assertEquals(day(now, 1), d.ratesDate());
        eq("320", d.monthTotals().expense());
        assertEquals(1, d.budget().top().size());
        eq("32.0", d.budget().top().getFirst().percentage());
        assertEquals(80, d.budget().warnPct());

        // 總覽隱藏的帳戶不計入資產負債與淨資產趨勢，但收支照算
        f.accountService.update(ME, usd.id, new com.doaccounts.service.AccountService.AccountRequest(usd.name, usd.kind, usd.currency,
                usd.openingBalance, usd.openingDate, false, null, null));
        var h = f.dashboardService.get(f.me);
        eq("1000", h.assets());
        eq("500", h.netWorth());
        assertEquals(1, h.hiddenAccounts());
        eq("320", h.monthTotals().expense());
        eq("500", f.reportService.netWorth(f.me, 1).points().getLast().netWorth());
    }
}
