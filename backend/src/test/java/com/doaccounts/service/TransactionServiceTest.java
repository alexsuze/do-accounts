package com.doaccounts.service;

import com.doaccounts.model.Account;
import com.doaccounts.model.Category;
import com.doaccounts.model.Transaction;
import com.doaccounts.service.TransactionService.TransactionRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static com.doaccounts.service.Fixture.ME;
import static com.doaccounts.service.Fixture.OTHER;
import static com.doaccounts.service.Fixture.expense;
import static com.doaccounts.service.Fixture.income;
import static com.doaccounts.service.Fixture.transfer;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 記帳核心：分錄規則、各項檢查、版本衝突、收支統計、帳戶交易後餘額（SD 2.5、4.2）。 */
class TransactionServiceTest {

    private final Fixture f = new Fixture();
    private final Account cash = f.account("錢包", "TWD");
    private final Account bank = f.account("國泰", "TWD");
    private final Account usd = f.account("玉山外幣", "USD");
    private final Category food;
    private final Category salary;

    TransactionServiceTest() {
        f.categoryService.ensureDefaults(ME);
        food = f.categories.stream().filter(c -> c.name.equals("餐飲")).findFirst().orElseThrow();
        salary = f.categories.stream().filter(c -> c.name.equals("薪資")).findFirst().orElseThrow();
    }

    private static HttpStatus status(Runnable r) {
        return (HttpStatus) assertThrows(ResponseStatusException.class, r::run).getStatusCode();
    }

    private static BigDecimal bd(String s) {
        return new BigDecimal(s);
    }

    @Test
    void expenseAndIncomeHaveOneSignedMainEntry() {
        Transaction e = f.txService.create(ME, expense(cash.id, food.id, "120", "2026-11-09"));
        assertEquals(1, e.entries.size());
        assertEquals(0, bd("-120").compareTo(e.entries.get(0).amount));
        assertEquals("main", e.entries.get(0).role);
        assertEquals(food.id, e.categoryId);

        Transaction i = f.txService.create(ME, income(bank.id, salary.id, "68500", "2026-11-05"));
        assertEquals(0, bd("68500").compareTo(i.entries.get(0).amount));

        assertEquals(0, bd("-120").compareTo(f.accountService.getWithBalance(ME, cash.id).balance), "餘額＝初始＋分錄");
        assertEquals(0, bd("68500").compareTo(f.accountService.getWithBalance(ME, bank.id).balance));
    }

    @Test
    void sameCurrencyTransfer() {
        Transaction t = f.txService.create(ME, transfer(bank.id, cash.id, "1000", null, null, "2026-11-09"));
        assertEquals("轉帳", t.title, "沒填項目名稱時預設為「轉帳」");
        assertEquals(2, t.entries.size());
        assertEquals(0, bd("-1000").compareTo(TransactionService.net(t, bank.id)));
        assertEquals(0, bd("1000").compareTo(TransactionService.net(t, cash.id)));
        assertEquals(HttpStatus.BAD_REQUEST, status(() -> f.txService.create(ME, transfer(bank.id, cash.id, "1000", "999", null, "2026-11-09"))),
                "同幣別轉入金額必須等於轉出");
        assertEquals(HttpStatus.BAD_REQUEST, status(() -> f.txService.create(ME, transfer(bank.id, bank.id, "1", null, null, "2026-11-09"))),
                "轉出轉入不能相同");
    }

    @Test
    void crossCurrencyTransferWithFee() {
        assertEquals(HttpStatus.BAD_REQUEST, status(() -> f.txService.create(ME, transfer(bank.id, usd.id, "32415", null, null, "2026-11-08"))),
                "跨幣別必須填轉入金額");
        Transaction t = f.txService.create(ME, transfer(bank.id, usd.id, "32415", "1000.00", "30", "2026-11-08"));
        assertEquals(3, t.entries.size());
        assertEquals(0, bd("-32445").compareTo(TransactionService.net(t, bank.id)), "手續費記在轉出帳戶");
        assertEquals(0, bd("1000").compareTo(TransactionService.net(t, usd.id)));
        Transaction.Entry fee = t.entries.stream().filter(e -> e.role.equals("fee")).findFirst().orElseThrow();
        assertEquals(f.categoryService.system(ME, "expense", "fee").id, fee.categoryId, "預設用系統分類「手續費」");
    }

    @Test
    void currencyDigitsAreEnforced() {
        assertEquals(HttpStatus.BAD_REQUEST, status(() -> f.txService.create(ME, expense(cash.id, food.id, "12.5", "2026-11-09"))), "TWD 不能有小數");
        f.txService.create(ME, expense(usd.id, food.id, "12.50", "2026-11-09"));
        assertEquals(HttpStatus.BAD_REQUEST, status(() -> f.txService.create(ME, expense(usd.id, food.id, "1.234", "2026-11-09"))), "USD 最多 2 位");
        assertEquals(HttpStatus.BAD_REQUEST, status(() -> f.txService.create(ME, expense(cash.id, food.id, "0", "2026-11-09"))), "金額必須大於 0");
        assertEquals(HttpStatus.BAD_REQUEST, status(() -> f.txService.create(ME, expense(cash.id, food.id, "-5", "2026-11-09"))));
    }

