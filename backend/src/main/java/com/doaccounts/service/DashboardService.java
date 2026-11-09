package com.doaccounts.service;

import com.doaccounts.model.Account;
import com.doaccounts.model.Transaction;
import com.doaccounts.model.User;
import com.doaccounts.service.BudgetService.BudgetItem;
import com.doaccounts.service.BudgetService.BudgetView;
import com.doaccounts.service.Flows.Flow;
import com.doaccounts.service.TransactionService.Totals;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 總覽（SA FR-7、SD 3.7 dashboard）。外幣帳戶依最新匯率換算後併入淨資產；本月收支依交易日匯率換算。
 */
@Service
public class DashboardService {

    /** 總覽顯示的分類預算數（使用率最高的幾項） */
    static final int TOP_BUDGETS = 3;

    private final AccountService accounts;
    private final LedgerQueries ledger;
    private final FxService fx;
    private final BudgetService budgets;

    public DashboardService(AccountService accounts, LedgerQueries ledger, FxService fx, BudgetService budgets) {
        this.accounts = accounts;
        this.ledger = ledger;
        this.fx = fx;
        this.budgets = budgets;
    }

    /** 預算概況：總預算（可能為 null）與使用率最高的幾個分類 */
    public record BudgetSummary(int warnPct, BudgetItem total, List<BudgetItem> top) {}

    /**
     * @param ratesDate 換算外幣帳戶用的匯率中最舊的日期（FR-8.3 在畫面標示匯率日期）；沒有外幣帳戶時為 null
     * @param hiddenAccounts 設為「總覽隱藏」而未計入資產負債的帳戶數（有餘額的才算）
     */
    public record Dashboard(String baseCurrency, BigDecimal netWorth, BigDecimal assets, BigDecimal liabilities,
                            String ratesDate, int hiddenAccounts, String month, Totals monthTotals, BudgetSummary budget,
                            List<Transaction> recent) {}

    public Dashboard get(User user) {
        String base = FxService.baseOf(user);
        String today = Dates.today().toString();
        RateTable rates = fx.latest(user);
        BigDecimal assets = BigDecimal.ZERO;
        BigDecimal liabilities = BigDecimal.ZERO;
        String ratesDate = null;
        int hidden = 0;
        Map<String, String> currencyOf = new HashMap<>();
        for (Account a : accounts.list(user.id, true)) {
            currencyOf.put(a.id, a.currency);
            if (a.balance.signum() == 0) continue;
            if (!a.visible) { // 總覽隱藏的帳戶不計入資產、負債、淨資產（收支與預算照算）
                hidden++;
                continue;
            }
            if (!base.equals(a.currency)) {
                String d = rateDate(rates, a.currency, base, today);
                if (d != null && (ratesDate == null || d.compareTo(ratesDate) < 0)) ratesDate = d;
            }
            BigDecimal v = rates.toBase(a.balance, a.currency, today);
            // 信用卡、貸款固定算負債；其他依正負（SD 4.6）
            if (Account.LIABILITY_KINDS.contains(a.kind) || v.signum() < 0) liabilities = liabilities.add(v);
            else assets = assets.add(v);
        }
        assets = Currencies.round(assets, base);
        liabilities = Currencies.round(liabilities, base);

        YearMonth m = YearMonth.now(Dates.ZONE);
        String[] range = Dates.monthRange(m);
        BigDecimal income = BigDecimal.ZERO;
        BigDecimal expense = BigDecimal.ZERO;
        List<Transaction> txs = ledger.search(user.id, new LedgerQueries.Filter(range[0], range[1], null, null, null, null, null));
        for (Flow f : Flows.of(txs, currencyOf, fx.table(user, range[1]))) {
            if (f.income()) income = income.add(f.amount());
            else expense = expense.add(f.amount().negate());
        }
        Totals totals = new Totals(Currencies.round(income, base), Currencies.round(expense, base));

        BudgetView bv = budgets.view(user, m.toString());
        List<BudgetItem> top = bv.items().stream().sorted(Comparator.comparing(BudgetItem::percentage).reversed()).limit(TOP_BUDGETS).toList();

        return new Dashboard(base, assets.add(liabilities), assets, liabilities, ratesDate, hidden, m.toString(), totals,
                new BudgetSummary(bv.warnPct(), bv.total(), top), ledger.recent(user.id, 5));
    }

    /** 換算 currency → base 所用匯率的日期（兩邊都不是 TWD 時取較舊的一邊） */
    private static String rateDate(RateTable rates, String currency, String base, String date) {
        String d = null;
        for (String c : List.of(currency, base)) {
            if (Currencies.PIVOT.equals(c)) continue;
            var r = rates.record(c, date);
            if (r != null && (d == null || r.date.compareTo(d) < 0)) d = r.date;
        }
        return d;
    }
}
