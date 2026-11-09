package com.doaccounts.service;

import com.doaccounts.model.Account;
import com.doaccounts.model.Category;
import com.doaccounts.model.Transaction;
import com.doaccounts.model.Transaction.Entry;
import com.doaccounts.repository.TransactionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 記帳（SA FR-3、SD 4.2）。一筆交易 = 一份文件，分錄內嵌；寫入只有一次 save（原子操作）。
 */
@Service
public class TransactionService {

    public static final int MAX_PAGE_SIZE = 200;
    private static final Set<String> KINDS = Set.of(Transaction.INCOME, Transaction.EXPENSE, Transaction.TRANSFER);

    private final TransactionRepository repo;
    private final LedgerQueries ledger;
    private final AccountService accounts;
    private final CategoryService categories;

    public TransactionService(TransactionRepository repo, LedgerQueries ledger, AccountService accounts, CategoryService categories) {
        this.repo = repo;
        this.ledger = ledger;
        this.accounts = accounts;
        this.categories = categories;
    }

    /** 金額一律正數；方向由 kind 決定（SD 3.4） */
    public record TransactionRequest(String kind, String date, String title, String note, List<String> tags,
                                     String categoryId, String accountId, BigDecimal amount,
                                     String toAccountId, BigDecimal toAmount, BigDecimal fee, String feeCategoryId,
                                     Long version) {}

    public record Totals(BigDecimal income, BigDecimal expense) {
        static Totals zero() { return new Totals(BigDecimal.ZERO, BigDecimal.ZERO); }
        Totals add(Totals o) { return new Totals(income.add(o.income), expense.add(o.expense)); }
    }

    public record DailyTotal(String date, Map<String, Totals> byCurrency) {}

    public record Page(List<Transaction> items, int page, int size, int total,
                       Map<String, Totals> summary, List<DailyTotal> dailyTotals) {}

    public record Suggestion(String title, String kind, String categoryId, String accountId, int count) {}

    public record LedgerRow(Transaction transaction, BigDecimal amount, BigDecimal balanceAfter) {}

    public record AccountLedger(Account account, List<LedgerRow> items, int page, int size, int total) {}

    public record DayBalance(String date, BigDecimal balance) {}

    // ---------- 寫入 ----------

    public Transaction create(String userId, TransactionRequest req) {
        Transaction t = new Transaction();
        t.userId = userId;
        fill(userId, t, req);
        return repo.save(t);
    }