    @Test
    void dateMustBeValidAndNotBeforeOpeningDate() {
        assertEquals(HttpStatus.BAD_REQUEST, status(() -> f.txService.create(ME, expense(cash.id, food.id, "1", "2026/11/09"))));
        assertEquals(HttpStatus.BAD_REQUEST, status(() -> f.txService.create(ME, expense(cash.id, food.id, "1", "2026-02-30"))));
        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> f.txService.create(ME, expense(cash.id, food.id, "45", "2025-12-30")));
        assertEquals(HttpStatus.UNPROCESSABLE_CONTENT, e.getStatusCode());
        assertTrue(e.getReason().contains("2026-01-01"));
        assertEquals(HttpStatus.UNPROCESSABLE_CONTENT, status(() -> f.txService.create(ME, transfer(bank.id, cash.id, "1", null, null, "2025-12-31"))),
                "轉帳兩邊都要檢查");
    }

    @Test
    void archivedAccountCannotBeUsed() {
        f.accountService.setArchived(ME, cash.id, true);
        assertEquals(HttpStatus.UNPROCESSABLE_CONTENT, status(() -> f.txService.create(ME, expense(cash.id, food.id, "1", "2026-11-09"))));
    }

    @Test
    void otherUsersDataIsNotFound() {
        Account theirs = f.account(OTHER, "他的錢包", "cash", "TWD", "0", "2026-01-01");
        Category theirFood = f.category(OTHER, "expense", "他的餐飲");
        assertEquals(HttpStatus.NOT_FOUND, status(() -> f.txService.create(ME, expense(theirs.id, food.id, "1", "2026-11-09"))));
        assertEquals(HttpStatus.NOT_FOUND, status(() -> f.txService.create(ME, expense(cash.id, theirFood.id, "1", "2026-11-09"))));
        Transaction mine = f.txService.create(ME, expense(cash.id, food.id, "1", "2026-11-09"));
        assertEquals(HttpStatus.NOT_FOUND, status(() -> f.txService.get(OTHER, mine.id)));
        assertEquals(HttpStatus.NOT_FOUND, status(() -> f.txService.delete(OTHER, mine.id)));
        assertEquals(HttpStatus.NOT_FOUND, status(() -> f.txService.bulkDelete(OTHER, List.of(mine.id))));
    }

    @Test
    void categoryKindMustMatch() {
        assertEquals(HttpStatus.BAD_REQUEST, status(() -> f.txService.create(ME, expense(cash.id, salary.id, "1", "2026-11-09"))));
        assertEquals(HttpStatus.BAD_REQUEST, status(() -> f.txService.create(ME, income(cash.id, food.id, "1", "2026-11-09"))));
    }

    @Test
    void updateRequiresCurrentVersion() {
        Transaction t = f.txService.create(ME, expense(cash.id, food.id, "120", "2026-11-09"));
        long v = t.version;
        TransactionRequest stale = new TransactionRequest("expense", "2026-11-09", "午餐", null, null, food.id, cash.id, bd("150"), null, null, null, null, v - 1);
        assertEquals(HttpStatus.CONFLICT, status(() -> f.txService.update(ME, t.id, stale)));
        TransactionRequest ok = new TransactionRequest("expense", "2026-11-09", "午餐", null, null, food.id, cash.id, bd("150"), null, null, null, null, v);
        Transaction updated = f.txService.update(ME, t.id, ok);
        assertEquals(0, bd("-150").compareTo(updated.entries.get(0).amount));
        assertEquals(HttpStatus.CONFLICT, status(() -> f.txService.update(ME, t.id, ok)), "同一版本號不能用兩次");

        t.kind = "investment";
        assertEquals(HttpStatus.UNPROCESSABLE_CONTENT, status(() -> f.txService.delete(ME, t.id)));
    }

    @Test
    void pnlCountsMainAndFeeOnly() {
        Map<String, String> cur = Map.of(bank.id, "TWD", usd.id, "USD", cash.id, "TWD");
        Transaction t = f.txService.create(ME, transfer(bank.id, usd.id, "32415", "1000", "30", "2026-11-08"));
        Map<String, TransactionService.Totals> p = TransactionService.pnl(t, cur);
        assertEquals(1, p.size(), "轉帳本身不計收支");
        assertEquals(0, bd("30").compareTo(p.get("TWD").expense()), "手續費算支出");
    }

    @Test
    void listSummaryDailyTotalsAndPaging() {
        f.txService.create(ME, expense(cash.id, food.id, "120", "2026-11-09"));
        f.txService.create(ME, expense(cash.id, food.id, "80", "2026-11-09"));
        f.txService.create(ME, income(bank.id, salary.id, "68500", "2026-11-05"));
        f.txService.create(ME, expense(usd.id, food.id, "5.50", "2026-11-06"));
        f.txService.create(ME, transfer(bank.id, cash.id, "500", null, null, "2026-11-07"));
        f.txService.create(ME, expense(cash.id, food.id, "999", "2026-10-31"));

        TransactionService.Page p = f.txService.list(ME, "2026-11-01", "2026-11-30", null, null, null, null, null, 0, 2);
        assertEquals(5, p.total());
        assertEquals(2, p.items().size());
        assertEquals("2026-11-09", p.items().get(0).date, "新到舊");
        assertEquals(0, bd("68500").compareTo(p.summary().get("TWD").income()));
        assertEquals(0, bd("200").compareTo(p.summary().get("TWD").expense()));
        assertEquals(0, bd("5.50").compareTo(p.summary().get("USD").expense()), "外幣分開統計");
        assertEquals(0, bd("200").compareTo(p.dailyTotals().get(0).byCurrency().get("TWD").expense()));

        TransactionService.Page child = f.txService.list(ME, null, null, null, null, List.of(food.id), null, null, 0, 50);
        assertEquals(4, child.total(), "只篩出餐飲的 4 筆支出（轉帳、薪資不在內）");
        assertEquals(HttpStatus.BAD_REQUEST, status(() -> f.txService.list(ME, null, null, null, null, null, null, null, 0, 999)));
    }

    @Test
    void filteringParentCategoryIncludesChildren() {
        Category lunch = f.categories.stream().filter(c -> c.name.equals("午餐")).findFirst().orElseThrow();
        Category transport = f.categories.stream().filter(c -> c.name.equals("交通")).findFirst().orElseThrow();
        f.txService.create(ME, expense(cash.id, lunch.id, "100", "2026-11-09"));
        f.txService.create(ME, expense(cash.id, food.id, "50", "2026-11-09"));
        f.txService.create(ME, expense(cash.id, transport.id, "30", "2026-11-09"));
        assertEquals(2, f.txService.list(ME, null, null, null, null, List.of(food.id), null, null, 0, 50).total());
        assertEquals(1, f.txService.list(ME, null, null, null, null, List.of(lunch.id), null, null, 0, 50).total());
    }

    @Test
    void bulkUpdateChecksCategoryKind() {
        Transaction a = f.txService.create(ME, expense(cash.id, food.id, "1", "2026-11-09"));
        Transaction b = f.txService.create(ME, income(bank.id, salary.id, "1", "2026-11-09"));
        Category drink = f.categories.stream().filter(c -> c.name.equals("飲料")).findFirst().orElseThrow();
        assertEquals(HttpStatus.UNPROCESSABLE_CONTENT, status(() -> f.txService.bulkUpdate(ME, List.of(a.id, b.id), drink.id, null, null)));
        assertEquals(1, f.txService.bulkUpdate(ME, List.of(a.id), drink.id, List.of("#聚餐", "公司"), null));
        assertEquals(drink.id, a.categoryId);
        assertEquals(List.of("聚餐", "公司"), a.tags, "標籤去掉 # 並保留順序");
        assertEquals(2, f.txService.bulkDelete(ME, List.of(a.id, b.id)));
        assertTrue(f.transactions.isEmpty());
    }

    @Test
    void accountLedgerHasRunningBalance() {
        Account wallet = f.account(ME, "零錢包", "cash", "TWD", "3000", "2026-01-01");
        f.txService.create(ME, expense(wallet.id, food.id, "120", "2026-11-01"));
        f.txService.create(ME, transfer(bank.id, wallet.id, "1000", null, null, "2026-11-02"));
        f.txService.create(ME, expense(wallet.id, food.id, "80", "2026-11-03"));
        TransactionService.AccountLedger l = f.txService.accountLedger(ME, wallet.id, 0, 50);
        assertEquals(3, l.total());
        assertEquals(0, bd("3800").compareTo(l.account().balance));
        assertEquals(0, bd("3800").compareTo(l.items().get(0).balanceAfter()), "最新一筆的交易後餘額＝目前餘額");
        assertEquals(0, bd("2880").compareTo(l.items().get(2).balanceAfter()), "最舊一筆：3000 − 120");
        assertEquals(0, bd("1000").compareTo(l.items().get(1).amount()));
    }

    @Test
    void suggestReturnsMostCommonCategoryAndAccount() {
        Category lunch = f.categories.stream().filter(c -> c.name.equals("午餐")).findFirst().orElseThrow();
        f.txService.create(ME, expense(cash.id, lunch.id, "100", "2026-11-01"));
        f.txService.create(ME, expense(cash.id, lunch.id, "110", "2026-11-02"));
        f.txService.create(ME, expense(bank.id, food.id, "120", "2026-11-03"));
        List<TransactionService.Suggestion> s = f.txService.suggest(ME, "午");
        assertEquals(1, s.size());
        assertEquals(lunch.id, s.get(0).categoryId());
        assertEquals(cash.id, s.get(0).accountId());
        assertEquals(3, s.get(0).count());
    }
}
