package com.doaccounts.service;

import com.doaccounts.model.Account;
import com.doaccounts.model.Category;
import com.doaccounts.service.AccountService.AccountRequest;
import com.doaccounts.service.AccountService.CreditRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;

import static com.doaccounts.service.Fixture.ME;
import static com.doaccounts.service.Fixture.OTHER;
import static com.doaccounts.service.Fixture.expense;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 帳戶：餘額推導、幣別鎖定、開戶日期、刪除與封存、排序（SA FR-2）。 */
class AccountServiceTest {

    private final Fixture f = new Fixture();
    private final Category food;

    AccountServiceTest() {
        f.categoryService.ensureDefaults(ME);
        food = f.categories.stream().filter(c -> c.name.equals("餐飲")).findFirst().orElseThrow();
    }

    private static HttpStatus status(Runnable r) {
        return (HttpStatus) assertThrows(ResponseStatusException.class, r::run).getStatusCode();
    }

    private static AccountRequest req(String name, String kind, String currency, String opening, String date) {
        return new AccountRequest(name, kind, currency, new BigDecimal(opening), date, true, null, null);
    }

    @Test
    void balanceIsOpeningPlusEntriesAndOpeningCanChangeLater() {
        Account a = f.account(ME, "錢包", "cash", "TWD", "3000", "2026-01-01");
        f.txService.create(ME, expense(a.id, food.id, "120", "2026-11-09"));
        assertEquals(0, new BigDecimal("2880").compareTo(f.accountService.getWithBalance(ME, a.id).balance));
        Account updated = f.accountService.update(ME, a.id, req("錢包", "cash", "TWD", "5000", "2026-01-01"));
        assertEquals(0, new BigDecimal("4880").compareTo(updated.balance), "有交易後仍可改初始餘額，餘額跟著變（SA FR-2.2）");
        assertTrue(updated.hasTransactions);
    }

    @Test
    void currencyLockedOnceUsed() {
        Account a = f.account("國泰", "TWD");
        f.accountService.update(ME, a.id, req("國泰", "bank", "USD", "0", "2026-01-01"));
        f.accountService.update(ME, a.id, req("國泰", "bank", "TWD", "0", "2026-01-01"));
        f.txService.create(ME, expense(a.id, food.id, "1", "2026-11-09"));
        assertEquals(HttpStatus.CONFLICT, status(() -> f.accountService.update(ME, a.id, req("國泰", "bank", "USD", "0", "2026-01-01"))));
    }

    @Test
    void openingDateCannotPassEarliestTransaction() {
        Account a = f.account("國泰", "TWD");
        f.txService.create(ME, expense(a.id, food.id, "1", "2026-03-15"));
        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> f.accountService.update(ME, a.id, req("國泰", "bank", "TWD", "0", "2026-04-01")));
        assertEquals(HttpStatus.UNPROCESSABLE_CONTENT, e.getStatusCode());
        assertTrue(e.getReason().contains("2026-03-15"));
        f.accountService.update(ME, a.id, req("國泰", "bank", "TWD", "0", "2026-03-15"));
    }

    @Test
    void deleteOnlyWithoutTransactionsOtherwiseArchive() {
        Account empty = f.account("空帳戶", "TWD");
        f.accountService.delete(ME, empty.id);
        assertTrue(f.find(empty.id).isEmpty());

        Account used = f.account("國泰", "TWD");
        f.txService.create(ME, expense(used.id, food.id, "1", "2026-11-09"));
        assertEquals(HttpStatus.CONFLICT, status(() -> f.accountService.delete(ME, used.id)));
        f.accountService.setArchived(ME, used.id, true);
        assertFalse(f.accountService.list(ME, false).stream().anyMatch(x -> x.id.equals(used.id)), "封存的帳戶預設不列出");
        assertTrue(f.accountService.list(ME, true).stream().anyMatch(x -> x.id.equals(used.id)));
    }

    @Test
    void validationAndIsolation() {
        f.account("國泰", "TWD");
        assertEquals(HttpStatus.CONFLICT, status(() -> f.account("國泰", "TWD")), "同名");
        assertEquals(HttpStatus.BAD_REQUEST, status(() -> f.account(ME, "x", "rocket", "TWD", "0", "2026-01-01")));
        assertEquals(HttpStatus.BAD_REQUEST, status(() -> f.account(ME, "y", "bank", "EUR", "0", "2026-01-01")));
        assertEquals(HttpStatus.BAD_REQUEST, status(() -> f.account(ME, "z", "bank", "TWD", "1.5", "2026-01-01")), "TWD 初始餘額不能有小數");
        Account theirs = f.account(OTHER, "國泰", "bank", "TWD", "0", "2026-01-01");
        assertNotNull(theirs, "不同使用者可以同名");
        assertEquals(HttpStatus.NOT_FOUND, status(() -> f.accountService.get(ME, theirs.id)));
        assertEquals(1, f.accountService.list(ME, true).size());
    }

    @Test
    void creditFieldsOnlyForCreditCards() {
        Account card = f.accountService.create(ME, new AccountRequest("台新", "credit_card", "TWD", new BigDecimal("-18330"), "2026-01-01", true, null,
                new CreditRequest(new BigDecimal("150000"), 28, 15)));
        assertEquals(28, card.credit.statementDay);
        assertEquals(0, new BigDecimal("-18330").compareTo(card.balance), "信用卡欠款用負數（SA FR-2.8）");
        Account bank = f.accountService.create(ME, new AccountRequest("國泰", "bank", "TWD", BigDecimal.ZERO, "2026-01-01", true, null,
                new CreditRequest(new BigDecimal("1"), 1, 1)));
        assertNull(bank.credit);
        assertEquals(HttpStatus.BAD_REQUEST, status(() -> f.accountService.create(ME, new AccountRequest("x", "credit_card", "TWD", BigDecimal.ZERO,
                "2026-01-01", true, null, new CreditRequest(null, 32, null)))));
    }

    @Test
    void reorderWithinKind() {
        Account a = f.account("A", "TWD");
        Account b = f.account("B", "TWD");
        f.account(ME, "C", "cash", "TWD", "0", "2026-01-01");
        f.accountService.reorder(ME, "bank", List.of(b.id, a.id));
        assertEquals(List.of("B", "A"), f.accountService.list(ME, true).stream().filter(x -> x.kind.equals("bank")).map(x -> x.name).toList());
        assertEquals(HttpStatus.BAD_REQUEST, status(() -> f.accountService.reorder(ME, "bank", List.of(a.id))));
        assertEquals("cash", f.accountService.list(ME, true).get(0).kind, "依類型排序：現金在銀行前");
    }
}
