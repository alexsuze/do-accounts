package com.doaccounts.service;

import com.doaccounts.model.Account;
import com.doaccounts.model.Category;
import com.doaccounts.model.ImportBatch;
import com.doaccounts.service.ImportService.ImportRequest;
import com.doaccounts.service.ImportService.ImportRow;
import com.doaccounts.service.ImportService.Preview;
import com.doaccounts.service.ImportService.RowResult;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.doaccounts.service.Fixture.ME;
import static com.doaccounts.service.Fixture.OTHER;
import static com.doaccounts.service.Fixture.expense;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** CSV 匯入：名稱對應、逐列檢查、重複偵測、新增分類、整批撤銷、失敗補償（SA FR-10.2～10.7）。 */
class ImportServiceTest {

    private final Fixture f = new Fixture();
    private final Account cash = f.account(ME, "錢包", "cash", "TWD", "0", "2026-01-01");
    private final Account bank = f.account(ME, "國泰 薪轉", "bank", "TWD", "0", "2026-01-01");
    private final Account usd = f.account(ME, "美元", "bank", "USD", "0", "2026-01-01");
    private final Category lunch;

    ImportServiceTest() {
        f.categoryService.ensureDefaults(ME);
        f.categoryService.ensureDefaults(OTHER);
        lunch = f.categories.stream().filter(c -> c.userId.equals(ME) && c.name.equals("午餐")).findFirst().orElseThrow();
    }

    private static ImportRow row(int line, String date, String kind, String title, String category, String account, String amount) {
        return new ImportRow(line, date, kind, title, category, account, amount == null ? null : new BigDecimal(amount), null, null, null, null, List.of());
    }

    private static ImportRequest req(List<ImportRow> rows, Map<String, String> catMap, List<Integer> lines) {
        return new ImportRequest("cathay.csv", rows, null, Map.of(), catMap, lines);
    }

    private static RowResult at(Preview p, int line) {
        return p.rows().stream().filter(r -> r.line() == line).findFirst().orElseThrow();
    }

    @Test
    void previewResolvesNamesAndReportsErrorsPerRow() {
        List<ImportRow> rows = List.of(
                row(1, "2026-10-03", "expense", "全聯", "餐飲 > 午餐", "錢包", "1286"),       // 分隔符號不同也能對到
                row(2, "2026-10-05", "income", "薪資轉帳", "薪資", "國泰薪轉", "68500"),        // 帳戶名稱去空白比對
                row(3, "2026-10-08", "expense", "富邦人壽", "保險費", "錢包", "3200"),          // 找不到分類
                row(4, "2026-10-09", "expense", "電費", "", "不存在的帳戶", "1180"),           // 找不到帳戶
                row(5, "2025-12-30", "expense", "文具", "", "錢包", "45"),                   // 早於開戶日
                row(6, "2026-10-10", "expense", "書", "", "美元", "12.345"),                  // 小數太多
                row(7, "2026-10-11", "", "退款", "", "錢包", "10"),                          // 類型不明
                row(8, "2026-10-12", "expense", "", "午餐", "錢包", "90"));                    // 沒有項目 → 用分類名稱
        Preview p = f.importService.preview(ME, req(rows, Map.of(), null));
        assertEquals(8, p.total());
        assertEquals("ok", at(p, 1).status());
        assertEquals("ok", at(p, 2).status());
        assertEquals("category_id", at(p, 3).field());
        assertEquals("account_id", at(p, 4).field());
        assertTrue(at(p, 5).message().contains("開戶日期"));
        assertEquals("amount", at(p, 6).field());
        assertEquals("kind", at(p, 7).field());
        assertEquals("ok", at(p, 8).status());
        assertEquals(3, p.ok());
        assertEquals(5, p.error());
        assertEquals("expense|保險費", p.missingCategories().getFirst().key());
        assertEquals("不存在的帳戶", p.missingAccounts().getFirst().name());
    }

