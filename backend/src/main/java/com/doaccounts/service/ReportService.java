package com.doaccounts.service;

import com.doaccounts.model.Account;
import com.doaccounts.model.Category;
import com.doaccounts.model.Transaction;
import com.doaccounts.model.User;
import com.doaccounts.service.Flows.Flow;
import com.doaccounts.service.LedgerQueries.AccountMonth;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * 報表（SA FR-6、SD 3.7、4.6）。一律換算成本位幣，轉帳不計入收支。
 */
@Service
public class ReportService {

    /** 「全部期間」最多回溯的月數，避免開戶日期打錯（例如 1900 年）時產生上萬個月 */
    static final int MAX_MONTHS = 240;

    private final AccountService accounts;
    private final CategoryService categories;
    private final LedgerQueries ledger;
    private final FxService fx;
    private final BudgetService budgets;

    public ReportService(AccountService accounts, CategoryService categories, LedgerQueries ledger, FxService fx, BudgetService budgets) {
        this.accounts = accounts;
        this.categories = categories;
        this.ledger = ledger;
        this.fx = fx;
        this.budgets = budgets;
    }

    // ---------- 淨資產趨勢 ----------

    public record NetWorthPoint(String month, BigDecimal assets, BigDecimal liabilities, BigDecimal investments, BigDecimal netWorth) {}

    public record NetWorth(String baseCurrency, List<NetWorthPoint> points) {}

    /** @param months 最近幾個月（6、12…）；0 表示全部期間。都不早於最早的開戶月份 */
    public NetWorth netWorth(User user, int months) {
        if (months < 0 || months > MAX_MONTHS) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "月數需在 0～" + MAX_MONTHS + " 之間");
        String base = FxService.baseOf(user);
        LocalDate today = Dates.today();
        YearMonth now = YearMonth.from(today);
        List<Account> all = accounts.list(user.id, true);
        if (all.isEmpty()) return new NetWorth(base, List.of());

        // 從最早的開戶月份起（還沒有任何帳戶的月份不列，避免「變化」從 0 起算）
        YearMonth first = all.stream().map(a -> YearMonth.parse(a.openingDate.substring(0, 7))).min(Comparator.naturalOrder()).orElse(now);
        YearMonth limit = now.minusMonths((months > 0 ? months : MAX_MONTHS) - 1);
        if (first.isBefore(limit)) first = limit;
        if (first.isAfter(now)) first = now;

        // 每個帳戶每個月的分錄加總 → 依月份累加成月底餘額（SD 4.6 步驟 1～2）
        Map<String, TreeMap<String, BigDecimal>> monthly = new HashMap<>();
        for (AccountMonth am : ledger.sumByAccountMonth(user.id)) {
            monthly.computeIfAbsent(am.accountId(), k -> new TreeMap<>()).merge(am.month(), am.sum(), BigDecimal::add);
        }
        RateTable rates = fx.latest(user);

