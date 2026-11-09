package com.doaccounts.service;

import com.doaccounts.model.Account;
import com.doaccounts.model.Category;
import com.doaccounts.model.Transaction;
import com.doaccounts.service.TransactionBatchService.BatchDelete;
import com.doaccounts.service.TransactionBatchService.BatchRequest;
import com.doaccounts.service.TransactionBatchService.BatchResult;
import com.doaccounts.service.TransactionBatchService.BatchRow;
import com.doaccounts.service.TransactionBatchService.RowConflict;
import com.doaccounts.service.TransactionBatchService.RowError;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static com.doaccounts.service.Fixture.ME;
import static com.doaccounts.service.Fixture.OTHER;
import static com.doaccounts.service.Fixture.expense;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;

/** 多筆儲存：全部檢查通過才寫入、一次回報全部錯誤、版本衝突、失敗補償（SD 4.2.1）。 */
class TransactionBatchServiceTest {

    private final Fixture f = new Fixture();
    private final Account cash = f.account("錢包", "TWD");
    private final Category food;

    TransactionBatchServiceTest() {
        f.categoryService.ensureDefaults(ME);
        food = f.categories.stream().filter(c -> c.name.equals("餐飲")).findFirst().orElseThrow();
    }

    private BatchRow row(int row, String title, String amount, String date) {
        return new BatchRow(row, null, null, "expense", date, title, null, null, food.id, cash.id, new BigDecimal(amount), null, null, null, null);
    }

    private BatchRow edit(int row, Transaction t, String amount) {
        return new BatchRow(row, t.id, t.version, "expense", t.date, t.title, null, null, food.id, cash.id, new BigDecimal(amount), null, null, null, null);
    }

    private static BatchRequest req(List<BatchRow> c, List<BatchRow> u, List<BatchDelete> d) {
        return new BatchRequest(c, u, d);
    }

    @SuppressWarnings("unchecked")
    private static <T> List<T> property(ErrorResponseException e, String key) {
        return (List<T>) e.getBody().getProperties().get(key);
    }

    @Test
    void createsUpdatesAndDeletesTogether() {
        Transaction a = f.txService.create(ME, expense(cash.id, food.id, "100", "2026-11-01"));
        Transaction b = f.txService.create(ME, expense(cash.id, food.id, "200", "2026-11-02"));
        BatchResult r = f.batchService.save(ME, req(
                List.of(row(1, "咖啡", "85", "2026-11-09"), row(2, "晚餐", "280", "2026-11-09")),
                List.of(edit(3, a, "150")),
                List.of(new BatchDelete(4, b.id, b.version))));
        assertEquals(2, r.created().size());
        assertEquals(1, r.created().get(0).row());
        assertEquals(1, r.updated().size());
        assertEquals(List.of(b.id), r.deletedIds());
        assertEquals(3, f.transactions.size());
        Transaction a2 = f.transactions.stream().filter(t -> t.id.equals(a.id)).findFirst().orElseThrow();
        assertEquals(0, new BigDecimal("-150").compareTo(a2.entries.get(0).amount), "a 已更新");
        assertEquals(a.version + 1, a2.version, "版本號加 1");
        assertEquals(0, new BigDecimal("-515").compareTo(f.accountService.getWithBalance(ME, cash.id).balance), "−85 −280 −150");
    }

    @Test
    void anyRowErrorWritesNothingAndReportsAllRowsWithFields() {
        Transaction a = f.txService.create(ME, expense(cash.id, food.id, "100", "2026-11-01"));
        ErrorResponseException e = assertThrows(ErrorResponseException.class, () -> f.batchService.save(ME, req(
                List.of(row(1, "咖啡", "85", "2026-11-09"), row(2, "文具", "45", "2025-12-30"), row(3, "", "10", "2026-11-09")),
                List.of(edit(4, a, "12.5")),
                List.of())));
        assertEquals(HttpStatus.UNPROCESSABLE_CONTENT, e.getStatusCode());
        List<RowError> errors = property(e, "errors");
        assertEquals(List.of(2, 3, 4), errors.stream().map(RowError::row).toList(), "不中途停止，一次回報全部");
        assertEquals("account_id", errors.get(0).field(), "開戶日期是帳戶的限制");
        assertTrue(errors.get(0).message().contains("2026-01-01"));
        assertEquals("title", errors.get(1).field());
        assertEquals("amount", errors.get(2).field(), "TWD 不能有小數");
        assertTrue(e.getBody().getDetail().contains("3 列"));
        assertEquals(1, f.transactions.size(), "一筆都沒寫入");
        assertEquals(0, new BigDecimal("-100").compareTo(a.entries.get(0).amount), "既有交易沒有被改");
    }

