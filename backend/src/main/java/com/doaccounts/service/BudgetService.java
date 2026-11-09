package com.doaccounts.service;

import com.doaccounts.model.Account;
import com.doaccounts.model.Budget;
import com.doaccounts.model.Category;
import com.doaccounts.model.User;
import com.doaccounts.repository.BudgetRepository;
import com.doaccounts.service.Flows.Flow;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 預算（SA FR-5、SD 3.6）。金額以本位幣計；花費依交易日匯率換算；大類預算包含子類花費。
 */
@Service
public class BudgetService {

    private static final Pattern MONTH = Pattern.compile("^\\d{4}-\\d{2}$");
    /** 分類 id 為 null（總預算）在 Map 裡的鍵 */
    private static final String TOTAL = "";
    static final int HISTORY_MONTHS = 6;

    private final BudgetRepository repo;
    private final CategoryService categories;
    private final AccountService accounts;
    private final LedgerQueries ledger;
    private final FxService fx;

    public BudgetService(BudgetRepository repo, CategoryService categories, AccountService accounts, LedgerQueries ledger, FxService fx) {
        this.repo = repo;
        this.categories = categories;
        this.accounts = accounts;
        this.ledger = ledger;
        this.fx = fx;
    }

    public record BudgetRequest(String month, String categoryId, BigDecimal amount, Boolean recurring) {}

    /**
     * 一項有效預算。sourceMonth 是實際設定的月份；inherited = 由之前的循環預算沿用；
     * derived = 沒有設總預算，由各分類預算加總而來（只會出現在 total）。
     */
    public record BudgetItem(String id, String categoryId, String sourceMonth, BigDecimal amount, boolean recurring,
                             boolean inherited, boolean derived, BigDecimal spent, BigDecimal remaining,
                             BigDecimal percentage, BigDecimal projected) {}

    public record Unbudgeted(String categoryId, BigDecimal spent) {}

    public record HistoryPoint(String month, BigDecimal budget, BigDecimal spent) {}

    public record BudgetView(String month, String baseCurrency, int warnPct, int daysInMonth, int daysElapsed,
                             BudgetItem total, List<BudgetItem> items, List<Unbudgeted> unbudgeted,
                             List<HistoryPoint> history) {}

    // ---------- 查詢 ----------

    public BudgetView view(User user, String month) {
        YearMonth m = parseMonth(month);
        String base = FxService.baseOf(user);
        List<Budget> records = repo.findByUserIdAndMonthLessThanEqual(user.id, m.toString());
        Map<String, Budget> eff = effective(records, m.toString());

        List<Category> cats = categories.list(user.id);
        Map<String, Category> catById = cats.stream().collect(Collectors.toMap(c -> c.id, c -> c));

        // 一次載入近 6 個月的交易，當月與歷史共用
        YearMonth first = m.minusMonths(HISTORY_MONTHS - 1);
        Map<String, List<Flow>> flowsByMonth = expenseFlowsByMonth(user, first, m);
        Map<String, BigDecimal> spentByCat = new HashMap<>();
        BigDecimal totalSpent = BigDecimal.ZERO;
        for (Flow f : flowsByMonth.getOrDefault(m.toString(), List.of())) {
            spentByCat.merge(f.categoryId(), f.amount().negate(), BigDecimal::add);
            totalSpent = totalSpent.add(f.amount().negate());
        }

        int days = m.lengthOfMonth();
        int elapsed = elapsedDays(m);

        List<BudgetItem> items = new ArrayList<>();
        for (Map.Entry<String, Budget> e : eff.entrySet()) {
            if (TOTAL.equals(e.getKey())) continue;
            Category c = catById.get(e.getKey());
            if (c == null || !Category.EXPENSE.equals(c.kind)) continue; // 分類已刪除或改成收入
            items.add(item(e.getValue(), m, rolledUp(c.id, cats, spentByCat), days, elapsed, base, false));
        }
        // 依分類排序（大類在前、子類接在大類後面）
        Map<String, Integer> order = categoryOrder(cats);
        items.sort(Comparator.comparing(i -> order.getOrDefault(i.categoryId(), Integer.MAX_VALUE)));

        BudgetItem total;
        Budget t = eff.get(TOTAL);
        if (t != null) {
            total = item(t, m, totalSpent, days, elapsed, base, false);
        } else if (!items.isEmpty()) {
            Budget sum = new Budget();
            sum.month = m.toString();
            sum.amount = derivedTotal(items, catById);
            total = item(sum, m, totalSpent, days, elapsed, base, true);
        } else {
            total = null;
        }

        List<Unbudgeted> unbudgeted = new ArrayList<>();
        for (Category root : cats.stream().filter(c -> c.parentId == null && Category.EXPENSE.equals(c.kind))
                .sorted(Comparator.comparingInt(c -> c.order)).toList()) {
            boolean covered = eff.containsKey(root.id) || cats.stream().anyMatch(c -> root.id.equals(c.parentId) && eff.containsKey(c.id));
            BigDecimal s = rolledUp(root.id, cats, spentByCat);
            if (!covered && s.signum() > 0) unbudgeted.add(new Unbudgeted(root.id, Currencies.round(s, base)));
        }

        List<HistoryPoint> history = new ArrayList<>();
        for (YearMonth h = first; !h.isAfter(m); h = h.plusMonths(1)) {
            BigDecimal spent = flowsByMonth.getOrDefault(h.toString(), List.of()).stream()
                    .map(f -> f.amount().negate()).reduce(BigDecimal.ZERO, BigDecimal::add);
            history.add(new HistoryPoint(h.toString(), totalBudgetOf(records, h.toString(), catById), Currencies.round(spent, base)));
        }

        return new BudgetView(m.toString(), base, user.settingsOrDefault().budgetWarnPct, days, elapsed,
                total, items, unbudgeted, history);
    }

