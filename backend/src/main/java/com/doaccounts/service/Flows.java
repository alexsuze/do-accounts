package com.doaccounts.service;

import com.doaccounts.model.Transaction;
import com.doaccounts.model.Transaction.Entry;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 把交易拆成「收支流量」：只取 main 與 fee 分錄（轉帳本身不算收支，SD 2.5、SA FR-6.5），
 * 依交易日匯率換算成本位幣（SA FR-5.4）。預算、報表、總覽共用。
 */
public final class Flows {

    private Flows() {}

    /**
     * @param amount 本位幣，收入為正、支出為負（保留 4 位小數）
     * @param categoryId main 用交易的分類，fee 用分錄上的手續費分類
     */
    public record Flow(String date, String categoryId, BigDecimal amount) {
        public boolean expense() { return amount.signum() < 0; }
        public boolean income() { return amount.signum() > 0; }
    }

    public static List<Flow> of(List<Transaction> txs, Map<String, String> currencyOf, RateTable rates) {
        List<Flow> out = new ArrayList<>();
        for (Transaction t : txs) {
            for (Entry e : t.entries) {
                boolean main = Transaction.ROLE_MAIN.equals(e.role);
                if (!main && !Transaction.ROLE_FEE.equals(e.role)) continue;
                String cur = currencyOf.get(e.accountId);
                if (cur == null) continue;
                out.add(new Flow(t.date, main ? t.categoryId : e.categoryId, rates.toBase(e.amount, cur, t.date)));
            }
        }
        return out;
    }
}