    @Test
    void staleVersionOrOtherUsersTransactionIsConflict() {
        Transaction a = f.txService.create(ME, expense(cash.id, food.id, "100", "2026-11-01"));
        Account theirs = f.account(OTHER, "他的錢包", "cash", "TWD", "0", "2026-01-01");
        Category theirFood = f.category(OTHER, "expense", "他的餐飲");
        Transaction x = f.txService.create(OTHER, expense(theirs.id, theirFood.id, "1", "2026-11-01"));
        BatchRow stale = new BatchRow(1, a.id, a.version + 1, "expense", a.date, a.title, null, null, food.id, cash.id, BigDecimal.TEN, null, null, null, null);
        ErrorResponseException e = assertThrows(ErrorResponseException.class, () -> f.batchService.save(ME, req(
                List.of(row(9, "咖啡", "85", "2026-11-09")), List.of(stale), List.of(new BatchDelete(2, x.id, x.version)))));
        assertEquals(HttpStatus.CONFLICT, e.getStatusCode());
        List<RowConflict> conflicts = property(e, "conflicts");
        assertEquals(List.of(1, 2), conflicts.stream().map(RowConflict::row).toList());
        assertEquals(2, f.transactions.size(), "連沒問題的新增列也不寫入");
    }

    @Test
    void limitsAndDuplicates() {
        List<BatchRow> many = new ArrayList<>();
        IntStream.rangeClosed(1, 101).forEach(i -> many.add(row(i, "x", "1", "2026-11-09")));
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class, () -> f.batchService.save(ME, req(many, null, null))).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class, () -> f.batchService.save(ME, req(null, null, null))).getStatusCode());
        Transaction a = f.txService.create(ME, expense(cash.id, food.id, "100", "2026-11-01"));
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class, () -> f.batchService.save(ME,
                req(null, List.of(edit(1, a, "5")), List.of(new BatchDelete(2, a.id, a.version))))).getStatusCode(), "同一筆不能又改又刪");
    }

    @Test
    void writeFailureIsCompensated() {
        Transaction a = f.txService.create(ME, expense(cash.id, food.id, "100", "2026-11-01"));
        Transaction b = f.txService.create(ME, expense(cash.id, food.id, "200", "2026-11-02"));
        long aVersion = a.version;
        // 第二筆修改寫入時資料庫斷線
        doAnswer(inv -> {
            Transaction t = inv.getArgument(0);
            if (t.id != null && t.id.equals(b.id)) throw new IllegalStateException("connection reset");
            t.version = t.version == null ? 0L : t.version + 1;
            if (t.id == null) { t.id = "new" + f.transactions.size(); f.transactions.add(t); }
            else { f.transactions.removeIf(x -> x.id.equals(t.id)); f.transactions.add(t); }
            return t;
        }).when(f.txRepo).save(any(Transaction.class));

        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> f.batchService.save(ME, req(
                List.of(row(1, "咖啡", "85", "2026-11-09")), List.of(edit(2, a, "150"), edit(3, b, "250")), List.of())));
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, e.getStatusCode());
        assertEquals(2, f.transactions.size(), "新增的那筆被刪掉");
        Transaction restoredA = f.transactions.stream().filter(t -> t.id.equals(a.id)).findFirst().orElseThrow();
        assertEquals(0, new BigDecimal("-100").compareTo(restoredA.entries.get(0).amount), "a 還原成原本的 100");
        assertEquals(aVersion, restoredA.version);
    }

    @Test
    void optimisticLockDuringWriteIsConflictAndCompensated() {
        Transaction a = f.txService.create(ME, expense(cash.id, food.id, "100", "2026-11-01"));
        doThrow(new OptimisticLockingFailureException("stale")).when(f.txRepo).delete(any(Transaction.class));
        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> f.batchService.save(ME, req(
                List.of(row(1, "咖啡", "85", "2026-11-09")), List.of(), List.of(new BatchDelete(2, a.id, a.version)))));
        assertEquals(HttpStatus.CONFLICT, e.getStatusCode());
        assertEquals(1, f.transactions.size());
        assertEquals(a.id, f.transactions.get(0).id);
    }
}
