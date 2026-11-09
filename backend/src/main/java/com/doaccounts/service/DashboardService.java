package com.doaccounts.service;

import com.doaccounts.model.Account;
import com.doaccounts.model.Transaction;
import com.doaccounts.model.User;
import com.doaccounts.service.TransactionService.Totals;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 總覽（SA FR-7、SD 3.7 dashboard）。
 * 階段 1 尚無匯率：淨資產與本月收支只計本位幣；外幣帳戶與外幣收支另列，階段 2 加入匯率換算後併入。
 */
@Service
public class DashboardService {

    private final AccountService accounts;
    private final LedgerQueries ledger;

    public DashboardService(AccountService accounts, LedgerQueries ledger) {
        this.accounts = accounts;
        this.ledger = ledger;
    }

    public record Dashboard(String baseCurrency, BigDecimal netWorth, BigDecimal assets, BigDecimal liabilities,
                            List<Account> foreignAccounts, String month, Totals monthTotals,
                            Map<String, Totals> monthOtherCurrencies, List<Transaction> recent) {}

    public Dashboard get(User user) {
        String base = user.baseCurrency == null ? "TWD" : user.baseCurrency;
        BigDecimal assets = BigDecimal.ZERO;
        BigDecimal liabilities = BigDecimal.ZERO;
        List<Account> foreign = new ArrayList<>();
        List<Account> all = accounts.list(user.id, true);
        Map<String, String> currencyOf = new TreeMap<>();
        for (Account a : all) {
            currencyOf.put(a.id, a.currency);
            if (!base.equals(a.currency)) {
                if (a.balance.signum() != 0 || a.archivedAt == null) foreign.add(a);
                continue;
            }
            // 信用卡、貸款固定算負債；其他依正負（SD 4.6）
            if (Account.LIABILITY_KINDS.contains(a.kind) || a.balance.signum() < 0) liabilities = liabilities.add(a.balance);
            else assets = assets.add(a.balance);
        }

        YearMonth m = YearMonth.now(Dates.ZONE);
        String[] range = Dates.monthRange(m);
        Map<String, Totals> byCurrency = new TreeMap<>();
        for (Transaction t : ledger.search(user.id, new LedgerQueries.Filter(range[0], range[1], null, null, null, null, null))) {
            TransactionService.pnl(t, currencyOf).forEach((cur, v) -> byCurrency.merge(cur, v, Totals::add));
        }
        Totals monthTotals = byCurrency.getOrDefault(base, Totals.zero());
        byCurrency.remove(base);

        return new Dashboard(base, assets.add(liabilities), assets, liabilities, foreign, m.toString(), monthTotals,
                byCurrency, ledger.recent(user.id, 5));
    }
}
