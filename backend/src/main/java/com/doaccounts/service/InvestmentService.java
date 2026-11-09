package com.doaccounts.service;

import com.doaccounts.model.Account;
import com.doaccounts.model.Category;
import com.doaccounts.model.Price;
import com.doaccounts.model.Security;
import com.doaccounts.model.Transaction;
import com.doaccounts.model.Transaction.Entry;
import com.doaccounts.model.Transaction.Investment;
import com.doaccounts.model.User;
import com.doaccounts.repository.PriceRepository;
import com.doaccounts.repository.SecurityRepository;
import com.doaccounts.repository.TransactionRepository;
import com.doaccounts.service.HoldingCalculator.Position;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 投資（SA FR-9、SD 3.9、4.5）。一筆投資交易 = 一份 kind = investment 的 transactions 文件，
 * 投資明細在 investment，交割現金在 entries（買賣用 settlement、現金股利與費用用 main 以計入收支）。
 * 持倉不另存，每次由交易計算（ADR-09）。
 */
@Service
public class InvestmentService {

    static final int MAX_DECIMALS = 6;
    private static final Pattern CODE = Pattern.compile("^[0-9A-Za-z.\\-]{1,12}$");
    private static final Map<String, String> TYPE_LABEL = Map.of(Investment.BUY, "買進", Investment.SELL, "賣出",
            Investment.CASH_DIVIDEND, "現金股利", Investment.STOCK_DIVIDEND, "股票股利", Investment.SPLIT, "分割", Investment.FEE, "費用",
            Investment.OPENING, "期初持股");

    private final TransactionRepository txRepo;
    private final LedgerQueries ledger;
    private final AccountService accounts;
    private final CategoryService categories;
    private final SecurityRepository securities;
    private final PriceRepository prices;
    private final QuoteService quotes;
    private final FxService fx;

    public InvestmentService(TransactionRepository txRepo, LedgerQueries ledger, AccountService accounts, CategoryService categories,
                             SecurityRepository securities, PriceRepository prices, QuoteService quotes, FxService fx) {
        this.txRepo = txRepo;
        this.ledger = ledger;
        this.accounts = accounts;
        this.categories = categories;
        this.securities = securities;
        this.prices = prices;
        this.quotes = quotes;
        this.fx = fx;
    }

    // ---------- 證券 ----------

    public record SecurityRequest(String market, String symbol, String name, String currency, String assetType) {}

    /** 搜尋證券（代號開頭或名稱包含）；台股查不到時以代號查 TWSE／TPEx 清單並自動建立（FR-9.1） */
    public List<Security> searchSecurities(String q, String market) {
        String query = q == null ? "" : q.trim();
        List<Security> all = securities.findAll();
        String lower = query.toLowerCase(Locale.ROOT);
        List<Security> hits = all.stream()
                .filter(s -> market == null || market.isBlank() || market.equals(s.market))
                .filter(s -> lower.isEmpty() || s.symbol.toLowerCase(Locale.ROOT).startsWith(lower) || s.name.toLowerCase(Locale.ROOT).contains(lower))
                .sorted(Comparator.comparing((Security s) -> !s.symbol.equalsIgnoreCase(query)).thenComparing(s -> s.symbol))
                .limit(20).collect(Collectors.toCollection(ArrayList::new));
        boolean exact = hits.stream().anyMatch(s -> s.symbol.equalsIgnoreCase(query));
        if (!exact && CODE.matcher(query).matches()) {
            for (String m : List.of("TW", "TWO")) {
                if (market != null && !market.isBlank() && !market.equals(m)) continue;
                QuoteService.Quote quote = quotes.lookup(m, query.toUpperCase(Locale.ROOT));
                if (quote != null) {
                    hits.addFirst(createFromQuote(quote));
                    break;
                }
            }
        }
        return hits;
    }