    @Test
    void commitImportsSelectedLinesCreatesCategoriesAndCanRevert() {
        List<ImportRow> rows = List.of(
                row(1, "2026-10-03", "expense", "全聯", "餐飲 › 食材", "錢包", "1286"),
                row(2, "2026-10-08", "expense", "富邦人壽", "保險費 › 壽險", "錢包", "3200"),
                row(3, "2026-10-09", "expense", "電費", "", "錢包", "1180"),
                row(4, "2026/13/02", "expense", "壞日期", "", "錢包", "1"));
        Map<String, String> catMap = Map.of("expense|保險費 › 壽險", ImportService.CREATE);
        Preview p = f.importService.preview(ME, req(rows, catMap, null));
        assertTrue(at(p, 2).newCategory());
        assertEquals("error", at(p, 4).status());

        ImportBatch b = f.importService.commit(ME, req(rows, catMap, List.of(1, 2, 4))); // 第 3 列取消勾選，第 4 列錯誤自動略過
        assertEquals(2, b.importedCount);
        assertEquals(2, b.skippedCount);
        assertEquals(List.of("保險費 › 壽險"), b.createdCategories);
        Category life = f.categories.stream().filter(c -> c.name.equals("壽險")).findFirst().orElseThrow();
        Category parent = f.categories.stream().filter(c -> c.id.equals(life.parentId)).findFirst().orElseThrow();
        assertEquals("保險費", parent.name, "大類不存在時一併新增");
        assertEquals(2, f.transactions.stream().filter(t -> b.id.equals(t.importBatchId)).count());
        assertEquals(0, new BigDecimal("-4486").compareTo(f.accountService.getWithBalance(ME, cash.id).balance));

        // 再匯入同一個檔案：全部標成重複
        Preview again = f.importService.preview(ME, req(rows, Map.of(), null));
        assertEquals("duplicate", at(again, 1).status());
        assertEquals("duplicate", at(again, 2).status());
        assertEquals("ok", at(again, 3).status());

        ImportBatch r = f.importService.revert(ME, b.id);
        assertEquals(ImportBatch.REVERTED, r.status);
        assertEquals(2, r.revertedCount);
        assertTrue(f.transactions.isEmpty());
        assertThrows(ResponseStatusException.class, () -> f.importService.revert(ME, b.id), "不能撤銷兩次");
        assertThrows(ResponseStatusException.class, () -> f.importService.revert(OTHER, b.id), "別人的匯入回 404");
    }

    @Test
    void duplicatesMatchDateAccountAmountAndSimilarTitle() {
        f.txService.create(ME, expense(cash.id, lunch.id, "500", "2026-10-03"));  // 項目「午餐」
        List<ImportRow> rows = List.of(
                row(1, "2026-10-03", "expense", "午餐 便當", "", "錢包", "500"),  // 名稱互相包含 → 重複
                row(2, "2026-10-03", "expense", "晚餐", "", "錢包", "500"),       // 名稱不同 → 不是
                row(3, "2026-10-03", "expense", "午餐", "", "錢包", "501"),       // 金額不同 → 不是
                row(4, "2026-10-03", "income", "午餐", "", "錢包", "500"));       // 方向不同 → 不是
        Preview p = f.importService.preview(ME, req(rows, Map.of(), null));
        assertEquals("duplicate", at(p, 1).status());
        assertTrue(at(p, 1).message().contains("午餐"));
        assertEquals("ok", at(p, 2).status());
        assertEquals("ok", at(p, 3).status());
        assertEquals("ok", at(p, 4).status());
        // 使用者勾選重複的列仍可匯入
        assertEquals(1, f.importService.commit(ME, req(rows, Map.of(), List.of(1))).importedCount);
    }

