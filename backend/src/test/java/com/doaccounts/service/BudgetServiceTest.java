package com.doaccounts.service;

import com.doaccounts.model.Account;
import com.doaccounts.model.Budget;
import com.doaccounts.model.Category;
import com.doaccounts.service.BudgetService.BudgetItem;
import com.doaccounts.service.BudgetService.BudgetRequest;
import com.doaccounts.service.BudgetService.BudgetView;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

import static com.doaccounts.service.Fixture.ME;
import static com.doaccounts.service.Fixture.expense;
import static com.doaccounts.service.Fixture.transfer;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 預算：循環沿用、停止標記、大類含子類、外幣換算、推估、複製（SA FR-5）。 */
class BudgetServiceTest {

    private final Fixture f = new Fixture();
    private final YearMonth now = YearMonth.from(Dates.today());
    private final String thisMonth = now.toString();
    private final Account cash = f.account(ME, "錢包", "cash", "TWD", "0", "2020-01-01");
    private final Account usd = f.account(ME, "美元", "bank", "USD", "0", "2020-01-01");
    private final Category food;
    private final Category lunch;
    private final Category traffic;
    private final Category salary;

    BudgetServiceTest() {
        f.categoryService.ensureDefaults(ME);
        food = cat("餐飲");
        lunch = cat("午餐");
        traffic = cat("交通");
        salary = cat("薪資");
        f.rate("USD", "2020-01-01", "30");
    }

    private Category cat(String name) {
        return f.categories.stream().filter(c -> c.name.equals(name)).findFirst().orElseThrow();
    }

    private static void eq(String expected, BigDecimal actual) {
        eq(expected, actual, "");
    }

