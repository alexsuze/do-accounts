package com.doaccounts.service;

import com.doaccounts.model.Account;
import com.doaccounts.model.Category;
import com.doaccounts.model.ImportBatch;
import com.doaccounts.model.ImportTemplate;
import com.doaccounts.model.Transaction;
import com.doaccounts.model.Transaction.Entry;
import com.doaccounts.repository.ImportBatchRepository;
import com.doaccounts.repository.ImportTemplateRepository;
import com.doaccounts.repository.TransactionRepository;
import com.doaccounts.service.TransactionService.Lookup;
import com.doaccounts.service.TransactionService.TransactionRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * CSV 匯入（SA FR-10.2～10.7、SD 4.7）。
 * CSV 在瀏覽器解析並依欄位對應轉成 {@link ImportRow}（ADR-17），這裡負責：名稱對應帳戶／分類、逐列檢查、重複偵測、
 * 批次寫入（每 500 筆）、失敗補償與整批撤銷。預覽與確認都重新計算，後端不暫存預覽。
 */
@Service
public class ImportService {

    private static final Logger log = LoggerFactory.getLogger(ImportService.class);

    public static final int MAX_ROWS = 10_000;
    static final int CHUNK = 500;
    /** categoryMap 的值：自動新增這個分類 */
    public static final String CREATE = "__create__";
    private static final String NEW_CATEGORY_COLOR = "#64748B";

    private final TransactionRepository txRepo;
    private final LedgerQueries ledger;
    private final TransactionService transactions;
    private final AccountService accounts;
    private final CategoryService categories;
    private final ImportBatchRepository batches;
    private final ImportTemplateRepository templates;

    public ImportService(TransactionRepository txRepo, LedgerQueries ledger, TransactionService transactions, AccountService accounts,
                         CategoryService categories, ImportBatchRepository batches, ImportTemplateRepository templates) {
        this.txRepo = txRepo;
        this.ledger = ledger;
        this.transactions = transactions;
        this.accounts = accounts;
        this.categories = categories;
        this.batches = batches;
        this.templates = templates;
    }

    /**
     * 一列匯入資料（前端依欄位對應整理好）。金額一律正數，方向由 kind 決定；account／category 是名稱，
     * category 可以是「大類 › 子類」。account 空白時用 defaultAccountId。
     */
    public record ImportRow(int line, String date, String kind, String title, String category, String account,
                            BigDecimal amount, String toAccount, BigDecimal toAmount, BigDecimal fee, String note, List<String> tags) {}

    /**
     * @param accountMap  CSV 裡的帳戶名稱 → 帳戶 id（名稱比對不到時由使用者指定）
     * @param categoryMap 「kind|分類名稱」→ 分類 id 或 {@link #CREATE}
     */
    public record ImportRequest(String fileName, List<ImportRow> rows, String defaultAccountId,
                                Map<String, String> accountMap, Map<String, String> categoryMap, List<Integer> lines) {}

    /** status：ok / duplicate / error；newCategory：匯入時會新增分類 */
    public record RowResult(int line, String status, String field, String message, boolean newCategory) {}

    public record MissingAccount(String name, int rows, String mappedTo) {}

    /** key 即 categoryMap 的鍵 */
    public record MissingCategory(String key, String kind, String name, int rows, String mappedTo) {}

    public record Preview(int total, int ok, int duplicate, int error, List<RowResult> rows,
                          List<MissingAccount> missingAccounts, List<MissingCategory> missingCategories) {}

    // ---------- 預覽 ----------

    public Preview preview(String userId, ImportRequest req) {
        return analyze(userId, req, context(userId)).preview();
    }

    // ---------- 確認匯入 ----------