    @Test
    void transferDefaultAccountAndMappedNames() {
        List<ImportRow> rows = new ArrayList<>(List.of(
                new ImportRow(1, "2026-10-03", "transfer", "", null, "國泰薪轉", new BigDecimal("3200"), "美元", new BigDecimal("100"), new BigDecimal("15"), null, List.of()),
                row(2, "2026-10-04", "expense", "咖啡", "", "", "85"),               // 沒有帳戶 → 預設帳戶
                row(3, "2026-10-05", "expense", "早餐", "", "Cash", "60")));          // 使用者指定對應
        ImportRequest r = new ImportRequest("x.csv", rows, cash.id, Map.of("Cash", cash.id), Map.of(), List.of(1, 2, 3));
        Preview p = f.importService.preview(ME, r);
        assertEquals(3, p.ok(), () -> p.rows().toString());
        assertEquals("Cash", p.missingAccounts().getFirst().name());
        assertEquals(cash.id, p.missingAccounts().getFirst().mappedTo());
        f.importService.commit(ME, r);
        assertEquals(0, new BigDecimal("100").compareTo(f.accountService.getWithBalance(ME, usd.id).balance));
        assertEquals(0, new BigDecimal("-3215").compareTo(f.accountService.getWithBalance(ME, bank.id).balance), "轉出＋手續費");

        // 指定成別人的帳戶 → 錯誤
        Account others = f.account(OTHER, "他的", "cash", "TWD", "0", "2026-01-01");
        Preview bad = f.importService.preview(ME, new ImportRequest("x.csv", List.of(row(1, "2026-10-05", "expense", "早餐", "", "Cash", "60")),
                null, Map.of("Cash", others.id), Map.of(), null));
        assertEquals("error", at(bad, 1).status());
    }

    @Test
    void limitsAndWriteFailureCompensation() {
        assertThrows(ResponseStatusException.class, () -> f.importService.preview(ME, req(List.of(), Map.of(), null)));
        List<ImportRow> many = new ArrayList<>();
        for (int i = 0; i <= ImportService.MAX_ROWS; i++) many.add(row(i + 1, "2026-10-03", "expense", "x", "", "錢包", "1"));
        assertThrows(ResponseStatusException.class, () -> f.importService.preview(ME, req(many, Map.of(), null)), "超過一萬筆");
        assertThrows(ResponseStatusException.class, () -> f.importService.commit(ME, req(many.subList(0, 3), Map.of(), List.of())), "沒有勾選");

        f.failSaveAll = true;
        assertThrows(ResponseStatusException.class, () -> f.importService.commit(ME, req(many.subList(0, 3), Map.of(), List.of(1, 2, 3))));
        assertTrue(f.transactions.isEmpty(), "寫入失敗時不留下資料");
        assertEquals(ImportBatch.FAILED, f.importBatches.getFirst().status);
    }

    @Test
    void templatesBelongToUser() {
        var t = f.importService.saveTemplate(ME, null, new ImportService.TemplateRequest("國泰對帳單", Map.of("date", "交易日期")));
        assertEquals(1, f.importService.templates(ME).size());
        assertTrue(f.importService.templates(OTHER).isEmpty());
        assertThrows(ResponseStatusException.class, () -> f.importService.saveTemplate(OTHER, t.id, new ImportService.TemplateRequest("x", Map.of())));
        assertThrows(ResponseStatusException.class, () -> f.importService.saveTemplate(ME, null, new ImportService.TemplateRequest(" ", Map.of())));
        f.importService.deleteTemplate(ME, t.id);
        assertTrue(f.templates.isEmpty());
    }

    @Test
    void categoryMatching() {
        List<Category> cats = f.categoryService.list(ME);
        assertEquals(lunch.id, ImportService.matchCategory("expense", "午餐", cats), "唯一的子類名稱");
        assertEquals(lunch.id, ImportService.matchCategory("expense", "餐飲 › 午餐", cats));
        assertNull(ImportService.matchCategory("income", "午餐", cats), "類型不同");
        assertEquals("餐飲 › 午餐", ImportService.displayName(" 餐飲>午餐 "));
    }
}