    public Security createSecurity(SecurityRequest req) {
        String market = req.market() == null ? "" : req.market().trim();
        if (!Security.MARKETS.contains(market)) throw bad("市場需為 TW、TWO、US、FUND、CRYPTO 或 OTHER");
        String symbol = req.symbol() == null ? "" : req.symbol().trim().toUpperCase(Locale.ROOT);
        if (symbol.isEmpty() || symbol.length() > 20) throw bad("代號需為 1～20 字");
        var existing = securities.findFirstByMarketAndSymbol(market, symbol);
        if (existing.isPresent()) return existing.get();
        if (("TW".equals(market) || "TWO".equals(market))) {
            QuoteService.Quote q = quotes.lookup(market, symbol);
            if (q != null) return createFromQuote(q);
        }
        String name = req.name() == null ? "" : req.name().trim();
        if (name.isEmpty() || name.length() > 60) throw bad("名稱需為 1～60 字");
        String currency = req.currency() == null || req.currency().isBlank() ? defaultCurrency(market) : req.currency();
        if (!Currencies.supported(currency)) throw bad("不支援的幣別：" + currency);
        String type = req.assetType() == null || req.assetType().isBlank() ? "stock" : req.assetType();
        if (!Security.TYPES.contains(type)) throw bad("類型不正確");
        Security s = new Security();
        s.market = market;
        s.symbol = symbol;
        s.name = name;
        s.currency = currency;
        s.assetType = type;
        return securities.save(s);
    }

    private Security createFromQuote(QuoteService.Quote q) {
        return securities.findFirstByMarketAndSymbol(q.market(), q.symbol()).orElseGet(() -> {
            Security s = new Security();
            s.market = q.market();
            s.symbol = q.symbol();
            s.name = q.name();
            s.currency = "TWD";
            s.assetType = q.symbol().startsWith("00") ? "etf" : "stock"; // 台灣 ETF 代號以 00 開頭
            Security saved = securities.save(s);
            if (q.close() != null) savePrice(saved.id, q.date(), q.close(), "TW".equals(q.market()) ? Price.TWSE : Price.TPEX, null);
            return saved;
        });
    }

    private static String defaultCurrency(String market) {
        return switch (market) {
            case "TW", "TWO" -> "TWD";
            case "US" -> "USD";
            default -> "TWD";
        };
    }

