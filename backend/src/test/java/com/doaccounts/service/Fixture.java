package com.doaccounts.service;

import com.doaccounts.model.Account;
import com.doaccounts.model.Category;
import com.doaccounts.model.Transaction;
import com.doaccounts.repository.AccountRepository;
import com.doaccounts.repository.CategoryRepository;
import com.doaccounts.repository.TransactionRepository;
import com.doaccounts.service.AccountService.AccountRequest;
import com.doaccounts.service.TransactionService.TransactionRequest;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 測試用的記憶體資料：repository 與 LedgerQueries 都用 mock 接到 List 上，不需要 MongoDB。
 * LedgerQueries 的查詢以簡化版邏輯模擬（依 user、帳戶、日期篩選）。
 */
class Fixture {

    static final String ME = "u1";
    static final String OTHER = "u2";

    final List<Account> accounts = new ArrayList<>();
    final List<Category> categories = new ArrayList<>();
    final List<Transaction> transactions = new ArrayList<>();

    final AccountRepository accountRepo = mock(AccountRepository.class);
    final CategoryRepository categoryRepo = mock(CategoryRepository.class);
    final TransactionRepository txRepo = mock(TransactionRepository.class);
    final LedgerQueries ledger = mock(LedgerQueries.class);

    final AccountService accountService = new AccountService(accountRepo, ledger);
    final CategoryService categoryService = new CategoryService(categoryRepo, ledger);
    final TransactionService txService = new TransactionService(txRepo, ledger, accountService, categoryService);
    final TransactionBatchService batchService = new TransactionBatchService(txRepo, ledger, txService);

    private int seq;

    Fixture() {
        when(accountRepo.save(any(Account.class))).thenAnswer(inv -> store(accounts, inv.getArgument(0), "a"));
        when(accountRepo.findById(anyString())).thenAnswer(inv -> accounts.stream().filter(a -> a.id.equals(inv.getArgument(0))).findFirst());
        when(accountRepo.findByUserId(anyString())).thenAnswer(inv -> accounts.stream().filter(a -> a.userId.equals(inv.getArgument(0))).toList());
        doAnswer(inv -> accounts.remove((Account) inv.getArgument(0))).when(accountRepo).delete(any(Account.class));

        when(categoryRepo.save(any(Category.class))).thenAnswer(inv -> store(categories, inv.getArgument(0), "c"));
        when(categoryRepo.findById(anyString())).thenAnswer(inv -> categories.stream().filter(c -> c.id.equals(inv.getArgument(0))).findFirst());
        when(categoryRepo.findByUserId(anyString())).thenAnswer(inv -> categories.stream().filter(c -> c.userId.equals(inv.getArgument(0))).toList());
        when(categoryRepo.countByUserId(anyString())).thenAnswer(inv -> categories.stream().filter(c -> c.userId.equals(inv.getArgument(0))).count());
        doAnswer(inv -> categories.remove((Category) inv.getArgument(0))).when(categoryRepo).delete(any(Category.class));
        doAnswer(inv -> { categories.removeAll((Collection<?>) inv.getArgument(0)); return null; }).when(categoryRepo).deleteAll(anyCollection());

        when(txRepo.save(any(Transaction.class))).thenAnswer(inv -> {
            Transaction t = inv.getArgument(0);
            t.version = t.version == null ? 0L : t.version + 1;
            if (t.id != null) transactions.replaceAll(x -> x.id.equals(t.id) ? t : x); // 同 id 的新物件取代舊的（像資料庫一樣）
            return store(transactions, t, "t");
        });
        when(txRepo.findById(anyString())).thenAnswer(inv -> transactions.stream().filter(t -> t.id.equals(inv.getArgument(0))).findFirst());
        doAnswer(inv -> transactions.remove((Transaction) inv.getArgument(0))).when(txRepo).delete(any(Transaction.class));
        doAnswer(inv -> { transactions.removeAll((Collection<?>) inv.getArgument(0)); return null; }).when(txRepo).deleteAll(anyCollection());

        when(ledger.sumByAccount(anyString())).thenAnswer(inv -> {
            Map<String, BigDecimal> m = new HashMap<>();
            mine(inv.getArgument(0)).forEach(t -> t.entries.forEach(e -> m.merge(e.accountId, e.amount, BigDecimal::add)));
            return m;
        });
        when(ledger.hasTransactions(anyString(), anyString())).thenAnswer(inv -> !byAccount(inv.getArgument(0), inv.getArgument(1)).isEmpty());
        when(ledger.earliestDate(anyString(), anyString())).thenAnswer(inv -> byAccount(inv.getArgument(0), inv.getArgument(1)).stream().map(t -> t.date).findFirst());
        when(ledger.byAccount(anyString(), anyString())).thenAnswer(inv -> byAccount(inv.getArgument(0), inv.getArgument(1)));
        when(ledger.findByIds(anyString(), anyCollection())).thenAnswer(inv -> mine(inv.getArgument(0)).stream().filter(t -> ((Collection<?>) inv.getArgument(1)).contains(t.id)).toList());
        when(ledger.countUsingCategories(anyString(), anyCollection())).thenAnswer(inv -> mine(inv.getArgument(0)).stream()
                .filter(t -> ((Collection<?>) inv.getArgument(1)).contains(t.categoryId)).count());
        doAnswer(inv -> {
            Collection<?> from = inv.getArgument(1);
            mine(inv.getArgument(0)).stream().filter(t -> from.contains(t.categoryId)).forEach(t -> t.categoryId = inv.getArgument(2));
            return null;
        }).when(ledger).moveCategories(anyString(), anyCollection(), anyString());
        when(ledger.search(anyString(), any())).thenAnswer(inv -> {
            LedgerQueries.Filter f = inv.getArgument(1);
            return newestFirst(mine(inv.getArgument(0))).stream()
                    .filter(t -> f.from() == null || t.date.compareTo(f.from()) >= 0)
                    .filter(t -> f.to() == null || t.date.compareTo(f.to()) <= 0)
                    .filter(t -> f.kinds() == null || f.kinds().isEmpty() || f.kinds().contains(t.kind))
                    .filter(t -> f.accountIds() == null || f.accountIds().isEmpty() || t.entries.stream().anyMatch(e -> f.accountIds().contains(e.accountId)))
                    .filter(t -> f.categoryIds() == null || f.categoryIds().isEmpty() || f.categoryIds().contains(t.categoryId))
                    .toList();
        });
        doAnswer(inv -> {
            Transaction o = inv.getArgument(0);
            transactions.removeIf(t -> t.id.equals(o.id));
            transactions.add(o);
            return null;
        }).when(ledger).restore(any(Transaction.class));
        doAnswer(inv -> {
            Collection<?> ids = inv.getArgument(1);
            transactions.removeIf(t -> t.userId.equals(inv.getArgument(0)) && ids.contains(t.id));
            return null;
        }).when(ledger).removeByIds(anyString(), anyCollection());
        when(ledger.recent(anyString(), anyInt())).thenAnswer(inv -> newestFirst(mine(inv.getArgument(0))).stream().limit((int) inv.getArgument(1)).toList());
        when(ledger.titleStartsWith(anyString(), anyString(), anyInt())).thenAnswer(inv -> newestFirst(mine(inv.getArgument(0))).stream()
                .filter(t -> t.title.startsWith(inv.getArgument(1))).toList());
    }

