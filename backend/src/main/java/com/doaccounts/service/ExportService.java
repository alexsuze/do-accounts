package com.doaccounts.service;

import com.doaccounts.model.Account;
import com.doaccounts.model.Category;
import com.doaccounts.model.Transaction;
import com.doaccounts.model.Transaction.Entry;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 交易 CSV 匯出（SA FR-10.1、SD 4.7）：UTF-8 with BOM（Excel 直接開啟不亂碼）；
 * 文字欄位開頭為 = + - @ 時加 ' 前綴，防止 CSV injection。格式可直接再匯入（內建範本「do-accounts 格式」）。
 */
@Service
public class ExportService {

    public static final List<String> HEADER = List.of("日期", "類型", "項目", "大類", "子類", "帳戶", "金額", "幣別",
            "轉入帳戶", "轉入金額", "手續費", "標籤", "備註");
    private static final Map<String, String> KIND = Map.of(Transaction.EXPENSE, "支出", Transaction.INCOME, "收入", Transaction.TRANSFER, "轉帳");

    private final AccountService accounts;
    private final CategoryService categories;

    public ExportService(AccountService accounts, CategoryService categories) {
        this.accounts = accounts;
        this.categories = categories;
    }

    /** txs 依傳入順序寫出（呼叫端決定排序） */
    public void writeCsv(String userId, List<Transaction> txs, OutputStream out) throws IOException {
        Map<String, Account> acc = new HashMap<>();
        for (Account a : accounts.list(userId, true)) acc.put(a.id, a);
        Map<String, Category> cat = new HashMap<>();
        for (Category c : categories.list(userId)) cat.put(c.id, c);

        Writer w = new OutputStreamWriter(out, StandardCharsets.UTF_8);
        w.write('﻿');
        line(w, HEADER);
        for (Transaction t : txs) {
            Entry main = null, in = null, fee = null;
            for (Entry e : t.entries) {
                switch (e.role) {
                    case Transaction.ROLE_MAIN, Transaction.ROLE_TRANSFER_OUT -> main = e;
                    case Transaction.ROLE_TRANSFER_IN -> in = e;
                    case Transaction.ROLE_FEE -> fee = e;
                    default -> { }
                }
            }
            if (main == null || Transaction.INVESTMENT.equals(t.kind)) continue; // 投資交易另有匯出（/api/export/trades）
            Account from = acc.get(main.accountId);
            Account to = in == null ? null : acc.get(in.accountId);
            Category c = t.categoryId == null ? null : cat.get(t.categoryId);
            Category root = c == null ? null : c.parentId == null ? c : cat.get(c.parentId);
            BigDecimal amount = Transaction.TRANSFER.equals(t.kind) ? main.amount.negate() : main.amount; // 支出為負、收入與轉帳為正
            line(w, List.of(
                    t.date,
                    KIND.getOrDefault(t.kind, t.kind),
                    safe(t.title),
                    safe(root == null ? "" : root.name),
                    safe(c == null || c.parentId == null ? "" : c.name),
                    safe(from == null ? "" : from.name),
                    amount.toPlainString(),
                    from == null ? "" : from.currency,
                    safe(to == null ? "" : to.name),
                    in == null ? "" : in.amount.toPlainString(),
                    fee == null ? "" : fee.amount.negate().toPlainString(),
                    safe(t.tags == null ? "" : String.join(" ", t.tags)),
                    safe(t.note == null ? "" : t.note)));
        }
        w.flush();
    }

    public static final List<String> TRADE_HEADER = List.of("日期", "類型", "投資帳戶", "市場", "代號", "名稱", "股數", "價格",
            "手續費", "稅", "交割帳戶", "交割金額", "幣別", "備註");
    private static final Map<String, String> TRADE = Map.of("buy", "買進", "sell", "賣出", "cash_dividend", "現金股利",
            "stock_dividend", "股票股利", "split", "分割", "fee", "費用");

    /** 投資交易 CSV（SA FR-10.9）：股數、價格為證券幣別；交割金額為交割帳戶幣別（流入為正） */
    public void writeTradesCsv(String userId, List<Transaction> txs, Map<String, com.doaccounts.model.Security> secs, OutputStream out) throws IOException {
        Map<String, Account> acc = new HashMap<>();
        for (Account a : accounts.list(userId, true)) acc.put(a.id, a);
        Writer w = new OutputStreamWriter(out, StandardCharsets.UTF_8);
        w.write('\uFEFF');
        line(w, TRADE_HEADER);
        for (Transaction t : txs) {
            if (t.investment == null) continue;
            var iv = t.investment;
            var sec = secs.get(iv.securityId);
            Account holder = acc.get(iv.accountId);
            Entry cash = t.entries.isEmpty() ? null : t.entries.getFirst();
            Account settle = cash == null ? null : acc.get(cash.accountId);
            line(w, List.of(t.date, TRADE.getOrDefault(iv.tradeType, iv.tradeType), safe(holder == null ? "" : holder.name),
                    sec == null ? "" : sec.market, safe(sec == null ? "" : sec.symbol), safe(sec == null ? "" : sec.name),
                    plain(iv.quantity), plain(iv.price), plain(iv.fee), plain(iv.tax),
                    safe(settle == null ? "" : settle.name), cash == null ? "" : cash.amount.toPlainString(),
                    settle == null ? "" : settle.currency, safe(t.note == null ? "" : t.note)));
        }
        w.flush();
    }

    private static String plain(BigDecimal v) {
        return v == null ? "" : v.stripTrailingZeros().toPlainString();
    }

    private static void line(Writer w, List<String> cells) throws IOException {
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) w.write(',');
            w.write(quote(cells.get(i)));
        }
        w.write("\r\n");
    }

    /** 含逗號、引號、換行時用雙引號包起來，內部的 " 改成 "" */
    static String quote(String s) {
        if (s == null) return "";
        if (s.indexOf(',') < 0 && s.indexOf('"') < 0 && s.indexOf('\n') < 0 && s.indexOf('\r') < 0) return s;
        return '"' + s.replace("\"", "\"\"") + '"';
    }

    /** 文字欄位開頭是 = + - @（或 Tab、CR）時加 ' 前綴，避免 Excel 當成公式執行 */
    static String safe(String s) {
        if (s == null || s.isEmpty()) return "";
        char c = s.charAt(0);
        return c == '=' || c == '+' || c == '-' || c == '@' || c == '\t' || c == '\r' ? "'" + s : s;
    }
}