    /** 只匯入 lines 指定的列（狀態為 ok 或 duplicate 者）；錯誤的列一律略過 */
    public ImportBatch commit(String userId, ImportRequest req) {
        Context ctx = context(userId);
        Analysis a = analyze(userId, req, ctx);
        Set<Integer> wanted = req.lines() == null ? Set.of() : new HashSet<>(req.lines());
        List<Prepared> chosen = a.prepared().stream()
                .filter(p -> wanted.contains(p.row().line()) && !"error".equals(a.status().get(p.row().line()))).toList();
        if (chosen.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "沒有可以匯入的資料列");

        // 建立需要的分類（只建這次會用到的）
        List<String> created = new ArrayList<>();
        Map<String, String> newIds = new HashMap<>();
        for (Prepared p : chosen) {
            if (p.createKey() == null || newIds.containsKey(p.createKey())) continue;
            Category c = createCategory(userId, p.kind(), p.createName(), ctx);
            newIds.put(p.createKey(), c.id);
            created.add(p.createName());
        }
        Lookup lk = TransactionService.cached(ctx.accountList, ctx.categoryList);

        ImportBatch batch = new ImportBatch();
        batch.userId = userId;
        batch.fileName = fileName(req.fileName());
        batch.rowCount = req.rows().size();
        batch.createdAt = Instant.now();
        batch.status = ImportBatch.FAILED; // 寫入完成才改成 completed
        batch.createdCategories = created;
        batch = batches.save(batch);

        List<Transaction> docs = new ArrayList<>();
        for (Prepared p : chosen) {
            TransactionRequest tr = p.request();
            if (p.createKey() != null) tr = withCategory(tr, newIds.get(p.createKey()));
            Transaction t = new Transaction();
            t.userId = userId;
            transactions.fill(userId, t, tr, lk);
            t.importBatchId = batch.id;
            docs.add(t);
        }
        try {
            for (int i = 0; i < docs.size(); i += CHUNK) txRepo.saveAll(docs.subList(i, Math.min(i + CHUNK, docs.size())));
        } catch (RuntimeException e) {
            log.error("匯入 {} 寫入失敗，刪除已寫入的交易", batch.id, e);
            ledger.removeByBatch(userId, batch.id);
            batches.save(batch);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "匯入失敗，資料未變更，請重試");
        }
        batch.status = ImportBatch.COMPLETED;
        batch.importedCount = docs.size();
        batch.skippedCount = batch.rowCount - docs.size();
        return batches.save(batch);
    }

    // ---------- 匯入紀錄 ----------

    public List<ImportBatch> batches(String userId) {
        return batches.findTop20ByUserIdOrderByCreatedAtDesc(userId);
    }

    /** 整批撤銷（FR-10.6）：刪除這批匯入的全部交易（含匯入後修改過的）；自動新增的分類保留 */
    public ImportBatch revert(String userId, String id) {
        ImportBatch b = batches.findById(id).filter(x -> userId.equals(x.userId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "找不到這次匯入"));
        if (!ImportBatch.COMPLETED.equals(b.status)) throw new ResponseStatusException(HttpStatus.CONFLICT, "這次匯入已經撤銷過或沒有完成");
        b.revertedCount = (int) ledger.removeByBatch(userId, b.id);
        b.status = ImportBatch.REVERTED;
        b.revertedAt = Instant.now();
        return batches.save(b);
    }

    // ---------- 範本 ----------

    public record TemplateRequest(String name, Map<String, Object> mapping) {}

    public List<ImportTemplate> templates(String userId) {
        return templates.findByUserIdOrderByNameAsc(userId);
    }

    public ImportTemplate saveTemplate(String userId, String id, TemplateRequest req) {
        ImportTemplate t = id == null ? new ImportTemplate() : template(userId, id);
        String name = req.name() == null ? "" : req.name().trim();
        if (name.isEmpty() || name.length() > 50) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "範本名稱需為 1～50 字");
        if (req.mapping() == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "缺少欄位對應");
        t.userId = userId;
        t.name = name;
        t.mapping = req.mapping();
        t.updatedAt = Instant.now();
        return templates.save(t);
    }

    public void deleteTemplate(String userId, String id) {
        templates.delete(template(userId, id));
    }

    private ImportTemplate template(String userId, String id) {
        return templates.findById(id).filter(t -> userId.equals(t.userId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "找不到這個範本"));
    }

    // ---------- 分析 ----------

    /** 使用者的帳戶與分類（一次載入，逐列查找用） */
    private static final class Context {
        final List<Account> accountList;
        final List<Category> categoryList;

        Context(List<Account> a, List<Category> c) {
            accountList = new ArrayList<>(a);
            categoryList = new ArrayList<>(c);
        }
    }

    private Context context(String userId) {
        return new Context(accounts.list(userId, true), categories.list(userId));
    }

    /** 一列整理好的交易請求；createKey 非 null 表示要先新增分類 */
    private record Prepared(ImportRow row, String kind, TransactionRequest request, String createKey, String createName) {}

    private record Analysis(Preview preview, List<Prepared> prepared, Map<Integer, String> status) {}

    private Analysis analyze(String userId, ImportRequest req, Context ctx) {
        List<ImportRow> rows = req.rows() == null ? List.of() : req.rows();
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "檔案沒有資料列");
        if (rows.size() > MAX_ROWS) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "單次最多匯入 " + MAX_ROWS + " 筆，請分成多個檔案");
        Map<String, String> accountMap = req.accountMap() == null ? Map.of() : req.accountMap();
        Map<String, String> categoryMap = req.categoryMap() == null ? Map.of() : req.categoryMap();
        Lookup lk = TransactionService.cached(ctx.accountList, ctx.categoryList);

        Map<String, int[]> missAcc = new LinkedHashMap<>();
        Map<String, Object[]> missCat = new LinkedHashMap<>(); // key → {kind, name, count}
        List<RowResult> results = new ArrayList<>();
        List<Prepared> prepared = new ArrayList<>();
        Map<Integer, String> status = new HashMap<>();

        for (ImportRow r : rows) {
            String kind = r.kind() == null ? "" : r.kind();
            try {
                if (!Set.of(Transaction.EXPENSE, Transaction.INCOME, Transaction.TRANSFER).contains(kind)) {
                    throw r.amount() == null
                            ? new FieldException(HttpStatus.UNPROCESSABLE_CONTENT, "amount", "金額空白或無法辨識")
                            : new FieldException(HttpStatus.UNPROCESSABLE_CONTENT, "kind", "無法判斷是收入、支出或轉帳");
                }
                String accountId = resolveAccount(r.account(), req.defaultAccountId(), accountMap, ctx, missAcc, "account_id");
                String toAccountId = Transaction.TRANSFER.equals(kind)
                        ? resolveAccount(r.toAccount(), null, accountMap, ctx, missAcc, "to_account_id") : null;

                String categoryId = null;
                String createKey = null;
                String createName = null;
                if (!Transaction.TRANSFER.equals(kind)) {
                    String name = displayName(r.category());
                    if (name.isEmpty()) {
                        categoryId = lk.system(kind, Category.SYS_OTHER).id;
                    } else {
                        String key = kind + "|" + name;
                        categoryId = matchCategory(kind, name, ctx.categoryList);
                        if (categoryId == null) {
                            Object[] m = missCat.computeIfAbsent(key, k -> new Object[]{kind, name, 0});
                            m[2] = (int) m[2] + 1;
                            String mapped = categoryMap.get(key);
                            if (CREATE.equals(mapped)) {
                                createKey = key;
                                createName = name;
                                categoryId = lk.system(kind, Category.SYS_OTHER).id; // 檢查用，寫入時換成新分類
                            } else if (mapped != null && !mapped.isBlank()) {
                                categoryId = mapped;
                            } else {
                                throw new FieldException(HttpStatus.UNPROCESSABLE_CONTENT, "category_id", "找不到分類「" + name + "」，請在上方選擇對應或新增");
                            }
                        }
                    }
                }
                String title = r.title() == null || r.title().isBlank()
                        ? (Transaction.TRANSFER.equals(kind) ? "" : leaf(displayName(r.category()), "匯入")) : r.title().trim();
                TransactionRequest tr = new TransactionRequest(kind, r.date(), title, r.note(), r.tags(), categoryId, accountId,
                        r.amount(), toAccountId, r.toAmount(), r.fee(), null, null);
                Transaction probe = new Transaction();
                transactions.fill(userId, probe, tr, lk); // 與手動記帳相同的檢查
                prepared.add(new Prepared(r, kind, tr, createKey, createName));
                status.put(r.line(), "ok");
                results.add(new RowResult(r.line(), "ok", null, createName == null ? null : "將新增分類「" + createName + "」", createName != null));
            } catch (FieldException e) {
                status.put(r.line(), "error");
                results.add(new RowResult(r.line(), "error", e.getField(), e.getReason(), false));
            } catch (ResponseStatusException e) {
                status.put(r.line(), "error");
                results.add(new RowResult(r.line(), "error", null, e.getReason(), false));
            }
        }

        markDuplicates(userId, prepared, results, status);

        int ok = 0, dup = 0, err = 0;
        for (RowResult x : results) {
            switch (x.status()) {
                case "ok" -> ok++;
                case "duplicate" -> dup++;
                default -> err++;
            }
        }
        List<MissingAccount> ma = missAcc.entrySet().stream()
                .map(e -> new MissingAccount(e.getKey(), e.getValue()[0], accountMap.get(e.getKey()))).toList();
        List<MissingCategory> mc = missCat.entrySet().stream()
                .map(e -> new MissingCategory(e.getKey(), (String) e.getValue()[0], (String) e.getValue()[1], (int) e.getValue()[2], categoryMap.get(e.getKey()))).toList();
        return new Analysis(new Preview(rows.size(), ok, dup, err, results, ma, mc), prepared, status);
    }

    /**
     * 重複偵測（FR-10.5）：同日期＋同帳戶＋同金額（帶正負號），且項目名稱去除空白後相同或互相包含。
     * 一次查出日期範圍內的既有交易，在記憶體比對。
     */
    private void markDuplicates(String userId, List<Prepared> prepared, List<RowResult> results, Map<Integer, String> status) {
        if (prepared.isEmpty()) return;
        String from = prepared.stream().map(p -> p.request().date()).min(String::compareTo).orElseThrow();
        String to = prepared.stream().map(p -> p.request().date()).max(String::compareTo).orElseThrow();
        Map<String, List<Transaction>> existing = new HashMap<>();
        for (Transaction t : ledger.search(userId, new LedgerQueries.Filter(from, to, null, null, null, null, null))) {
            for (Entry e : t.entries) {
                if (Transaction.ROLE_MAIN.equals(e.role) || Transaction.ROLE_TRANSFER_OUT.equals(e.role)) {
                    existing.computeIfAbsent(dupKey(t.date, e.accountId, e.amount), k -> new ArrayList<>()).add(t);
                }
            }
        }
        Map<Integer, Integer> index = new HashMap<>();
        for (int i = 0; i < results.size(); i++) index.put(results.get(i).line(), i);
        for (Prepared p : prepared) {
            TransactionRequest r = p.request();
            BigDecimal signed = Transaction.INCOME.equals(r.kind()) ? r.amount() : r.amount().negate();
            String title = norm(r.title().isEmpty() && Transaction.TRANSFER.equals(r.kind()) ? "轉帳" : r.title());
            for (Transaction t : existing.getOrDefault(dupKey(r.date(), r.accountId(), signed), List.of())) {
                String other = norm(t.title);
                if (other.equals(title) || other.contains(title) || title.contains(other)) {
                    int i = index.get(p.row().line());
                    RowResult old = results.get(i);
                    results.set(i, new RowResult(old.line(), "duplicate", null,
                            "與 " + t.date.substring(5).replace('-', '/') + "「" + t.title + "」相同日期、帳戶、金額 · 預設跳過", old.newCategory()));
                    status.put(old.line(), "duplicate");
                    break;
                }
            }
        }
    }

    private static String dupKey(String date, String accountId, BigDecimal amount) {
        return date + "|" + accountId + "|" + amount.stripTrailingZeros().toPlainString();
    }

    private static String resolveAccount(String raw, String defaultId, Map<String, String> map, Context ctx,
                                         Map<String, int[]> missing, String field) {
        String name = raw == null ? "" : raw.trim();
        if (name.isEmpty()) {
            if (defaultId != null && !defaultId.isBlank() && "account_id".equals(field)) return defaultId;
            throw new FieldException(HttpStatus.UNPROCESSABLE_CONTENT, field, "account_id".equals(field) ? "沒有帳戶，請設定預設帳戶" : "轉帳需要轉入帳戶");
        }
        String n = norm(name);
        for (Account a : ctx.accountList) if (norm(a.name).equals(n)) return a.id;
        missing.computeIfAbsent(name, k -> new int[1])[0]++;
        String mapped = map.get(name);
        if (mapped != null && !mapped.isBlank()) return mapped;
        throw new FieldException(HttpStatus.UNPROCESSABLE_CONTENT, field, "找不到帳戶「" + name + "」，請在上方選擇對應");
    }

    /** 「大類 › 子類」或單一名稱（先找大類，再找唯一同名的子類） */
    static String matchCategory(String kind, String display, List<Category> cats) {
        String[] parts = display.split("\\s*›\\s*", 2);
        List<Category> ofKind = cats.stream().filter(c -> kind.equals(c.kind)).toList();
        Category root = ofKind.stream().filter(c -> c.parentId == null && norm(c.name).equals(norm(parts[0]))).findFirst().orElse(null);
        if (parts.length == 2) {
            if (root == null) return null;
            return ofKind.stream().filter(c -> root.id.equals(c.parentId) && norm(c.name).equals(norm(parts[1])))
                    .map(c -> c.id).findFirst().orElse(null);
        }
        if (root != null) return root.id;
        List<Category> subs = ofKind.stream().filter(c -> c.parentId != null && norm(c.name).equals(norm(parts[0]))).toList();
        return subs.size() == 1 ? subs.getFirst().id : null;
    }

    /** 新增分類：「大類 › 子類」時大類不存在也一併新增 */
    private Category createCategory(String userId, String kind, String display, Context ctx) {
        String[] parts = display.split("\\s*›\\s*", 2);
        String rootId = matchCategory(kind, parts[0], ctx.categoryList.stream().filter(c -> c.parentId == null).toList());
        if (parts.length == 1) return add(userId, ctx, kind, null, parts[0]);
        if (rootId == null) rootId = add(userId, ctx, kind, null, parts[0]).id;
        return add(userId, ctx, kind, rootId, parts[1]);
    }

    private Category add(String userId, Context ctx, String kind, String parentId, String name) {
        Category c = categories.create(userId, new CategoryService.CategoryRequest(kind, parentId, name, null, NEW_CATEGORY_COLOR));
        ctx.categoryList.add(c);
        return c;
    }

    private static TransactionRequest withCategory(TransactionRequest r, String categoryId) {
        return new TransactionRequest(r.kind(), r.date(), r.title(), r.note(), r.tags(), categoryId, r.accountId(), r.amount(),
                r.toAccountId(), r.toAmount(), r.fee(), r.feeCategoryId(), r.version());
    }

    /** 統一分隔符號：「餐飲>午餐」「餐飲 › 午餐」→「餐飲 › 午餐」 */
    static String displayName(String raw) {
        if (raw == null) return "";
        String[] parts = raw.trim().split("\\s*[›>]\\s*");
        List<String> out = new ArrayList<>();
        for (String p : parts) if (!p.isBlank()) out.add(p.trim().replaceAll("\\s+", " "));
        return String.join(" › ", out.subList(0, Math.min(2, out.size())));
    }

    private static String leaf(String display, String fallback) {
        if (display.isEmpty()) return fallback;
        String[] parts = display.split(" › ");
        return parts[parts.length - 1];
    }

    private static String norm(String s) {
        return s == null ? "" : s.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }

    private static String fileName(String s) {
        String n = s == null || s.isBlank() ? "未命名.csv" : s.trim();
        return n.length() > 120 ? n.substring(0, 120) : n;
    }
}
