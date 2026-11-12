package com.doaccounts.service;

import com.doaccounts.model.Account;
import com.doaccounts.model.Budget;
import com.doaccounts.model.ExchangeRate;
import com.doaccounts.model.ImportBatch;
import com.doaccounts.model.ImportTemplate;
import com.doaccounts.model.Price;
import com.doaccounts.model.Security;
import com.doaccounts.model.User;
import com.doaccounts.model.Candle;
import com.doaccounts.model.CandleMonth;
import com.doaccounts.model.Category;
import com.doaccounts.model.IntradayQuote;
import com.doaccounts.model.IntradayStatus;
import com.doaccounts.model.Transaction;
import com.doaccounts.repository.AccountRepository;
import com.doaccounts.repository.BudgetRepository;
import com.doaccounts.repository.ExchangeRateRepository;
import com.doaccounts.repository.ImportBatchRepository;
import com.doaccounts.repository.ImportTemplateRepository;
import com.doaccounts.repository.UserRepository;
import com.doaccounts.repository.PriceRepository;
import com.doaccounts.repository.CandleRepository;
import com.doaccounts.repository.IntradayQuoteRepository;
import com.doaccounts.repository.IntradayStatusRepository;
import com.doaccounts.repository.CandleMonthRepository;
import com.doaccounts.repository.SecurityRepository;
import com.doaccounts.repository.CategoryRepository;
import com.doaccounts.repository.TransactionRepository;
import com.doaccounts.service.AccountService.AccountRequest;
import com.doaccounts.service.TransactionService.TransactionRequest;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 測試用的記憶體資料：repository 與 LedgerQueries 都用 mock 接到 List 上，不需要 MongoDB。
 * LedgerQueries 的查詢以簡化版邏輯模擬（依 user、帳戶、日期篩選）。
 */
class Fixture {

    static final String ME = "u1";
    static final String OTHER = "u2";

    final List<Account> accounts = new ArrayList<>();
    final List<Category> categories = new ArrayList<>();
    final List<Transaction> transactions = new ArrayList<>();
    final List<Budget> budgets = new ArrayList<>();
    final List<ExchangeRate> rates = new ArrayList<>();
    final List<ImportBatch> importBatches = new ArrayList<>();
    final List<ImportTemplate> templates = new ArrayList<>();
    final List<User> users = new ArrayList<>();
    final List<Security> securities = new ArrayList<>();
    final List<Price> prices = new ArrayList<>();
    final List<Candle> candles = new ArrayList<>();
    final List<IntradayQuote> intradayQuotes = new ArrayList<>();
    final List<IntradayStatus> intradayStatuses = new ArrayList<>();
    /** 假的盤中價來源（null 表示連不到）與時鐘 */
    Map<?, ?> misFeed;
    java.time.ZonedDateTime now = java.time.ZonedDateTime.now(Dates.ZONE);
    final List<CandleMonth> candleMonths = new ArrayList<>();
    /** 假的 K 線資料來源：網址 → 回應 */
    final Map<String, Map<?, ?>> candleFeed = new HashMap<>();
    final List<String> candleRequests = new ArrayList<>();
    /** 假的台股清單（QuoteService 的資料來源） */
    final Map<String, List<Map<String, Object>>> quoteFeed = new HashMap<>();

    final AccountRepository accountRepo = mock(AccountRepository.class);
    final CategoryRepository categoryRepo = mock(CategoryRepository.class);
    final TransactionRepository txRepo = mock(TransactionRepository.class);
    final LedgerQueries ledger = mock(LedgerQueries.class);
    final BudgetRepository budgetRepo = mock(BudgetRepository.class);
    final ExchangeRateRepository fxRepo = mock(ExchangeRateRepository.class);
    final ImportBatchRepository batchRepo = mock(ImportBatchRepository.class);
    final ImportTemplateRepository templateRepo = mock(ImportTemplateRepository.class);
    final UserRepository userRepo = mock(UserRepository.class);
    final SecurityRepository securityRepo = mock(SecurityRepository.class);
    final PriceRepository priceRepo = mock(PriceRepository.class);
    final CandleRepository candleRepo = mock(CandleRepository.class);
    final CandleMonthRepository candleMonthRepo = mock(CandleMonthRepository.class);
    final IntradayQuoteRepository intradayRepo = mock(IntradayQuoteRepository.class);
    final IntradayStatusRepository intradayStatusRepo = mock(IntradayStatusRepository.class);
    final IntradayService intradayService = new IntradayService(intradayRepo, intradayStatusRepo, priceRepo, url -> {
        if (misFeed == null) throw new IllegalStateException("連不到");
        return misFeed;
    }, () -> now);
    final CandleService candleService = new CandleService(candleRepo, candleMonthRepo, url -> {
        candleRequests.add(url);
        Map<?, ?> body = candleFeed.get(url);
        if (body == null) throw new IllegalStateException("連不到 " + url);
        return body;
    }, ms -> { });