    private static void eq(String expected, BigDecimal actual, String msg) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), () -> msg + " expected " + expected + " but was " + actual);
    }

    private Budget set(String month, Category c, String amount, boolean recurring) {
        return f.budgetService.upsert(f.me, new BudgetRequest(month, c == null ? null : c.id, new BigDecimal(amount), recurring));
    }

    private BudgetItem item(BudgetView v, Category c) {
        return v.items().stream().filter(i -> i.categoryId().equals(c.id)).findFirst().orElse(null);
    }

    @Test
    void recurringBudgetCarriesForwardUntilOverriddenOrStopped() {
        String m1 = now.minusMonths(3).toString();
        String m2 = now.minusMonths(2).toString();
        String m3 = now.minusMonths(1).toString();
        set(m1, food, "10000", true);
        set(m1, traffic, "3000", false);

        BudgetView v2 = f.budgetService.view(f.me, m2);
        assertTrue(item(v2, food).inherited(), "循環預算沿用");
        eq("10000", item(v2, food).amount());
        assertNull(item(v2, traffic), "非循環不沿用");

        set(m2, food, "12000", false); // 單月覆寫：m2 用 12000，之後不再沿用（最新一筆不是循環）
        eq("12000", item(f.budgetService.view(f.me, m2), food).amount());
        assertNull(item(f.budgetService.view(f.me, m3), food));

        set(m2, food, "12000", true);
        f.budgetService.delete(f.me, m3, food.id); // 從 m3 起停止
        assertNull(item(f.budgetService.view(f.me, m3), food));
        assertNull(item(f.budgetService.view(f.me, thisMonth), food));
        eq("12000", item(f.budgetService.view(f.me, m2), food).amount(), "之前的月份不受影響");

        set(thisMonth, food, "9000", false); // 停止後還能再設定
        eq("9000", item(f.budgetService.view(f.me, thisMonth), food).amount());
        assertThrows(ResponseStatusException.class, () -> f.budgetService.delete(f.me, m3, food.id), "已經沒有預算");
    }

    @Test
    void spentIncludesChildrenFeesAndForeignCurrencyButNotTransfersOrIncome() {
        String d = now.atDay(1).toString();
        set(thisMonth, food, "10000", false);
        set(thisMonth, null, "50000", false);
        f.txService.create(ME, expense(cash.id, food.id, "100", d));
        f.txService.create(ME, expense(cash.id, lunch.id, "200", d));     // 子類算進大類（FR-5.2）
        f.txService.create(ME, expense(usd.id, lunch.id, "10", d));       // 10 USD × 30 = 300
        f.txService.create(ME, expense(cash.id, traffic.id, "50", d));
        f.txService.create(ME, transfer(cash.id, usd.id, "300", "10", "15", d)); // 轉帳不算，手續費算支出
        f.txService.create(ME, Fixture.income(cash.id, salary.id, "60000", d));

        BudgetView v = f.budgetService.view(f.me, thisMonth);
        eq("600", item(v, food).spent());
        eq("9400", item(v, food).remaining());
        eq("6.0", item(v, food).percentage());
        eq("665", v.total().spent()); // 100 + 200 + 300 + 50 + 手續費 15
        assertFalse(v.total().derived());

        // 交通沒有預算 → 列在「未設預算的支出」；有預算的餐飲不列
        assertTrue(v.unbudgeted().stream().anyMatch(u -> u.categoryId().equals(traffic.id)));
        assertFalse(v.unbudgeted().stream().anyMatch(u -> u.categoryId().equals(food.id)));
        assertEquals(BudgetService.HISTORY_MONTHS, v.history().size());
        eq("50000", v.history().getLast().budget());
    }

    @Test
    void projectionUsesElapsedDaysAndPastMonthsAreFinal() {
        String past = now.minusMonths(1).toString();
        set(past, food, "1000", false);
        f.txService.create(ME, expense(cash.id, food.id, "800", now.minusMonths(1).atDay(1).toString()));
        BudgetView v = f.budgetService.view(f.me, past);
        assertEquals(v.daysInMonth(), v.daysElapsed());
        eq("800", item(v, food).projected());

        set(thisMonth, food, "1000", false);
        f.txService.create(ME, expense(cash.id, food.id, "100", now.atDay(1).toString()));
        BudgetView cur = f.budgetService.view(f.me, thisMonth);
        int elapsed = Dates.today().getDayOfMonth();
        BigDecimal expected = new BigDecimal(100L * cur.daysInMonth()).divide(BigDecimal.valueOf(elapsed), 0, java.math.RoundingMode.HALF_UP);
        eq(expected.toPlainString(), item(cur, food).projected());

        BudgetView future = f.budgetService.view(f.me, now.plusMonths(1).toString());
        assertEquals(0, future.daysElapsed());
    }

    @Test
    void derivedTotalSumsTopLevelBudgetsWithoutDoubleCounting() {
        set(thisMonth, food, "10000", false);
        set(thisMonth, lunch, "4000", false); // 子類，大類已有預算 → 不重複加
        set(thisMonth, traffic, "3000", false);
        BudgetView v = f.budgetService.view(f.me, thisMonth);
        assertTrue(v.total().derived());
        eq("13000", v.total().amount());
        assertEquals(List.of(food.id, lunch.id, traffic.id), v.items().stream().map(BudgetItem::categoryId).toList(), "依分類順序，子類接在大類後");
    }

    @Test
    void validationAndCopy() {
        assertThrows(ResponseStatusException.class, () -> set(thisMonth, salary, "100", false), "收入分類不能設預算");
        assertThrows(ResponseStatusException.class, () -> set("2026-13", food, "100", false));
        assertThrows(ResponseStatusException.class, () -> set(thisMonth, food, "12.5", false), "TWD 不能有小數");
        assertThrows(ResponseStatusException.class, () -> set(thisMonth, food, "0", false));

        String prev = now.minusMonths(1).toString();
        set(prev, food, "10000", true);
        set(prev, traffic, "3000", false);
        set(thisMonth, traffic, "2500", false);
        assertEquals(1, f.budgetService.copy(f.me, prev, thisMonth), "本月已設定的交通不覆蓋");
        BudgetView v = f.budgetService.view(f.me, thisMonth);
        eq("10000", item(v, food).amount());
        eq("2500", item(v, traffic).amount());
        assertThrows(ResponseStatusException.class, () -> f.budgetService.copy(f.me, now.minusMonths(5).toString(), thisMonth), "來源沒有預算");

        f.budgetService.delete(f.me, thisMonth, food.id); // 停止後，複製會蓋掉停止標記
        assertNull(item(f.budgetService.view(f.me, thisMonth), food));
        assertEquals(1, f.budgetService.copy(f.me, prev, thisMonth));
        eq("10000", item(f.budgetService.view(f.me, thisMonth), food).amount());

        set(thisMonth, food, "11000", false); // 同月同分類是覆蓋
        assertEquals(1, f.budgets.stream().filter(b -> b.month.equals(thisMonth) && food.id.equals(b.categoryId)).count());
    }
}
