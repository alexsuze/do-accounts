package com.doaccounts.service;

import com.doaccounts.model.Account;
import com.doaccounts.model.Category;
import com.doaccounts.model.Transaction;
import com.doaccounts.service.BudgetService.BudgetRequest;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static com.doaccounts.service.Fixture.ME;
import static com.doaccounts.service.Fixture.OTHER;
import static com.doaccounts.service.Fixture.expense;
import static com.doaccounts.service.Fixture.transfer;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** CSV 匯出與 JSON 備份還原（SA FR-10.1、10.8）。 */
class ExportBackupTest {

    private final Fixture f = new Fixture();
    private final Account cash = f.account(ME, "錢包", "cash", "TWD", "100", "2026-01-01");
    private final Account bank = f.account(ME, "國泰", "bank", "TWD", "0", "2026-01-01");
    private final Category lunch;

    ExportBackupTest() {
        f.categoryService.ensureDefaults(ME);
        lunch = f.categories.stream().filter(c -> c.userId.equals(ME) && c.name.equals("午餐")).findFirst().orElseThrow();
    }

    @Test
    void csvHasBomHeaderSignedAmountsAndInjectionGuard() throws Exception {
        var e = expense(cash.id, lunch.id, "120", "2026-11-01");
        f.txService.create(ME, new TransactionService.TransactionRequest("expense", "2026-11-01", "=HYPERLINK(\"x\")", "多,逗號", List.of("#聚餐"),
                lunch.id, cash.id, new BigDecimal("120"), null, null, null, null, null));
        f.txService.create(ME, transfer(bank.id, cash.id, "500", null, "15", "2026-11-02"));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        f.exportService.writeCsv(ME, f.ledger.all(ME), out);
        String csv = out.toString(StandardCharsets.UTF_8);
        assertTrue(csv.startsWith("﻿日期,類型,項目,大類,子類,帳戶,金額,幣別"));
        String[] lines = csv.split("\r\n");
        assertEquals("2026-11-01,支出,\"'=HYPERLINK(\"\"x\"\")\",餐飲,午餐,錢包,-120,TWD,,,,聚餐,\"多,逗號\"", lines[1]);
        assertEquals("2026-11-02,轉帳,轉帳,,,國泰,500,TWD,錢包,500,15,,", lines[2]);
        assertEquals("'-5", ExportService.safe("-5"));
        assertEquals("", ExportService.quote(null));
        assertTrue(e != null);
    }

    @Test
    void backupRestoresIntoEmptyAccountWithNewIds() {
        Transaction t = f.txService.create(ME, expense(cash.id, lunch.id, "120", "2026-11-01"));
        f.budgetService.upsert(f.me, new BudgetRequest("2026-11", lunch.id, new BigDecimal("3000"), true));
        f.fxService.setManual(ME, "2026-11-01", "USD", new BigDecimal("32.5"));
        f.me.baseCurrency = "TWD";
        var backup = f.backupService.export(f.me);
        assertEquals(BackupService.FORMAT, backup.format());
        assertEquals(1, backup.transactions().size());

        // 另一個空帳號（只有預設分類）
        f.categoryService.ensureDefaults(OTHER);
        var other = Fixture.user(OTHER);
        f.users.add(other);
        // 經過 JSON 來回轉換（與實際下載、上傳相同），也確保還原不會改到原帳號的物件
        var json = tools.jackson.databind.json.JsonMapper.builder()
                .propertyNamingStrategy(tools.jackson.databind.PropertyNamingStrategies.SNAKE_CASE).build();
        var copy = json.readValue(json.writeValueAsString(f.backupService.export(f.me)), BackupService.Backup.class);
        var r = f.backupService.restore(other, copy);
        assertEquals(2, r.accounts());
        assertEquals(1, r.transactions());
        Account restored = f.accounts.stream().filter(a -> a.userId.equals(OTHER) && a.name.equals("錢包")).findFirst().orElseThrow();
        assertNotEquals(cash.id, restored.id, "換成新的 id");
        assertEquals(0, new BigDecimal("-20").compareTo(f.accountService.getWithBalance(OTHER, restored.id).balance));
        Transaction rt = f.transactions.stream().filter(x -> x.userId.equals(OTHER)).findFirst().orElseThrow();
        Category rc = f.categoryService.get(OTHER, rt.categoryId);
        assertEquals("午餐", rc.name);
        assertEquals("餐飲", f.categoryService.get(OTHER, rc.parentId).name);
        assertEquals(1, f.budgets.stream().filter(b -> b.userId.equals(OTHER)).count());
        assertEquals(1, f.fxService.manual(OTHER).size());
        assertEquals(1, f.categories.stream().filter(c -> c.userId.equals(OTHER) && c.name.equals("午餐")).count(), "預設分類被備份的分類取代，不重複");
        assertEquals(t.title, rt.title);

        // 已經有交易的帳號不能還原
        assertThrows(ResponseStatusException.class, () -> f.backupService.restore(f.me, f.backupService.export(f.me)));
    }

    @Test
    void restoreRejectsBrokenFiles() {
        var other = Fixture.user(OTHER);
        f.users.add(other);
        assertThrows(ResponseStatusException.class, () -> f.backupService.restore(other, null));
        var good = f.backupService.export(f.me);
        var wrongFormat = new BackupService.Backup("x", 1, null, "TWD", null, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), null);
        assertThrows(ResponseStatusException.class, () -> f.backupService.restore(other, wrongFormat));
        var future = new BackupService.Backup(BackupService.FORMAT, 99, null, "TWD", null, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), null);
        assertThrows(ResponseStatusException.class, () -> f.backupService.restore(other, future));
        Transaction orphan = new Transaction();
        orphan.title = "孤兒";
        orphan.date = "2026-11-01";
        orphan.entries = List.of(new Transaction.Entry("不存在", new BigDecimal("-1"), Transaction.ROLE_MAIN, null));
        var broken = new BackupService.Backup(BackupService.FORMAT, 1, null, "TWD", null, good.accounts(), good.categories(),
                List.of(orphan), List.of(), List.of(), List.of(), null);
        var ex = assertThrows(ResponseStatusException.class, () -> f.backupService.restore(other, broken));
        assertTrue(ex.getReason().contains("帳戶不存在"));
        assertTrue(f.accounts.stream().noneMatch(a -> a.userId.equals(OTHER)), "檢查失敗時不動任何資料");
    }
}