    final AccountService accountService = new AccountService(accountRepo, ledger);
    final CategoryService categoryService = new CategoryService(categoryRepo, ledger);
    final TransactionService txService = new TransactionService(txRepo, ledger, accountService, categoryService);
    final TransactionBatchService batchService = new TransactionBatchService(txRepo, ledger, txService);
    final FxService fxService = new FxService(fxRepo);
    final BudgetService budgetService = new BudgetService(budgetRepo, categoryService, accountService, ledger, fxService);
    final QuoteService quoteService = new QuoteService(securityRepo, priceRepo, url -> {
        List<Map<String, Object>> rows = quoteFeed.get(url);
        if (rows == null) throw new IllegalStateException("連不到 " + url);
        return rows;
    });
    final InvestmentService investmentService = new InvestmentService(txRepo, ledger, accountService, categoryService, securityRepo, priceRepo, quoteService, fxService, candleService, intradayService);
    final ReportService reportService = new ReportService(accountService, categoryService, ledger, fxService, budgetService, investmentService);
    final DashboardService dashboardService = new DashboardService(accountService, ledger, fxService, budgetService, investmentService);

    final ImportService importService = new ImportService(txRepo, ledger, txService, accountService, categoryService, batchRepo, templateRepo);
    final ExportService exportService = new ExportService(accountService, categoryService);
    final BackupService backupService = new BackupService(userRepo, accountRepo, categoryRepo, txRepo, budgetRepo, fxRepo, templateRepo, ledger, categoryService, securityRepo);

    /** 本位幣 TWD 的使用者 */
    final User me = user(ME);

    {
        users.add(me);
    }

    private int seq;