    public Security security(String id) {
        return securities.findById(id == null ? "" : id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "找不到這檔證券"));
    }

    // ---------- 報價 ----------

    /** 手動報價（FR-9.5），只影響自己 */
    public Price setPrice(String userId, String securityId, String date, BigDecimal close) {
        security(securityId);
        Dates.check(date, "日期");
        if (close == null || close.signum() <= 0) throw bad("收盤價必須大於 0");
        if (close.stripTrailingZeros().scale() > MAX_DECIMALS) throw bad("收盤價最多 " + MAX_DECIMALS + " 位小數");
        return savePrice(securityId, date, close, Price.MANUAL, userId);
    }

    private Price savePrice(String securityId, String date, BigDecimal close, String source, String userId) {
        Price p = (userId == null ? prices.findFirstByUserIdIsNullAndSecurityIdAndDate(securityId, date)
                : prices.findFirstByUserIdAndSecurityIdAndDate(userId, securityId, date)).orElseGet(Price::new);
        p.securityId = securityId;
        p.date = date;
        p.close = close.stripTrailingZeros();
        p.source = source;
        p.userId = userId;
        p.updatedAt = Instant.now();
        return prices.save(p);
    }

    PriceTable priceTable(String userId, java.util.Collection<String> securityIds, String upTo) {
        if (securityIds.isEmpty()) return new PriceTable(List.of(), List.of());
        return new PriceTable(prices.findByUserIdIsNullAndSecurityIdInAndDateLessThanEqual(securityIds, upTo),
                prices.findByUserIdAndSecurityIdInAndDateLessThanEqual(userId, securityIds, upTo));
    }

    // ---------- 交易 ----------

    /**
     * @param quantity 股數；現金股利為參與配息的股數；分割為比例
     * @param price 成交價；現金股利為每股配息
     * @param settlementAmount 交割帳戶與證券幣別不同時，實際扣款或入帳的金額（交割帳戶幣別，正數）
     * @param principal 期初持股的投入本金（證券幣別）；省略時等於股數 × 均價
     */
    public record TradeRequest(String tradeType, String accountId, String securityId, String date, BigDecimal quantity,
                               BigDecimal price, BigDecimal fee, BigDecimal tax, String settlementAccountId,
                               BigDecimal settlementAmount, String note, Long version, BigDecimal principal) {}

    public Transaction create(String userId, TradeRequest req) {
        Transaction t = new Transaction();
        t.userId = userId;
        fill(userId, t, req);
        validateHistory(userId, t);
        return txRepo.save(t);
    }

    public Transaction update(String userId, String id, TradeRequest req) {
        Transaction t = trade(userId, id);
        if (req.version() == null || !req.version().equals(t.version)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "此筆紀錄已在其他地方被修改，請重新整理");
        }
        fill(userId, t, req);
        validateHistory(userId, t);
        return txRepo.save(t);
    }

    public void delete(String userId, String id) {
        Transaction t = trade(userId, id);
        List<Transaction> rest = ledger.investments(userId).stream().filter(x -> !x.id.equals(t.id)).toList();
        HoldingCalculator.compute(rest, null); // 刪掉買進後，之後的賣出可能變成超賣
        txRepo.delete(t);
    }

    public Transaction trade(String userId, String id) {
        Transaction t = txRepo.findById(id == null ? "" : id).filter(x -> userId.equals(x.userId) && Transaction.INVESTMENT.equals(x.kind))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "找不到這筆投資交易"));
        return t;
    }

    /** 以修改後的內容重算整段歷史，任何時間點超賣就拒絕（SD 4.5） */
    private void validateHistory(String userId, Transaction t) {
        List<Transaction> all = new ArrayList<>(ledger.investments(userId).stream().filter(x -> t.id == null || !x.id.equals(t.id)).toList());
        Transaction probe = copyForCalc(t);
        all.add(probe);
        HoldingCalculator.compute(all, null);
    }

    private static Transaction copyForCalc(Transaction t) {
        Transaction c = new Transaction();
        c.id = t.id;
        c.date = t.date;
        c.createdAt = t.createdAt == null ? Instant.now() : t.createdAt;
        c.investment = t.investment;
        return c;
    }

    private void fill(String userId, Transaction t, TradeRequest req) {
        String type = req.tradeType();
        if (!TYPE_LABEL.containsKey(type)) throw field("trade_type", "投資交易類型不正確");
        String date = on("date", () -> Dates.check(req.date(), "日期"));
        Account acc = on("account_id", () -> accounts.get(userId, req.accountId()));
        if (!"investment".equals(acc.kind)) throw field("account_id", "請選擇「投資」類型的帳戶");
        usable(acc, date, "account_id");
        Security sec = on("security_id", () -> security(req.securityId()));

        BigDecimal qty = req.quantity();
        BigDecimal price = req.price();
        BigDecimal fee = nz(req.fee());
        BigDecimal tax = nz(req.tax());
        check(fee, "fee", "手續費", false);
        check(tax, "tax", "稅", false);
        BigDecimal cash; // 證券幣別，流入為正；null 表示沒有現金
        BigDecimal principal = null;
        switch (type) {
            case Investment.OPENING -> {
                check(qty, "quantity", "股數", true);
                check(price, "price", "均價", false);
                if (req.principal() != null) {
                    check(req.principal(), "principal", "投入本金", false);
                    principal = req.principal().stripTrailingZeros();
                }
                fee = BigDecimal.ZERO;
                tax = BigDecimal.ZERO;
                cash = null;
            }
            case Investment.BUY, Investment.SELL -> {
                check(qty, "quantity", "股數", true);
                check(price, "price", "成交價", false);
                BigDecimal gross = qty.multiply(price);
                cash = Investment.BUY.equals(type) ? gross.add(fee).add(tax).negate() : gross.subtract(fee).subtract(tax);
            }
            case Investment.CASH_DIVIDEND -> {
                check(qty, "quantity", "配息股數", true);
                check(price, "price", "每股配息", true);
                cash = qty.multiply(price).subtract(fee).subtract(tax);
                if (cash.signum() <= 0) throw field("fee", "費用不能大於股利");
                price = price.stripTrailingZeros();
            }
            case Investment.STOCK_DIVIDEND -> {
                check(qty, "quantity", "配發股數", true);
                price = null;
                fee = BigDecimal.ZERO;
                tax = BigDecimal.ZERO;
                cash = null;
            }
            case Investment.SPLIT -> {
                check(qty, "quantity", "分割比例", true);
                price = null;
                fee = BigDecimal.ZERO;
                tax = BigDecimal.ZERO;
                cash = null;
            }
            default -> { // fee
                if (fee.signum() <= 0) throw field("fee", "費用必須大於 0");
                qty = null;
                price = null;
                tax = BigDecimal.ZERO;
                cash = fee.negate();
            }
        }

        List<Entry> entries = new ArrayList<>();
        String categoryId = null;
        if (cash != null) {
            String sid = req.settlementAccountId() == null || req.settlementAccountId().isBlank() ? acc.id : req.settlementAccountId();
            Account settle = on("settlement_account_id", () -> accounts.get(userId, sid));
            usable(settle, date, "settlement_account_id");
            BigDecimal amount;
            if (settle.currency.equals(sec.currency)) {
                amount = cash.setScale(Currencies.digits(settle.currency), RoundingMode.HALF_UP);
            } else {
                if (req.settlementAmount() == null) {
                    throw field("settlement_amount", "交割帳戶是 " + settle.currency + "，請填寫實際" + (cash.signum() < 0 ? "扣款" : "入帳") + "金額");
                }
                BigDecimal abs = on("settlement_amount", () -> Currencies.check(req.settlementAmount(), settle.currency, true, "交割金額"));
                amount = cash.signum() < 0 ? abs.negate() : abs;
            }
            boolean pnl = Investment.CASH_DIVIDEND.equals(type) || Investment.FEE.equals(type);
            if (pnl) {
                categoryId = Investment.CASH_DIVIDEND.equals(type)
                        ? categories.system(userId, Category.INCOME, Category.SYS_INVESTMENT_PNL).id
                        : categories.system(userId, Category.EXPENSE, Category.SYS_FEE).id;
            }
            entries.add(new Entry(settle.id, amount, pnl ? Transaction.ROLE_MAIN : Transaction.ROLE_SETTLEMENT, null));
        }

        Investment iv = new Investment();
        iv.accountId = acc.id;
        iv.securityId = sec.id;
        iv.tradeType = type;
        iv.quantity = qty == null ? null : qty.stripTrailingZeros();
        iv.price = price == null ? null : price.stripTrailingZeros();
        iv.fee = fee.stripTrailingZeros();
        iv.tax = tax.stripTrailingZeros();
        iv.principal = principal;

        String note = req.note() == null ? "" : req.note().trim();
        if (note.length() > 500) throw field("note", "備註最多 500 字");
        t.kind = Transaction.INVESTMENT;
        t.date = date;
        t.title = TYPE_LABEL.get(type) + " " + sec.symbol + " " + sec.name;
        if (t.title.length() > 100) t.title = t.title.substring(0, 100);
        t.note = note;
        t.categoryId = categoryId;
        t.entries = entries;
        t.investment = iv;
    }

    private static void usable(Account a, String date, String field) {
        if (a.archivedAt != null) throw field(field, "帳戶「" + a.name + "」已封存，不能記帳");
        if (a.openingDate != null && date.compareTo(a.openingDate) < 0) {
            throw field(field, "交易日期不能早於帳戶「" + a.name + "」的開戶日期（" + a.openingDate + "）");
        }
    }

    private static void check(BigDecimal v, String field, String label, boolean positive) {
        if (v == null) {
            if (positive) throw field(field, label + "必填");
            throw field(field, label + "必填");
        }
        if (positive ? v.signum() <= 0 : v.signum() < 0) throw field(field, label + (positive ? "必須大於 0" : "不能是負數"));
        if (v.stripTrailingZeros().scale() > MAX_DECIMALS) throw field(field, label + "最多 " + MAX_DECIMALS + " 位小數");
        if (v.compareTo(new BigDecimal("1000000000000")) >= 0) throw field(field, label + "太大");
    }

    // ---------- 查詢 ----------

    public record TradeRow(Transaction transaction, Security security, BigDecimal realized) {}

    public record TradePage(List<TradeRow> items, int page, int size, int total) {}

    public TradePage trades(String userId, String accountId, String securityId, String from, String to, int page, int size) {
        // 匯出時 size 可以是整數上限；API 由 controller 限制在 200 以內
        if (size < 1) throw bad("每頁筆數需為 1～200");
        List<Transaction> all = ledger.investments(userId);
        Map<String, BigDecimal> realized = HoldingCalculator.compute(all, null).realizedByTrade();
        List<Transaction> list = new ArrayList<>(all.stream()
                .filter(t -> accountId == null || accountId.equals(t.investment.accountId))
                .filter(t -> securityId == null || securityId.equals(t.investment.securityId))
                .filter(t -> from == null || t.date.compareTo(from) >= 0)
                .filter(t -> to == null || t.date.compareTo(to) <= 0)
                .toList()).reversed();
        Map<String, Security> secs = securityMap(list);
        int fromIdx = Math.min(page * size, list.size());
        List<TradeRow> items = list.subList(fromIdx, Math.min(fromIdx + size, list.size())).stream()
                .map(t -> {
                    Security sec = secs.get(t.investment.securityId);
                    BigDecimal r = realized.get(t.id);
                    return new TradeRow(t, sec, r == null || sec == null ? r : money(r, sec.currency));
                }).toList();
        return new TradePage(items, page, size, list.size());
    }

    /**
     * 一檔持股。金額為證券幣別，*_base 為本位幣（最新匯率）。price 為 null 表示還沒有任何報價，市值以成本計。
     */
    public record Holding(String accountId, Security security, BigDecimal quantity, BigDecimal avgCost, BigDecimal cost,
                          BigDecimal price, String priceDate, String priceSource, boolean manualPrice,
                          BigDecimal marketValue, BigDecimal unrealized, BigDecimal realized, BigDecimal dividends,
                          BigDecimal returnPct, BigDecimal marketValueBase, BigDecimal unrealizedBase) {}

    public List<Holding> holdings(User user, String accountId) {
        Snapshot s = snapshot(user, Dates.today().toString());
        return s.holdings.stream().filter(h -> accountId == null || accountId.equals(h.accountId())).toList();
    }

    public record Slice(String key, BigDecimal value, BigDecimal percentage) {}

    public record Summary(String baseCurrency, BigDecimal marketValue, BigDecimal cost, BigDecimal unrealized, BigDecimal unrealizedPct,
                          BigDecimal realizedThisYear, int sellsThisYear, BigDecimal realizedTotal, BigDecimal dividendsTotal,
                          BigDecimal dividendsThisYear, int dividendCountThisYear, BigDecimal returnPct, String oldestPriceDate,
                          Map<String, List<Slice>> allocation) {}

    /** 總市值、損益、股利與資產配置（本位幣，FR-9.6、9.8） */
    public Summary summary(User user, String accountId) {
        String base = FxService.baseOf(user);
        String today = Dates.today().toString();
        String year = today.substring(0, 4);
        Snapshot s = snapshot(user, today);
        RateTable rates = s.rates;
        BigDecimal mv = BigDecimal.ZERO, cost = BigDecimal.ZERO, realizedYear = BigDecimal.ZERO, realizedTotal = BigDecimal.ZERO;
        BigDecimal divTotal = BigDecimal.ZERO, divYear = BigDecimal.ZERO, invested = BigDecimal.ZERO, priorGain = BigDecimal.ZERO;
        int divCount = 0;
        String oldest = null;
        Map<String, Map<String, BigDecimal>> alloc = new LinkedHashMap<>();
        for (String k : List.of("market", "asset_type", "currency")) alloc.put(k, new LinkedHashMap<>());
        for (Position p : s.positions.values()) {
            if (accountId != null && !accountId.equals(p.accountId)) continue;
            Security sec = s.securities.get(p.securityId);
            String cur = sec == null ? base : sec.currency;
            realizedTotal = realizedTotal.add(rates.toBase(p.realized, cur, today));
            realizedYear = realizedYear.add(rates.toBase(p.realizedByYear.getOrDefault(year, BigDecimal.ZERO), cur, today));
            divTotal = divTotal.add(rates.toBase(p.dividends, cur, today));
            divYear = divYear.add(rates.toBase(p.dividendsByYear.getOrDefault(year, BigDecimal.ZERO), cur, today));
            divCount += p.dividendCountByYear.getOrDefault(year, 0);
            invested = invested.add(rates.toBase(p.invested, cur, today));
            priorGain = priorGain.add(rates.toBase(p.priorGain, cur, today));
        }
        for (Holding h : s.holdings) {
            if (accountId != null && !accountId.equals(h.accountId())) continue;
            mv = mv.add(h.marketValueBase());
            cost = cost.add(h.marketValueBase().subtract(h.unrealizedBase()));
            if (h.priceDate() != null && (oldest == null || h.priceDate().compareTo(oldest) < 0)) oldest = h.priceDate();
            alloc.get("market").merge(h.security().market, h.marketValueBase(), BigDecimal::add);
            alloc.get("asset_type").merge(h.security().assetType, h.marketValueBase(), BigDecimal::add);
            alloc.get("currency").merge(h.security().currency, h.marketValueBase(), BigDecimal::add);
        }
        int sells = (int) ledger.investments(user.id).stream()
                .filter(t -> Investment.SELL.equals(t.investment.tradeType) && t.date.startsWith(year)
                        && (accountId == null || accountId.equals(t.investment.accountId))).count();
        BigDecimal unrealized = mv.subtract(cost);
        Map<String, List<Slice>> allocation = new LinkedHashMap<>();
        BigDecimal total = mv;
        alloc.forEach((k, m) -> allocation.put(k, m.entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                .map(e -> new Slice(e.getKey(), Currencies.round(e.getValue(), base), pct(e.getValue(), total))).toList()));
        BigDecimal ret = invested.signum() == 0 ? BigDecimal.ZERO : pct(unrealized.add(realizedTotal).add(divTotal).add(priorGain), invested);
        return new Summary(base, Currencies.round(mv, base), Currencies.round(cost, base), Currencies.round(unrealized, base),
                pct(unrealized, cost), Currencies.round(realizedYear, base), sells, Currencies.round(realizedTotal, base),
                Currencies.round(divTotal, base), Currencies.round(divYear, base), divCount, ret, oldest, allocation);
    }

    /** 某天的投資總市值（本位幣，給淨資產用，FR-9.7）；accountIds 為 null 表示全部帳戶 */
    public Map<String, BigDecimal> marketValues(User user, List<String> dates, RateTable rates, Set<String> accountIds) {
        Map<String, BigDecimal> out = new LinkedHashMap<>();
        if (dates.isEmpty()) return out;
        List<Transaction> txs = ledger.investments(user.id);
        if (txs.isEmpty()) {
            dates.forEach(d -> out.put(d, BigDecimal.ZERO));
            return out;
        }
        String last = dates.stream().max(String::compareTo).orElseThrow();
        Map<String, Security> secs = securityMap(txs);
        PriceTable pt = priceTable(user.id, secs.keySet(), last);
        for (String d : dates) {
            BigDecimal sum = BigDecimal.ZERO;
            for (Position p : HoldingCalculator.compute(txs, d).positions().values()) {
                if (p.held.signum() == 0 || (accountIds != null && !accountIds.contains(p.accountId))) continue;
                Security sec = secs.get(p.securityId);
                Price px = pt.at(p.securityId, d);
                BigDecimal value = px == null ? p.cost : p.held.multiply(px.close);
                sum = sum.add(rates.toBase(value, sec == null ? FxService.baseOf(user) : sec.currency, d));
            }
            out.put(d, sum);
        }
        return out;
    }

    private record Snapshot(Map<String, Position> positions, Map<String, Security> securities, List<Holding> holdings, RateTable rates) {}

    private Snapshot snapshot(User user, String date) {
        List<Transaction> txs = ledger.investments(user.id);
        Map<String, Position> positions = HoldingCalculator.compute(txs, date).positions();
        Map<String, Security> secs = securityMap(txs);
        PriceTable pt = priceTable(user.id, secs.keySet(), date);
        RateTable rates = fx.table(user, date);
        List<Holding> list = new ArrayList<>();
        for (Position p : positions.values()) {
            if (p.held.signum() == 0) continue;
            Security sec = secs.get(p.securityId);
            if (sec == null) continue;
            Price px = pt.at(p.securityId, date);
            BigDecimal mv = px == null ? p.cost : p.held.multiply(px.close);
            BigDecimal unrealized = mv.subtract(p.cost);
            // 報酬率 =（未實現＋已實現＋股利＋期初持股開始記帳前的損益）÷ 累計投入
            BigDecimal ret = p.invested.signum() == 0 ? BigDecimal.ZERO
                    : pct(unrealized.add(p.realized).add(p.dividends).add(p.priorGain), p.invested);
            String base = FxService.baseOf(user);
            BigDecimal mvBase = Currencies.round(rates.toBase(mv, sec.currency, date), base);
            list.add(new Holding(p.accountId, sec, p.held, p.avg().setScale(4, RoundingMode.HALF_UP), money(p.cost, sec.currency),
                    px == null ? null : px.close, px == null ? null : px.date, px == null ? null : px.source, px != null && px.userId != null,
                    money(mv, sec.currency), money(unrealized, sec.currency), money(p.realized, sec.currency), money(p.dividends, sec.currency),
                    ret, mvBase, Currencies.round(rates.toBase(unrealized, sec.currency, date), base)));
        }
        list.sort(Comparator.comparing(Holding::marketValueBase).reversed());
        return new Snapshot(positions, secs, list, rates);
    }

    private Map<String, Security> securityMap(List<Transaction> txs) {
        Set<String> ids = txs.stream().filter(t -> t.investment != null).map(t -> t.investment.securityId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<String, Security> out = new HashMap<>();
        for (Security s : securities.findAllById(ids)) out.put(s.id, s);
        return out;
    }

    private static BigDecimal money(BigDecimal v, String currency) {
        return v.setScale(Currencies.digits(currency), RoundingMode.HALF_UP);
    }

    private static BigDecimal pct(BigDecimal v, BigDecimal total) {
        return total.signum() == 0 ? BigDecimal.ZERO : v.multiply(BigDecimal.valueOf(100)).divide(total.abs(), 1, RoundingMode.HALF_UP);
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static <T> T on(String field, java.util.function.Supplier<T> s) {
        try {
            return s.get();
        } catch (ResponseStatusException e) {
            throw FieldException.of(field, e);
        }
    }

    private static FieldException field(String field, String message) {
        return new FieldException(HttpStatus.UNPROCESSABLE_CONTENT, field, message);
    }

    private static ResponseStatusException bad(String m) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, m);
    }
}