    /** 整份覆寫並重建分錄。version 必填且需與目前一致（SD ADR-13）。 */
    public Transaction update(String userId, String id, TransactionRequest req) {
        Transaction t = get(userId, id);
        if (Transaction.INVESTMENT.equals(t.kind)) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, "投資交易請到投資頁修改");
        }
        if (req.version() == null) throw bad("缺少版本號，請重新整理後再修改");
        if (!req.version().equals(t.version)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "此筆紀錄已在其他地方被修改，請重新整理");
        }
        fill(userId, t, req);
        return repo.save(t);
    }

    public void delete(String userId, String id) {
        Transaction t = get(userId, id);
        if (Transaction.INVESTMENT.equals(t.kind)) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, "投資交易請到投資頁刪除");
        }
        repo.delete(t);
    }

    /** 批次刪除（SA FR-3.9）。投資交易不能在這裡刪。 @return 刪除筆數 */
    public int bulkDelete(String userId, List<String> ids) {
        List<Transaction> list = ownAll(userId, ids);
        if (list.stream().anyMatch(t -> Transaction.INVESTMENT.equals(t.kind))) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, "選取的交易包含投資交易，請到投資頁處理");
        }
        repo.deleteAll(list);
        return list.size();
    }

    /** 批次修改分類與標籤（SA FR-3.9）。分類的收支類型必須與每一筆一致。 @return 修改筆數 */
    public int bulkUpdate(String userId, List<String> ids, String categoryId, List<String> addTags, List<String> removeTags) {
        List<Transaction> list = ownAll(userId, ids);
        Category c = categoryId == null || categoryId.isBlank() ? null : categories.get(userId, categoryId);
        if (c != null) {
            long mismatch = list.stream().filter(t -> !c.kind.equals(t.kind)).count();
            if (mismatch > 0) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT,
                        "選取的交易中有 " + mismatch + " 筆不是" + kindLabel(c.kind) + "，不能改成這個分類");
            }
        }
        List<String> add = normalizeTags(addTags);
        Set<String> remove = new LinkedHashSet<>(normalizeTags(removeTags));
        for (Transaction t : list) {
            if (c != null) t.categoryId = c.id;
            LinkedHashSet<String> tags = new LinkedHashSet<>(t.tags);
            tags.addAll(add);
            tags.removeAll(remove);
            if (tags.size() > 10) throw bad("每筆交易最多 10 個標籤");
            t.tags = new ArrayList<>(tags);
        }
        repo.saveAll(list);
        return list.size();
    }

    /** 檢查並把 request 填進交易（含重建分錄）。錯誤以 FieldException 標出欄位，多筆編輯會用到。 */
    void fill(String userId, Transaction t, TransactionRequest req) {
        String kind = req.kind();
        if (!KINDS.contains(kind)) throw field("kind", bad("交易類型需為收入、支出或轉帳"));
        String date = on("date", () -> Dates.check(req.date(), "日期"));
        Account from = on("account_id", () -> usable(userId, req.accountId(), date));

        String title = req.title() == null ? "" : req.title().trim();
        if (title.isEmpty() && Transaction.TRANSFER.equals(kind)) title = "轉帳";
        if (title.isEmpty() || title.length() > 100) throw field("title", bad("項目名稱需為 1～100 字"));
        String note = req.note() == null ? "" : req.note().trim();
        if (note.length() > 500) throw field("note", bad("備註最多 500 字"));

        BigDecimal amount = on("amount", () -> Currencies.check(req.amount(), from.currency, true, "金額"));
        String categoryId = null;
        List<Entry> entries;
        if (Transaction.TRANSFER.equals(kind)) {
            Account to = on("to_account_id", () -> usable(userId, req.toAccountId(), date));
            if (to.id.equals(from.id)) throw field("to_account_id", bad("轉出與轉入帳戶不能相同"));
            BigDecimal toAmount;
            if (from.currency.equals(to.currency)) {
                if (req.toAmount() != null && req.toAmount().compareTo(amount) != 0) throw field("to_amount", bad("同幣別轉帳的轉入金額必須等於轉出金額"));
                toAmount = amount;
            } else {
                if (req.toAmount() == null) throw field("to_amount", bad("跨幣別轉帳需要填寫轉入金額（" + to.currency + "）"));
                toAmount = on("to_amount", () -> Currencies.check(req.toAmount(), to.currency, true, "轉入金額"));
            }
            BigDecimal fee = null;
            String feeCategoryId = null;
            if (req.fee() != null && req.fee().signum() != 0) {
                fee = on("fee", () -> Currencies.check(req.fee(), from.currency, true, "手續費"));
                Category fc = on("fee", () -> req.feeCategoryId() == null || req.feeCategoryId().isBlank()
                        ? categories.system(userId, Category.EXPENSE, Category.SYS_FEE)
                        : categories.get(userId, req.feeCategoryId()));
                if (!Category.EXPENSE.equals(fc.kind)) throw field("fee", bad("手續費分類必須是支出分類"));
                feeCategoryId = fc.id;
            }
            entries = buildTransferEntries(from.id, amount, to.id, toAmount, fee, feeCategoryId);
        } else {
            Category c = on("category_id", () -> categories.get(userId, req.categoryId()));
            if (!kind.equals(c.kind)) throw field("category_id", bad("分類「" + c.name + "」不是" + kindLabel(kind) + "分類"));
            categoryId = c.id;
            entries = List.of(buildMainEntry(kind, from.id, amount));
        }
        List<String> tags = on("tags", () -> normalizeTags(req.tags()));

        t.kind = kind;
        t.date = date;
        t.title = title;
        t.note = note;
        t.tags = tags;
        t.categoryId = categoryId;
        t.entries = new ArrayList<>(entries);
    }

    private static <T> T on(String field, java.util.function.Supplier<T> s) {
        try {
            return s.get();
        } catch (ResponseStatusException e) {
            throw FieldException.of(field, e);
        }
    }

    private static FieldException field(String field, ResponseStatusException e) {
        return FieldException.of(field, e);
    }

    /** 收入為正、支出為負的單一分錄（SD 2.5 規則表） */
    static Entry buildMainEntry(String kind, String accountId, BigDecimal amount) {
        return new Entry(accountId, Transaction.EXPENSE.equals(kind) ? amount.negate() : amount, Transaction.ROLE_MAIN, null);
    }

    /** 轉出為負、轉入為正，手續費記在轉出帳戶（SD 2.5 規則表） */
    static List<Entry> buildTransferEntries(String fromId, BigDecimal amount, String toId, BigDecimal toAmount,
                                            BigDecimal fee, String feeCategoryId) {
        List<Entry> list = new ArrayList<>();
        list.add(new Entry(fromId, amount.negate(), Transaction.ROLE_TRANSFER_OUT, null));
        list.add(new Entry(toId, toAmount, Transaction.ROLE_TRANSFER_IN, null));
        if (fee != null) list.add(new Entry(fromId, fee.negate(), Transaction.ROLE_FEE, feeCategoryId));
        return list;
    }

    /** 自己的、未封存的帳戶，且日期不早於開戶日期（SA FR-2.7） */
    private Account usable(String userId, String accountId, String date) {
        if (accountId == null || accountId.isBlank()) throw bad("請選擇帳戶");
        Account a = accounts.get(userId, accountId);
        if (a.archivedAt != null) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, "帳戶「" + a.name + "」已封存，不能記帳");
        if (a.openingDate != null && date.compareTo(a.openingDate) < 0) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT,
                    "交易日期不能早於帳戶「" + a.name + "」的開戶日期（" + a.openingDate + "）");
        }
        return a;
    }

    static List<String> normalizeTags(List<String> tags) {
        if (tags == null) return new ArrayList<>();
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (String s : tags) {
            if (s == null) continue;
            String v = s.trim().replaceFirst("^#", "");
            if (v.isEmpty()) continue;
            if (v.length() > 20) throw bad("標籤最多 20 字：" + v);
            out.add(v);
        }
        if (out.size() > 10) throw bad("每筆交易最多 10 個標籤");
        return new ArrayList<>(out);
    }

    // ---------- 讀取 ----------

    public Transaction get(String userId, String id) {
        return repo.findById(id == null ? "" : id).filter(t -> userId.equals(t.userId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "找不到此交易"));
    }

    /** 明細（SA FR-3.4、3.5）：篩選、摘要、每日小計（依幣別），再分頁 */
    public Page list(String userId, String from, String to, List<String> kinds, List<String> accountIds,
                     List<String> categoryIds, List<String> tags, String q, int page, int size) {
        if (from != null) Dates.check(from, "起始日期");
        if (to != null) Dates.check(to, "結束日期");
        if (size < 1 || size > MAX_PAGE_SIZE) throw bad("每頁筆數需為 1～" + MAX_PAGE_SIZE);
        if (page < 0) throw bad("頁碼不能是負數");
        // 帳戶、分類先確認是自己的，再展開子分類
        List<String> accIds = accountIds == null ? List.of() : accountIds.stream().map(id -> accounts.get(userId, id).id).toList();
        List<String> catIds = categoryIds == null ? List.of()
                : categoryIds.stream().flatMap(id -> categories.withChildren(userId, id).stream()).distinct().toList();
        List<Transaction> all = ledger.search(userId, new LedgerQueries.Filter(from, to, kinds, accIds, catIds, tags, q));

        Map<String, String> currencyOf = currencyMap(userId);
        Map<String, Totals> summary = new TreeMap<>();
        Map<String, Map<String, Totals>> daily = new LinkedHashMap<>();
        for (Transaction t : all) {
            for (Map.Entry<String, Totals> e : pnl(t, currencyOf).entrySet()) {
                summary.merge(e.getKey(), e.getValue(), Totals::add);
                daily.computeIfAbsent(t.date, d -> new TreeMap<>()).merge(e.getKey(), e.getValue(), Totals::add);
            }
        }
        List<DailyTotal> dailyTotals = daily.entrySet().stream().map(e -> new DailyTotal(e.getKey(), e.getValue())).toList();
        int fromIdx = Math.min(page * size, all.size());
        List<Transaction> items = all.subList(fromIdx, Math.min(fromIdx + size, all.size()));
        return new Page(items, page, size, all.size(), summary, dailyTotals);
    }

    /**
     * 一筆交易的收支（依幣別）：只計 main 與 fee 分錄（SD 2.5 收支統計規則）。
     * 支出以正數表示金額大小。
     */
    static Map<String, Totals> pnl(Transaction t, Map<String, String> currencyOf) {
        Map<String, Totals> out = new HashMap<>();
        for (Entry e : t.entries) {
            if (!Transaction.ROLE_MAIN.equals(e.role) && !Transaction.ROLE_FEE.equals(e.role)) continue;
            String cur = currencyOf.getOrDefault(e.accountId, "?");
            Totals v = e.amount.signum() >= 0 ? new Totals(e.amount, BigDecimal.ZERO) : new Totals(BigDecimal.ZERO, e.amount.negate());
            out.merge(cur, v, Totals::add);
        }
        return out;
    }

    /** 依項目名稱開頭，回傳最常用的分類與帳戶（SA FR-3.7） */
    public List<Suggestion> suggest(String userId, String q) {
        if (q == null || q.isBlank()) return List.of();
        Map<String, List<Transaction>> byTitle = ledger.titleStartsWith(userId, q, 300).stream()
                .filter(t -> !Transaction.INVESTMENT.equals(t.kind))
                .collect(Collectors.groupingBy(t -> t.title, LinkedHashMap::new, Collectors.toList()));
        return byTitle.values().stream()
                .map(list -> {
                    Transaction latest = list.get(0);
                    return new Suggestion(latest.title, latest.kind, mostCommon(list, t -> t.categoryId),
                            mostCommon(list, t -> t.entries.isEmpty() ? null : t.entries.get(0).accountId), list.size());
                })
                .sorted(Comparator.comparingInt(Suggestion::count).reversed())
                .limit(5)
                .toList();
    }

    private static String mostCommon(List<Transaction> list, Function<Transaction, String> f) {
        return list.stream().map(f).filter(Objects::nonNull)
                .collect(Collectors.groupingBy(Function.identity(), LinkedHashMap::new, Collectors.counting()))
                .entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null);
    }

    /** 帳戶詳細頁：此帳戶的交易（新到舊）與每筆交易後的餘額 */
    public AccountLedger accountLedger(String userId, String accountId, int page, int size) {
        if (size < 1 || size > MAX_PAGE_SIZE) throw bad("每頁筆數需為 1～" + MAX_PAGE_SIZE);
        Account a = accounts.getWithBalance(userId, accountId);
        List<Transaction> oldestFirst = ledger.byAccount(userId, accountId);
        List<LedgerRow> rows = new ArrayList<>();
        BigDecimal running = a.openingBalance;
        for (Transaction t : oldestFirst) {
            BigDecimal delta = net(t, accountId);
            running = running.add(delta);
            rows.add(new LedgerRow(t, delta, running));
        }
        List<LedgerRow> newestFirst = new ArrayList<>(rows.reversed());
        int fromIdx = Math.min(Math.max(page, 0) * size, newestFirst.size());
        return new AccountLedger(a, newestFirst.subList(fromIdx, Math.min(fromIdx + size, newestFirst.size())),
                page, size, newestFirst.size());
    }

    /** 最近 days 天每天結束時的餘額（帳戶詳細頁走勢，SD 3.3 history） */
    public List<DayBalance> dailyBalances(String userId, String accountId, int days) {
        if (days < 1 || days > 3660) throw bad("天數需為 1～3660");
        Account a = accounts.get(userId, accountId);
        LocalDate end = Dates.today();
        LocalDate start = end.minusDays(days - 1L);
        Map<String, BigDecimal> deltaByDate = new TreeMap<>();
        for (Transaction t : ledger.byAccount(userId, accountId)) deltaByDate.merge(t.date, net(t, accountId), BigDecimal::add);
        BigDecimal running = a.openingBalance;
        for (Map.Entry<String, BigDecimal> e : deltaByDate.entrySet()) {
            if (e.getKey().compareTo(start.toString()) < 0) running = running.add(e.getValue());
        }
        List<DayBalance> out = new ArrayList<>();
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            String key = d.toString();
            running = running.add(deltaByDate.getOrDefault(key, BigDecimal.ZERO));
            if (a.openingDate == null || key.compareTo(a.openingDate) >= 0) out.add(new DayBalance(key, running));
        }
        return out;
    }

    /** 交易對某個帳戶的淨影響（同一帳戶可能有轉出＋手續費兩筆分錄） */
    static BigDecimal net(Transaction t, String accountId) {
        return t.entries.stream().filter(e -> accountId.equals(e.accountId)).map(e -> e.amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private Map<String, String> currencyMap(String userId) {
        Map<String, String> m = new HashMap<>();
        for (Account a : accounts.list(userId, true)) m.put(a.id, a.currency);
        return m;
    }

    private List<Transaction> ownAll(String userId, Collection<String> ids) {
        if (ids == null || ids.isEmpty()) throw bad("請先選取交易");
        if (ids.size() > 500) throw bad("一次最多處理 500 筆");
        Set<String> want = new LinkedHashSet<>(ids);
        List<Transaction> found = ledger.findByIds(userId, want);
        if (found.size() != want.size()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "有部分交易不存在或已被刪除，請重新整理");
        return found;
    }

    private static String kindLabel(String kind) {
        return switch (kind) {
            case Transaction.INCOME -> "收入";
            case Transaction.EXPENSE -> "支出";
            default -> "轉帳";
        };
    }

    private static ResponseStatusException bad(String m) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, m);
    }
}