    private <T> T store(List<T> list, T obj, String prefix) {
        try {
            var f = obj.getClass().getField("id");
            if (f.get(obj) == null) { f.set(obj, prefix + (++seq)); list.add(obj); }
            return obj;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private List<Transaction> mine(String userId) {
        return transactions.stream().filter(t -> t.userId.equals(userId)).toList();
    }

    private List<Transaction> byAccount(String userId, String accountId) {
        return mine(userId).stream().filter(t -> t.entries.stream().anyMatch(e -> e.accountId.equals(accountId)))
                .sorted((a, b) -> a.date.compareTo(b.date)).toList();
    }

    private static List<Transaction> newestFirst(List<Transaction> list) {
        return list.stream().sorted((a, b) -> b.date.compareTo(a.date)).toList();
    }

    // ---- 建立資料的捷徑 ----

    Account account(String user, String name, String kind, String currency, String opening, String openingDate) {
        return accountService.create(user, new AccountRequest(name, kind, currency, new BigDecimal(opening), openingDate, true, null, null));
    }

    Account account(String name, String currency) {
        return account(ME, name, "bank", currency, "0", "2026-01-01");
    }

    Category category(String user, String kind, String name) {
        return categoryService.create(user, new CategoryService.CategoryRequest(kind, null, name, null, "#22D3EE"));
    }

    static TransactionRequest expense(String accountId, String categoryId, String amount, String date) {
        return new TransactionRequest("expense", date, "午餐", null, null, categoryId, accountId, new BigDecimal(amount), null, null, null, null, null);
    }

    static TransactionRequest income(String accountId, String categoryId, String amount, String date) {
        return new TransactionRequest("income", date, "薪資", null, null, categoryId, accountId, new BigDecimal(amount), null, null, null, null, null);
    }

    static TransactionRequest transfer(String fromId, String toId, String amount, String toAmount, String fee, String date) {
        return new TransactionRequest("transfer", date, null, null, null, null, fromId, new BigDecimal(amount), toId,
                toAmount == null ? null : new BigDecimal(toAmount), fee == null ? null : new BigDecimal(fee), null, null);
    }

    Optional<Account> find(String id) {
        return accounts.stream().filter(a -> a.id.equals(id)).findFirst();
    }
}
