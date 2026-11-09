package com.doaccounts.service;

import com.doaccounts.model.Transaction;
import com.doaccounts.model.Transaction.Investment;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 持倉計算（SD 4.5，純函式）：移動平均成本法。依日期、建立時間排序後逐筆累計；
 * 任何時間點賣出超過持有股數就丟出 422（修改或刪除過去的交易也會檢查）。金額都是證券幣別。
 */
public final class HoldingCalculator {

    private static final int SCALE = 10;

    private HoldingCalculator() {}

    /** 一個帳戶持有的一檔證券 */
    public static final class Position {
        public final String accountId;
        public final String securityId;
        public BigDecimal held = BigDecimal.ZERO;
        /** 目前持股的成本（含買進手續費、稅） */
        public BigDecimal cost = BigDecimal.ZERO;
        public BigDecimal realized = BigDecimal.ZERO;
        /** 現金股利（扣除費用後） */
        public BigDecimal dividends = BigDecimal.ZERO;
        /** 累計投入：所有買進的金額＋費用（報酬率的分母） */
        public BigDecimal invested = BigDecimal.ZERO;
        /** 年份 → 已實現損益 */
        public final Map<String, BigDecimal> realizedByYear = new HashMap<>();
        /** 年份 → 現金股利 */
        public final Map<String, BigDecimal> dividendsByYear = new HashMap<>();
        /** 年份 → 現金股利次數 */
        public final Map<String, Integer> dividendCountByYear = new HashMap<>();

        Position(String accountId, String securityId) {
            this.accountId = accountId;
            this.securityId = securityId;
        }

        public BigDecimal avg() {
            return held.signum() == 0 ? BigDecimal.ZERO : cost.divide(held, SCALE, RoundingMode.HALF_UP);
        }
    }

    public record Result(Map<String, Position> positions, Map<String, BigDecimal> realizedByTrade) {
        /** key = accountId|securityId */
        public Position get(String accountId, String securityId) {
            return positions.get(key(accountId, securityId));
        }
    }

    public static String key(String accountId, String securityId) {
        return accountId + "|" + securityId;
    }

    /** 投資交易依日期、建立時間排序（同一天先買後賣的順序以建立時間為準） */
    public static List<Transaction> sorted(List<Transaction> txs) {
        List<Transaction> list = new ArrayList<>(txs.stream().filter(t -> t.investment != null).toList());
        list.sort(Comparator.comparing((Transaction t) -> t.date)
                .thenComparing(t -> t.createdAt == null ? Instant.MAX : t.createdAt));
        return list;
    }

    /**
     * @param upTo 只計算這一天（含）以前的交易；null 為全部
     */
    public static Result compute(List<Transaction> txs, String upTo) {
        Map<String, Position> positions = new LinkedHashMap<>();
        Map<String, BigDecimal> realizedByTrade = new HashMap<>();
        for (Transaction t : sorted(txs)) {
            if (upTo != null && t.date.compareTo(upTo) > 0) break;
            Investment iv = t.investment;
            Position p = positions.computeIfAbsent(key(iv.accountId, iv.securityId), k -> new Position(iv.accountId, iv.securityId));
            BigDecimal qty = nz(iv.quantity);
            BigDecimal price = nz(iv.price);
            BigDecimal fee = nz(iv.fee);
            BigDecimal tax = nz(iv.tax);
            String year = t.date.substring(0, 4);
            switch (iv.tradeType) {
                case Investment.BUY -> {
                    BigDecimal c = qty.multiply(price).add(fee).add(tax);
                    p.cost = p.cost.add(c);
                    p.invested = p.invested.add(c);
                    p.held = p.held.add(qty);
                }
                case Investment.SELL -> {
                    if (qty.compareTo(p.held) > 0) {
                        throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT,
                                "賣出股數超過持有股數（" + t.date + " 時持有 " + p.held.stripTrailingZeros().toPlainString() + " 股）");
                    }
                    BigDecimal avg = p.avg();
                    BigDecimal out = qty.multiply(avg);
                    BigDecimal r = qty.multiply(price).subtract(fee).subtract(tax).subtract(out);
                    p.realized = p.realized.add(r);
                    p.realizedByYear.merge(year, r, BigDecimal::add);
                    if (t.id != null) realizedByTrade.put(t.id, r);
                    p.held = p.held.subtract(qty);
                    p.cost = p.held.signum() == 0 ? BigDecimal.ZERO : p.cost.subtract(out);
                }
                case Investment.CASH_DIVIDEND -> {
                    BigDecimal d = qty.multiply(price).subtract(fee).subtract(tax);
                    p.dividends = p.dividends.add(d);
                    p.dividendsByYear.merge(year, d, BigDecimal::add);
                    p.dividendCountByYear.merge(year, 1, Integer::sum);
                }
                case Investment.STOCK_DIVIDEND -> p.held = p.held.add(qty);
                case Investment.SPLIT -> p.held = p.held.multiply(qty);
                case Investment.FEE -> {
                    p.cost = p.cost.add(fee);
                    p.invested = p.invested.add(fee);
                }
                default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不支援的投資交易類型：" + iv.tradeType);
            }
        }
        return new Result(positions, realizedByTrade);
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