        List<NetWorthPoint> points = new ArrayList<>();
        for (YearMonth m = first; !m.isAfter(now); m = m.plusMonths(1)) {
            String end = m.equals(now) ? today.toString() : m.atEndOfMonth().toString();
            BigDecimal assets = BigDecimal.ZERO;
            BigDecimal liabilities = BigDecimal.ZERO;
            for (Account a : all) {
                if (a.openingDate.compareTo(end) > 0) continue; // 還沒開戶
                BigDecimal bal = a.openingBalance;
                TreeMap<String, BigDecimal> sums = monthly.get(a.id);
                if (sums != null) {
                    for (BigDecimal v : sums.headMap(m.toString(), true).values()) bal = bal.add(v);
                }
                BigDecimal v = rates.toBase(bal, a.currency, end);
                // 信用卡、貸款固定算負債；其他依正負（SD 4.6 步驟 5）
                if (Account.LIABILITY_KINDS.contains(a.kind) || v.signum() < 0) liabilities = liabilities.add(v);
                else assets = assets.add(v);
            }
            assets = Currencies.round(assets, base);
            liabilities = Currencies.round(liabilities, base);
            // 投資市值於階段 4 加入
            points.add(new NetWorthPoint(m.toString(), assets, liabilities, BigDecimal.ZERO, assets.add(liabilities)));
        }
        return new NetWorth(base, points);
    }

    // ---------- 收支結構 ----------

    public record BreakdownChild(String categoryId, BigDecimal amount) {}

    public record BreakdownItem(String categoryId, BigDecimal amount, BigDecimal percentage, List<BreakdownChild> children) {}

    public record Breakdown(String baseCurrency, String kind, String from, String to, BigDecimal total, List<BreakdownItem> items) {}

    /**
     * 依大類彙總，子類列在 children；直接記在大類本身的金額以大類 id 列為一個 child（有其他子類時才列）。
     * 金額一律為正數。
     */
    public Breakdown breakdown(User user, String from, String to, String kind) {
        Dates.check(from, "開始日期");
        Dates.check(to, "結束日期");
        if (from.compareTo(to) > 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "開始日期不能晚於結束日期");
        boolean expense = !Category.INCOME.equals(kind);
        String base = FxService.baseOf(user);
        Map<String, Category> catById = categories.list(user.id).stream().collect(Collectors.toMap(c -> c.id, c -> c));

        Map<String, BigDecimal> direct = new HashMap<>();
        for (Flow f : flows(user, from, to)) {
            if (expense ? !f.expense() : !f.income()) continue;
            direct.merge(f.categoryId() == null ? "" : f.categoryId(), f.amount().abs(), BigDecimal::add);
        }

        Map<String, Map<String, BigDecimal>> byRoot = new HashMap<>();
        for (Map.Entry<String, BigDecimal> e : direct.entrySet()) {
            Category c = catById.get(e.getKey());
            String root = c == null ? e.getKey() : (c.parentId != null ? c.parentId : c.id);
            byRoot.computeIfAbsent(root, k -> new HashMap<>()).merge(e.getKey(), e.getValue(), BigDecimal::add);
        }

        BigDecimal total = direct.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        List<BreakdownItem> items = new ArrayList<>();
        for (Map.Entry<String, Map<String, BigDecimal>> e : byRoot.entrySet()) {
            BigDecimal sum = e.getValue().values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            List<BreakdownChild> children = e.getValue().size() == 1 && e.getValue().containsKey(e.getKey()) ? List.of()
                    : e.getValue().entrySet().stream()
                    .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                    .map(c -> new BreakdownChild(c.getKey(), Currencies.round(c.getValue(), base))).toList();
            items.add(new BreakdownItem(e.getKey().isEmpty() ? null : e.getKey(), Currencies.round(sum, base), pct(sum, total), children));
        }
        items.sort(Comparator.comparing(BreakdownItem::amount).reversed());
        return new Breakdown(base, expense ? Category.EXPENSE : Category.INCOME, from, to, Currencies.round(total, base), items);
    }

    // ---------- 每日收支 ----------

    public record DayPoint(String date, BigDecimal income, BigDecimal expense) {}

    public record Daily(String baseCurrency, String month, BigDecimal income, BigDecimal expense, BigDecimal averageExpense,
                        DayPoint maxExpenseDay, List<DayPoint> days) {}

    public Daily daily(User user, String month) {
        YearMonth m = BudgetService.parseMonth(month);
        String base = FxService.baseOf(user);
        Map<String, BigDecimal[]> byDay = new TreeMap<>();
        for (int d = 1; d <= m.lengthOfMonth(); d++) byDay.put(m.atDay(d).toString(), new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
        for (Flow f : flows(user, m.atDay(1).toString(), m.atEndOfMonth().toString())) {
            BigDecimal[] v = byDay.get(f.date());
            if (v == null) continue;
            if (f.income()) v[0] = v[0].add(f.amount());
            else if (f.expense()) v[1] = v[1].add(f.amount().negate());
        }
        List<DayPoint> days = byDay.entrySet().stream()
                .map(e -> new DayPoint(e.getKey(), Currencies.round(e.getValue()[0], base), Currencies.round(e.getValue()[1], base))).toList();
        BigDecimal income = days.stream().map(DayPoint::income).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal expense = days.stream().map(DayPoint::expense).reduce(BigDecimal.ZERO, BigDecimal::add);
        int elapsed = BudgetService.elapsedDays(m);
        BigDecimal avg = elapsed == 0 ? BigDecimal.ZERO : Currencies.round(expense.divide(BigDecimal.valueOf(elapsed), 4, RoundingMode.HALF_UP), base);
        DayPoint max = days.stream().filter(d -> d.expense().signum() > 0).max(Comparator.comparing(DayPoint::expense)).orElse(null);
        return new Daily(base, m.toString(), income, expense, avg, max, days);
    }

    // ---------- 分類趨勢 ----------

    public record TrendPoint(String month, BigDecimal amount, BigDecimal budget) {}

    public record CategoryTrend(String baseCurrency, String categoryId, String kind, BigDecimal average, List<TrendPoint> points) {}

    /** 某分類（含子類）最近 N 個月的金額（正數）與當月預算；平均不含本月（本月還沒過完） */
    public CategoryTrend categoryTrend(User user, String categoryId, int months) {
        if (months < 1 || months > 60) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "月數需在 1～60 之間");
        Category c = categories.get(user.id, categoryId);
        Set<String> ids = Set.copyOf(categories.withChildren(user.id, categoryId));
        String base = FxService.baseOf(user);
        YearMonth now = YearMonth.from(Dates.today());
        YearMonth first = now.minusMonths(months - 1);
        boolean expense = Category.EXPENSE.equals(c.kind);

        Map<String, BigDecimal> byMonth = new TreeMap<>();
        for (YearMonth m = first; !m.isAfter(now); m = m.plusMonths(1)) byMonth.put(m.toString(), BigDecimal.ZERO);
        for (Flow f : flows(user, first.atDay(1).toString(), now.atEndOfMonth().toString())) {
            if (f.categoryId() == null || !ids.contains(f.categoryId())) continue;
            if (expense ? !f.expense() : !f.income()) continue;
            byMonth.merge(f.date().substring(0, 7), f.amount().abs(), BigDecimal::add);
        }
        Map<String, BigDecimal> budget = expense ? budgets.amounts(user.id, categoryId, List.copyOf(byMonth.keySet())) : Map.of();
        List<TrendPoint> points = byMonth.entrySet().stream()
                .map(e -> new TrendPoint(e.getKey(), Currencies.round(e.getValue(), base), budget.get(e.getKey()))).toList();
        List<TrendPoint> past = points.subList(0, points.size() - 1);
        BigDecimal avg = past.isEmpty() ? BigDecimal.ZERO : Currencies.round(past.stream().map(TrendPoint::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add).divide(BigDecimal.valueOf(past.size()), 4, RoundingMode.HALF_UP), base);
        return new CategoryTrend(base, categoryId, c.kind, avg, points);
    }

    // ---------- 共用 ----------

    private List<Flow> flows(User user, String from, String to) {
        Map<String, String> currencyOf = new HashMap<>();
        for (Account a : accounts.list(user.id, true)) currencyOf.put(a.id, a.currency);
        List<Transaction> txs = ledger.search(user.id, new LedgerQueries.Filter(from, to, null, null, null, null, null));
        String today = Dates.today().toString();
        return Flows.of(txs, currencyOf, fx.table(user, to.compareTo(today) > 0 ? to : today));
    }

    private static BigDecimal pct(BigDecimal v, BigDecimal total) {
        return total.signum() == 0 ? BigDecimal.ZERO : v.multiply(BigDecimal.valueOf(100)).divide(total, 1, RoundingMode.HALF_UP);
    }
}