    /** 某分類（null = 總預算）在各月份的有效預算金額，沒有預算的月份為 null（分類趨勢報表用） */
    public Map<String, BigDecimal> amounts(String userId, String categoryId, List<String> months) {
        if (months.isEmpty()) return Map.of();
        List<Budget> records = repo.findByUserIdAndMonthLessThanEqual(userId, months.getLast());
        Map<String, BigDecimal> out = new LinkedHashMap<>();
        for (String month : months) {
            Budget b = effective(records, month).get(key(categoryId));
            out.put(month, b == null ? null : b.amount);
        }
        return out;
    }

    // ---------- 寫入 ----------

    /** 設定某月某分類的預算（已有就覆蓋，FR-5.1） */
    public Budget upsert(User user, BudgetRequest req) {
        YearMonth m = parseMonth(req.month());
        String catId = blankToNull(req.categoryId());
        if (catId != null) checkExpenseCategory(user.id, catId);
        BigDecimal amount = Currencies.check(req.amount(), FxService.baseOf(user), true, "預算金額");
        Budget b = own(user.id, m.toString(), catId);
        if (b == null) {
            b = new Budget();
            b.userId = user.id;
            b.month = m.toString();
            b.categoryId = catId;
        }
        b.amount = amount;
        b.recurring = Boolean.TRUE.equals(req.recurring());
        return repo.save(b);
    }