    Fixture() {
        when(accountRepo.save(any(Account.class))).thenAnswer(inv -> store(accounts, inv.getArgument(0), "a"));
        when(accountRepo.findById(anyString())).thenAnswer(inv -> accounts.stream().filter(a -> a.id.equals(inv.getArgument(0))).findFirst());
        when(accountRepo.findByUserId(anyString())).thenAnswer(inv -> accounts.stream().filter(a -> a.userId.equals(inv.getArgument(0))).toList());
        doAnswer(inv -> accounts.remove((Account) inv.getArgument(0))).when(accountRepo).delete(any(Account.class));

        when(categoryRepo.save(any(Category.class))).thenAnswer(inv -> store(categories, inv.getArgument(0), "c"));
        when(categoryRepo.findById(anyString())).thenAnswer(inv -> categories.stream().filter(c -> c.id.equals(inv.getArgument(0))).findFirst());
        when(categoryRepo.findByUserId(anyString())).thenAnswer(inv -> categories.stream().filter(c -> c.userId.equals(inv.getArgument(0))).toList());
        when(categoryRepo.countByUserId(anyString())).thenAnswer(inv -> categories.stream().filter(c -> c.userId.equals(inv.getArgument(0))).count());
        doAnswer(inv -> categories.remove((Category) inv.getArgument(0))).when(categoryRepo).delete(any(Category.class));
        doAnswer(inv -> { categories.removeAll((Collection<?>) inv.getArgument(0)); return null; }).when(categoryRepo).deleteAll(anyCollection());

        when(txRepo.save(any(Transaction.class))).thenAnswer(inv -> {
            Transaction t = inv.getArgument(0);
            t.version = t.version == null ? 0L : t.version + 1;
            if (t.createdAt == null) t.createdAt = java.time.Instant.now().plusNanos(++clock); // 模擬 @CreatedDate，同一天的交易依建立順序
            if (t.id != null) transactions.replaceAll(x -> x.id.equals(t.id) ? t : x); // 同 id 的新物件取代舊的（像資料庫一樣）
            return store(transactions, t, "t");
        });
        when(txRepo.findById(anyString())).thenAnswer(inv -> transactions.stream().filter(t -> t.id.equals(inv.getArgument(0))).findFirst());
        doAnswer(inv -> transactions.remove((Transaction) inv.getArgument(0))).when(txRepo).delete(any(Transaction.class));
        doAnswer(inv -> { transactions.removeAll((Collection<?>) inv.getArgument(0)); return null; }).when(txRepo).deleteAll(anyCollection());

        when(ledger.sumByAccount(anyString())).thenAnswer(inv -> {
            Map<String, BigDecimal> m = new HashMap<>();
            mine(inv.getArgument(0)).forEach(t -> t.entries.forEach(e -> m.merge(e.accountId, e.amount, BigDecimal::add)));
            return m;
        });
        when(ledger.hasTransactions(anyString(), anyString())).thenAnswer(inv -> !byAccount(inv.getArgument(0), inv.getArgument(1)).isEmpty()
                || mine(inv.getArgument(0)).stream().anyMatch(t -> t.investment != null && inv.getArgument(1).equals(t.investment.accountId)));
        when(ledger.investments(anyString())).thenAnswer(inv -> mine(inv.getArgument(0)).stream().filter(t -> Transaction.INVESTMENT.equals(t.kind))
                .sorted((a, b) -> a.date.compareTo(b.date)).toList());
        when(securityRepo.save(any(Security.class))).thenAnswer(inv -> store(securities, inv.getArgument(0), "s"));
        when(securityRepo.findAll()).thenAnswer(inv -> List.copyOf(securities));
        when(securityRepo.findById(anyString())).thenAnswer(inv -> securities.stream().filter(x -> x.id.equals(inv.getArgument(0))).findFirst());
        when(securityRepo.findAllById(any())).thenAnswer(inv -> {
            Iterable<?> ids = inv.getArgument(0);
            List<Object> l = new ArrayList<>();
            ids.forEach(l::add);
            return securities.stream().filter(x -> l.contains(x.id)).toList();
        });
        when(securityRepo.findFirstByMarketAndSymbol(anyString(), anyString())).thenAnswer(inv -> securities.stream()
                .filter(x -> x.market.equals(inv.getArgument(0)) && x.symbol.equals(inv.getArgument(1))).findFirst());
        when(intradayRepo.save(any(IntradayQuote.class))).thenAnswer(inv -> store(intradayQuotes, inv.getArgument(0), "iq"));
        when(intradayRepo.findFirstBySecurityIdAndDate(anyString(), anyString())).thenAnswer(inv -> intradayQuotes.stream()
                .filter(q -> q.securityId.equals(inv.getArgument(0)) && q.date.equals(inv.getArgument(1))).findFirst());
        when(intradayRepo.findBySecurityIdInAndDate(anyCollection(), anyString())).thenAnswer(inv -> intradayQuotes.stream()
                .filter(q -> ((Collection<?>) inv.getArgument(0)).contains(q.securityId) && q.date.equals(inv.getArgument(1))).toList());
        when(intradayStatusRepo.save(any(IntradayStatus.class))).thenAnswer(inv -> {
            IntradayStatus st = inv.getArgument(0);
            intradayStatuses.removeIf(x -> x.id.equals(st.id));
            intradayStatuses.add(st);
            return st;
        });
        when(intradayStatusRepo.findById(anyString())).thenAnswer(inv -> intradayStatuses.stream().filter(x -> x.id.equals(inv.getArgument(0))).findFirst());
        when(candleRepo.save(any(Candle.class))).thenAnswer(inv -> store(candles, inv.getArgument(0), "k"));
        when(candleRepo.findFirstBySecurityIdAndDate(anyString(), anyString())).thenAnswer(inv -> candles.stream()
                .filter(c -> c.securityId.equals(inv.getArgument(0)) && c.date.equals(inv.getArgument(1))).findFirst());
        when(candleRepo.findBySecurityIdAndDateGreaterThanEqualOrderByDateAsc(anyString(), anyString())).thenAnswer(inv -> candles.stream()
                .filter(c -> c.securityId.equals(inv.getArgument(0)) && c.date.compareTo(inv.getArgument(1)) >= 0)
                .sorted((a, b) -> a.date.compareTo(b.date)).toList());
        when(candleMonthRepo.save(any(CandleMonth.class))).thenAnswer(inv -> store(candleMonths, inv.getArgument(0), "km"));
        when(candleMonthRepo.findFirstBySecurityIdAndMonth(anyString(), anyString())).thenAnswer(inv -> candleMonths.stream()
                .filter(c -> c.securityId.equals(inv.getArgument(0)) && c.month.equals(inv.getArgument(1))).findFirst());
        when(priceRepo.save(any(Price.class))).thenAnswer(inv -> store(prices, inv.getArgument(0), "p"));
        when(priceRepo.findByUserIdIsNullAndSecurityIdInAndDateLessThanEqual(anyCollection(), anyString())).thenAnswer(inv -> prices.stream()
                .filter(x -> x.userId == null && ((Collection<?>) inv.getArgument(0)).contains(x.securityId) && x.date.compareTo(inv.getArgument(1)) <= 0).toList());
        when(priceRepo.findByUserIdAndSecurityIdInAndDateLessThanEqual(anyString(), anyCollection(), anyString())).thenAnswer(inv -> prices.stream()
                .filter(x -> inv.getArgument(0).equals(x.userId) && ((Collection<?>) inv.getArgument(1)).contains(x.securityId) && x.date.compareTo(inv.getArgument(2)) <= 0).toList());
        when(priceRepo.findFirstByUserIdIsNullAndSecurityIdAndDate(anyString(), anyString())).thenAnswer(inv -> prices.stream()
                .filter(x -> x.userId == null && x.securityId.equals(inv.getArgument(0)) && x.date.equals(inv.getArgument(1))).findFirst());
        when(priceRepo.findFirstByUserIdAndSecurityIdAndDate(anyString(), anyString(), anyString())).thenAnswer(inv -> prices.stream()
                .filter(x -> inv.getArgument(0).equals(x.userId) && x.securityId.equals(inv.getArgument(1)) && x.date.equals(inv.getArgument(2))).findFirst());
        when(ledger.earliestDate(anyString(), anyString())).thenAnswer(inv -> byAccount(inv.getArgument(0), inv.getArgument(1)).stream().map(t -> t.date).findFirst());
        when(ledger.byAccount(anyString(), anyString())).thenAnswer(inv -> byAccount(inv.getArgument(0), inv.getArgument(1)));
        when(ledger.findByIds(anyString(), anyCollection())).thenAnswer(inv -> mine(inv.getArgument(0)).stream().filter(t -> ((Collection<?>) inv.getArgument(1)).contains(t.id)).toList());
        when(ledger.countUsingCategories(anyString(), anyCollection())).thenAnswer(inv -> mine(inv.getArgument(0)).stream()
                .filter(t -> ((Collection<?>) inv.getArgument(1)).contains(t.categoryId)).count());
        doAnswer(inv -> {
            Collection<?> from = inv.getArgument(1);
            mine(inv.getArgument(0)).stream().filter(t -> from.contains(t.categoryId)).forEach(t -> t.categoryId = inv.getArgument(2));
            return null;
        }).when(ledger).moveCategories(anyString(), anyCollection(), anyString());
        when(ledger.search(anyString(), any())).thenAnswer(inv -> {
            LedgerQueries.Filter f = inv.getArgument(1);
            return newestFirst(mine(inv.getArgument(0))).stream()
                    .filter(t -> f.from() == null || t.date.compareTo(f.from()) >= 0)
                    .filter(t -> f.to() == null || t.date.compareTo(f.to()) <= 0)
                    .filter(t -> f.kinds() == null || f.kinds().isEmpty() || f.kinds().contains(t.kind))
                    .filter(t -> f.accountIds() == null || f.accountIds().isEmpty() || t.entries.stream().anyMatch(e -> f.accountIds().contains(e.accountId)))
                    .filter(t -> f.categoryIds() == null || f.categoryIds().isEmpty() || f.categoryIds().contains(t.categoryId))
                    .toList();
        });
        doAnswer(inv -> {
            Transaction o = inv.getArgument(0);
            transactions.removeIf(t -> t.id.equals(o.id));
            transactions.add(o);
            return null;
        }).when(ledger).restore(any(Transaction.class));
        doAnswer(inv -> {
            Collection<?> ids = inv.getArgument(1);
            transactions.removeIf(t -> t.userId.equals(inv.getArgument(0)) && ids.contains(t.id));
            return null;
        }).when(ledger).removeByIds(anyString(), anyCollection());
        when(ledger.recent(anyString(), anyInt())).thenAnswer(inv -> newestFirst(mine(inv.getArgument(0))).stream().limit((int) inv.getArgument(1)).toList());
        when(txRepo.saveAll(anyList())).thenAnswer(inv -> {
            List<Transaction> list = inv.getArgument(0);
            if (failSaveAll) throw new IllegalStateException("模擬寫入失敗");
            list.forEach(t -> txRepo.save(t));
            return list;
        });
        when(accountRepo.saveAll(anyList())).thenAnswer(inv -> { List<Account> l = inv.getArgument(0); l.forEach(this::upsertAccount); return l; });
        doAnswer(inv -> { accounts.removeAll(List.copyOf((Collection<?>) inv.getArgument(0))); return null; }).when(accountRepo).deleteAll(anyCollection());
        when(categoryRepo.saveAll(anyList())).thenAnswer(inv -> { List<Category> l = inv.getArgument(0); l.forEach(c -> { categories.removeIf(x -> x.id.equals(c.id)); categories.add(c); }); return l; });
        when(budgetRepo.findByUserId(anyString())).thenAnswer(inv -> budgets.stream().filter(b -> b.userId.equals(inv.getArgument(0))).toList());
        when(budgetRepo.saveAll(anyList())).thenAnswer(inv -> { List<Budget> l = inv.getArgument(0); l.forEach(b -> store(budgets, b, "b")); return l; });
        doAnswer(inv -> { budgets.removeAll(List.copyOf((Collection<?>) inv.getArgument(0))); return null; }).when(budgetRepo).deleteAll(anyCollection());
        when(fxRepo.saveAll(anyList())).thenAnswer(inv -> { List<ExchangeRate> l = inv.getArgument(0); l.forEach(r -> store(rates, r, "r")); return l; });
        doAnswer(inv -> { rates.removeAll(List.copyOf((Collection<?>) inv.getArgument(0))); return null; }).when(fxRepo).deleteAll(anyCollection());
        when(batchRepo.save(any(ImportBatch.class))).thenAnswer(inv -> store(importBatches, inv.getArgument(0), "ib"));
        when(batchRepo.findById(anyString())).thenAnswer(inv -> importBatches.stream().filter(b -> b.id.equals(inv.getArgument(0))).findFirst());
        when(batchRepo.findTop20ByUserIdOrderByCreatedAtDesc(anyString())).thenAnswer(inv -> importBatches.stream().filter(b -> b.userId.equals(inv.getArgument(0))).toList());
        when(templateRepo.save(any(ImportTemplate.class))).thenAnswer(inv -> store(templates, inv.getArgument(0), "tp"));
        when(templateRepo.findById(anyString())).thenAnswer(inv -> templates.stream().filter(t -> t.id.equals(inv.getArgument(0))).findFirst());
        when(templateRepo.findByUserIdOrderByNameAsc(anyString())).thenAnswer(inv -> templates.stream().filter(t -> t.userId.equals(inv.getArgument(0))).toList());
        when(templateRepo.saveAll(anyList())).thenAnswer(inv -> { List<ImportTemplate> l = inv.getArgument(0); l.forEach(t -> store(templates, t, "tp")); return l; });
        doAnswer(inv -> templates.remove((ImportTemplate) inv.getArgument(0))).when(templateRepo).delete(any(ImportTemplate.class));
        doAnswer(inv -> { templates.removeAll(List.copyOf((Collection<?>) inv.getArgument(0))); return null; }).when(templateRepo).deleteAll(anyCollection());
        when(userRepo.findById(anyString())).thenAnswer(inv -> users.stream().filter(u -> u.id.equals(inv.getArgument(0))).findFirst());
        when(userRepo.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(ledger.removeByBatch(anyString(), anyString())).thenAnswer(inv -> {
            long before = transactions.size();
            transactions.removeIf(t -> t.userId.equals(inv.getArgument(0)) && inv.getArgument(1).equals(t.importBatchId));
            return before - transactions.size();
        });
        when(ledger.hasAny(anyString())).thenAnswer(inv -> !mine(inv.getArgument(0)).isEmpty());
        when(ledger.all(anyString())).thenAnswer(inv -> mine(inv.getArgument(0)).stream().sorted((a, b) -> a.date.compareTo(b.date)).toList());

        when(budgetRepo.save(any(Budget.class))).thenAnswer(inv -> store(budgets, inv.getArgument(0), "b"));
        when(budgetRepo.findByUserIdAndMonthLessThanEqual(anyString(), anyString())).thenAnswer(inv -> budgets.stream()
                .filter(b -> b.userId.equals(inv.getArgument(0)) && b.month.compareTo(inv.getArgument(1)) <= 0).toList());
        doAnswer(inv -> budgets.remove((Budget) inv.getArgument(0))).when(budgetRepo).delete(any(Budget.class));

        when(fxRepo.save(any(ExchangeRate.class))).thenAnswer(inv -> store(rates, inv.getArgument(0), "r"));
        when(fxRepo.findById(anyString())).thenAnswer(inv -> rates.stream().filter(r -> r.id.equals(inv.getArgument(0))).findFirst());
        doAnswer(inv -> rates.remove((ExchangeRate) inv.getArgument(0))).when(fxRepo).delete(any(ExchangeRate.class));
        when(fxRepo.findByUserIdIsNullAndCurrencyInAndDateLessThanEqual(anyCollection(), anyString())).thenAnswer(inv -> rates.stream()
                .filter(r -> r.userId == null && ((Collection<?>) inv.getArgument(0)).contains(r.currency) && r.date.compareTo(inv.getArgument(1)) <= 0).toList());
        when(fxRepo.findByUserIdAndCurrencyInAndDateLessThanEqual(anyString(), anyCollection(), anyString())).thenAnswer(inv -> rates.stream()
                .filter(r -> inv.getArgument(0).equals(r.userId) && ((Collection<?>) inv.getArgument(1)).contains(r.currency) && r.date.compareTo(inv.getArgument(2)) <= 0).toList());
        when(fxRepo.findByUserIdOrderByDateDesc(anyString())).thenAnswer(inv -> rates.stream().filter(r -> inv.getArgument(0).equals(r.userId))
                .sorted((a, b) -> b.date.compareTo(a.date)).toList());
        when(fxRepo.findFirstByUserIdIsNullAndCurrencyAndDate(anyString(), anyString())).thenAnswer(inv -> rates.stream()
                .filter(r -> r.userId == null && r.currency.equals(inv.getArgument(0)) && r.date.equals(inv.getArgument(1))).findFirst());
        when(fxRepo.findFirstByUserIdAndCurrencyAndDate(anyString(), anyString(), anyString())).thenAnswer(inv -> rates.stream()
                .filter(r -> inv.getArgument(0).equals(r.userId) && r.currency.equals(inv.getArgument(1)) && r.date.equals(inv.getArgument(2))).findFirst());
        when(fxRepo.existsByUserIdIsNullAndCurrency(anyString())).thenAnswer(inv -> rates.stream().anyMatch(r -> r.userId == null && r.currency.equals(inv.getArgument(0))));
        when(fxRepo.existsByUserIdIsNullAndDateAndSourceNot(anyString(), anyString())).thenAnswer(inv -> rates.stream()
                .anyMatch(r -> r.userId == null && r.date.equals(inv.getArgument(0)) && !r.source.equals(inv.getArgument(1))));
        when(ledger.sumByAccountMonth(anyString())).thenAnswer(inv -> {
            Map<String, BigDecimal> m = new HashMap<>();
            mine(inv.getArgument(0)).forEach(t -> t.entries.forEach(e -> m.merge(e.accountId + "|" + t.date.substring(0, 7), e.amount, BigDecimal::add)));
            return m.entrySet().stream().map(e -> new LedgerQueries.AccountMonth(e.getKey().split("\\|")[0], e.getKey().split("\\|")[1], e.getValue())).toList();
        });

        when(ledger.titleStartsWith(anyString(), anyString(), anyInt())).thenAnswer(inv -> newestFirst(mine(inv.getArgument(0))).stream()
                .filter(t -> t.title.startsWith(inv.getArgument(1))).toList());
    }

    private long clock;

    /** 設成 true 讓 txRepo.saveAll 失敗（測試補償） */
    boolean failSaveAll;

    private void upsertAccount(Account a) {
        accounts.removeIf(x -> x.id.equals(a.id));
        accounts.add(a);
    }

    private <T> T store(List<T> list, T obj, String prefix) {
        try {
            var f = obj.getClass().getField("id");
            if (f.get(obj) == null) { f.set(obj, prefix + (++seq)); list.add(obj); } else if (!list.contains(obj)) {
                Object id = f.get(obj);
                boolean exists = false;
                for (T x : list) if (id.equals(f.get(x))) exists = true;
                if (!exists) list.add(obj); // 預先指定 id 的新文件（還原備份）
            }
            return obj;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private List<Transaction> mine(String userId) {
        return transactions.stream().filter(t -> t.userId.equals(userId)).toList();
    }

    private List<Transaction> byAccount(String userId, String accountId) {
        return mine(userId).stream().filter(t -> t.entries.stream().anyMatch(e -> e.accountId.equals(accountId)))
                .sorted((a, b) -> a.date.compareTo(b.date)).toList();
    }

    private static List<Transaction> newestFirst(List<Transaction> list) {
        return list.stream().sorted((a, b) -> b.date.compareTo(a.date)).toList();
    }

    // ---- 建立資料的捷徑 ----

    Account account(String user, String name, String kind, String currency, String opening, String openingDate) {
        return accountService.create(user, new AccountRequest(name, kind, currency, new BigDecimal(opening), openingDate, true, null, null));
    }

    Account account(String name, String currency) {
        return account(ME, name, "bank", currency, "0", "2026-01-01");
    }

    Category category(String user, String kind, String name) {
        return categoryService.create(user, new CategoryService.CategoryRequest(kind, null, name, null, "#22D3EE"));
    }

    static TransactionRequest expense(String accountId, String categoryId, String amount, String date) {
        return new TransactionRequest("expense", date, "午餐", null, null, categoryId, accountId, new BigDecimal(amount), null, null, null, null, null);
    }

    static TransactionRequest income(String accountId, String categoryId, String amount, String date) {
        return new TransactionRequest("income", date, "薪資", null, null, categoryId, accountId, new BigDecimal(amount), null, null, null, null, null);
    }

    static TransactionRequest transfer(String fromId, String toId, String amount, String toAmount, String fee, String date) {
        return new TransactionRequest("transfer", date, null, null, null, null, fromId, new BigDecimal(amount), toId,
                toAmount == null ? null : new BigDecimal(toAmount), fee == null ? null : new BigDecimal(fee), null, null);
    }

    static User user(String id) {
        User u = new User();
        u.id = id;
        u.status = User.ACTIVE;
        return u;
    }

    /** 系統匯率：1 currency = rate TWD */
    ExchangeRate rate(String currency, String date, String rate) {
        ExchangeRate r = new ExchangeRate();
        r.currency = currency;
        r.date = date;
        r.rate = new BigDecimal(rate);
        r.source = "test";
        return store(rates, r, "r");
    }

    Optional<Account> find(String id) {
        return accounts.stream().filter(a -> a.id.equals(id)).findFirst();
    }
}