    /**
     * 從這個月起移除某分類的預算：刪掉這個月的設定；若之前還有循環預算會沿用到這個月，
     * 就寫入「停止」標記（amount = null），讓這個月與之後不再沿用。
     */
    public void delete(User user, String month, String categoryId) {
        YearMonth m = parseMonth(month);
        String catId = blankToNull(categoryId);
        List<Budget> records = new ArrayList<>(repo.findByUserIdAndMonthLessThanEqual(user.id, m.toString()));
        Budget own = own(records, m.toString(), catId);
        boolean active = effective(records, m.toString()).containsKey(key(catId));
        if ((own == null || own.amount == null) && !active) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "這個月沒有這項預算");
        if (own != null) {
            repo.delete(own);
            records.remove(own);
        }
        if (effective(records, m.toString()).containsKey(key(catId))) {
            Budget stop = new Budget();
            stop.userId = user.id;
            stop.month = m.toString();
            stop.categoryId = catId;
            stop.amount = null;
            stop.recurring = true;
            repo.save(stop);
        }
    }

    /** 把 from 月份的有效預算複製到 to 月份（to 已經自己設定過的分類不覆蓋，FR-5.3）；回傳複製筆數 */
    public int copy(User user, String from, String to) {
        YearMonth f = parseMonth(from);
        YearMonth t = parseMonth(to);
        if (f.equals(t)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "來源與目標月份相同");
        Map<String, Budget> source = effective(repo.findByUserIdAndMonthLessThanEqual(user.id, f.toString()), f.toString());
        if (source.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, f + " 沒有任何預算可以複製");
        List<Budget> target = repo.findByUserIdAndMonthLessThanEqual(user.id, t.toString());
        int n = 0;
        for (Budget s : source.values()) {
            Budget b = own(target, t.toString(), s.categoryId);
            if (b != null && b.amount != null) continue; // 目標月份自己設定過的不覆蓋；停止標記則改成複製的金額
            if (b == null) {
                b = new Budget();
                b.userId = user.id;
                b.month = t.toString();
                b.categoryId = s.categoryId;
            }
            b.amount = s.amount;
            b.recurring = s.recurring;
            repo.save(b);
            n++;
        }
        return n;
    }

    // ---------- 計算 ----------

    /**
     * 每個分類在 month 的有效預算：月份 ≤ month 中最新的一筆；同月直接用，較早的只有循環才沿用；
     * amount 為 null（停止標記）表示沒有預算。
     */
    static Map<String, Budget> effective(List<Budget> records, String month) {
        Map<String, Budget> latest = new HashMap<>();
        for (Budget b : records) {
            if (b.month.compareTo(month) > 0) continue;
            latest.merge(key(b.categoryId), b, (a, c) -> c.month.compareTo(a.month) > 0 ? c : a);
        }
        Map<String, Budget> out = new LinkedHashMap<>();
        latest.forEach((k, b) -> {
            if (b.amount != null && (b.month.equals(month) || b.recurring)) out.put(k, b);
        });
        return out;
    }

    private BudgetItem item(Budget b, YearMonth m, BigDecimal spent, int days, int elapsed, String base, boolean derived) {
        BigDecimal s = Currencies.round(spent, base);
        BigDecimal pct = b.amount.signum() == 0 ? BigDecimal.ZERO
                : s.multiply(BigDecimal.valueOf(100)).divide(b.amount, 1, RoundingMode.HALF_UP);
        BigDecimal projected = elapsed == 0 || elapsed >= days ? s
                : Currencies.round(spent.multiply(BigDecimal.valueOf(days)).divide(BigDecimal.valueOf(elapsed), 4, RoundingMode.HALF_UP), base);
        return new BudgetItem(b.id, b.categoryId, b.month, b.amount, b.recurring, !m.toString().equals(b.month),
                derived, s, b.amount.subtract(s), pct, projected);
    }

    /** 沒有總預算時：大類預算加總，再加上「大類沒設預算」的子類預算（避免重複計算） */
    private static BigDecimal derivedTotal(List<BudgetItem> items, Map<String, Category> catById) {
        java.util.Set<String> budgeted = items.stream().map(BudgetItem::categoryId).collect(Collectors.toSet());
        BigDecimal sum = BigDecimal.ZERO;
        for (BudgetItem i : items) {
            Category c = catById.get(i.categoryId());
            if (c != null && c.parentId != null && budgeted.contains(c.parentId)) continue;
            sum = sum.add(i.amount());
        }
        return sum;
    }

    private static BigDecimal totalBudgetOf(List<Budget> records, String month, Map<String, Category> catById) {
        Map<String, Budget> eff = effective(records, month);
        Budget t = eff.get(TOTAL);
        if (t != null) return t.amount;
        BigDecimal sum = BigDecimal.ZERO;
        boolean any = false;
        for (Map.Entry<String, Budget> e : eff.entrySet()) {
            Category c = catById.get(e.getKey());
            if (c == null || !Category.EXPENSE.equals(c.kind)) continue;
            if (c.parentId != null && eff.containsKey(c.parentId)) continue;
            sum = sum.add(e.getValue().amount);
            any = true;
        }
        return any ? sum : null;
    }

    /** 分類本身加上子分類的花費（FR-5.2） */
    private static BigDecimal rolledUp(String catId, List<Category> cats, Map<String, BigDecimal> spentByCat) {
        BigDecimal s = spentByCat.getOrDefault(catId, BigDecimal.ZERO);
        for (Category c : cats) {
            if (catId.equals(c.parentId)) s = s.add(spentByCat.getOrDefault(c.id, BigDecimal.ZERO));
        }
        return s;
    }

    private Map<String, List<Flow>> expenseFlowsByMonth(User user, YearMonth from, YearMonth to) {
        Map<String, String> currencyOf = new HashMap<>();
        for (Account a : accounts.list(user.id, true)) currencyOf.put(a.id, a.currency);
        String end = to.atEndOfMonth().toString();
        RateTable rates = fx.table(user, max(end, Dates.today().toString()));
        var txs = ledger.search(user.id, new LedgerQueries.Filter(from.atDay(1).toString(), end, null, null, null, null, null));
        return Flows.of(txs, currencyOf, rates).stream().filter(Flow::expense)
                .collect(Collectors.groupingBy(f -> f.date().substring(0, 7)));
    }

    private static Map<String, Integer> categoryOrder(List<Category> cats) {
        Map<String, Integer> out = new HashMap<>();
        List<Category> roots = cats.stream().filter(c -> c.parentId == null).sorted(Comparator.comparingInt(c -> c.order)).toList();
        int i = 0;
        for (Category r : roots) {
            out.put(r.id, i++);
            for (Category c : cats.stream().filter(c -> r.id.equals(c.parentId)).sorted(Comparator.comparingInt(c -> c.order)).toList()) {
                out.put(c.id, i++);
            }
        }
        return out;
    }

    static int elapsedDays(YearMonth m) {
        YearMonth now = YearMonth.from(Dates.today());
        if (m.isBefore(now)) return m.lengthOfMonth();
        if (m.isAfter(now)) return 0;
        return Dates.today().getDayOfMonth();
    }

    private void checkExpenseCategory(String userId, String id) {
        Category c = categories.get(userId, id);
        if (!Category.EXPENSE.equals(c.kind)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "預算只能設定在支出分類");
    }

    private Budget own(String userId, String month, String catId) {
        return own(repo.findByUserIdAndMonthLessThanEqual(userId, month), month, catId);
    }

    private static Budget own(List<Budget> records, String month, String catId) {
        return records.stream().filter(b -> b.month.equals(month) && Objects.equals(b.categoryId, catId)).findFirst().orElse(null);
    }

    static YearMonth parseMonth(String s) {
        if (s == null || !MONTH.matcher(s).matches()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "月份格式錯誤，需為 YYYY-MM");
        try {
            return YearMonth.parse(s);
        } catch (DateTimeParseException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "月份不正確：" + s);
        }
    }

    private static String key(String categoryId) {
        return categoryId == null ? TOTAL : categoryId;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    private static String max(String a, String b) {
        return a.compareTo(b) >= 0 ? a : b;
    }
}
