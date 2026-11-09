package com.doaccounts.service;

import com.doaccounts.model.Account;
import com.doaccounts.model.Budget;
import com.doaccounts.model.Category;
import com.doaccounts.model.ExchangeRate;
import com.doaccounts.model.ImportTemplate;
import com.doaccounts.model.Transaction;
import com.doaccounts.model.Transaction.Entry;
import com.doaccounts.model.User;
import com.doaccounts.repository.AccountRepository;
import com.doaccounts.repository.BudgetRepository;
import com.doaccounts.repository.CategoryRepository;
import com.doaccounts.repository.ExchangeRateRepository;
import com.doaccounts.repository.ImportTemplateRepository;
import com.doaccounts.repository.TransactionRepository;
import com.doaccounts.repository.UserRepository;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 完整 JSON 備份與還原（SA FR-10.8、SD 3.10）。
 * 還原只限還沒有任何交易的帳號：先清掉該帳號的帳戶、分類、預算、手動匯率、範本，再以新的 id 寫入（避免與原帳號的 id 衝突）。
 */
@Service
public class BackupService {

    private static final Logger log = LoggerFactory.getLogger(BackupService.class);

    public static final String FORMAT = "do-accounts-backup";
    public static final int VERSION = 1;
    static final int CHUNK = 500;

    private final UserRepository users;
    private final AccountRepository accountRepo;
    private final CategoryRepository categoryRepo;
    private final TransactionRepository txRepo;
    private final BudgetRepository budgetRepo;
    private final ExchangeRateRepository fxRepo;
    private final ImportTemplateRepository templateRepo;
    private final LedgerQueries ledger;
    private final CategoryService categories;

    public BackupService(UserRepository users, AccountRepository accountRepo, CategoryRepository categoryRepo, TransactionRepository txRepo,
                         BudgetRepository budgetRepo, ExchangeRateRepository fxRepo, ImportTemplateRepository templateRepo,
                         LedgerQueries ledger, CategoryService categories) {
        this.users = users;
        this.accountRepo = accountRepo;
        this.categoryRepo = categoryRepo;
        this.txRepo = txRepo;
        this.budgetRepo = budgetRepo;
        this.fxRepo = fxRepo;
        this.templateRepo = templateRepo;
        this.ledger = ledger;
        this.categories = categories;
    }

    /** 投資（證券、持倉）於階段 4 加入，屆時 VERSION 加 1 並向下相容 */
    public record Backup(String format, int version, Instant exportedAt, String baseCurrency, User.Settings settings,
                         List<Account> accounts, List<Category> categories, List<Transaction> transactions,
                         List<Budget> budgets, List<ExchangeRate> manualRates, List<ImportTemplate> importTemplates) {}

    public record RestoreResult(int accounts, int categories, int transactions, int budgets, int manualRates, int importTemplates) {}

    public Backup export(User user) {
        List<Account> accs = accountRepo.findByUserId(user.id);
        accs.forEach(a -> { a.balance = null; a.hasTransactions = null; });
        return new Backup(FORMAT, VERSION, Instant.now(), FxService.baseOf(user), user.settingsOrDefault(),
                accs, categoryRepo.findByUserId(user.id), ledger.all(user.id), budgetRepo.findByUserId(user.id),
                fxRepo.findByUserIdOrderByDateDesc(user.id), templateRepo.findByUserIdOrderByNameAsc(user.id));
    }

    public RestoreResult restore(User user, Backup b) {
        if (b == null || !FORMAT.equals(b.format())) throw bad("這不是 do-accounts 的備份檔");
        if (b.version() > VERSION) throw bad("備份檔的版本（" + b.version() + "）比系統新，請先更新系統");
        if (ledger.hasAny(user.id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "這個帳號已經有交易，只能還原到還沒有任何交易的帳號");
        }
        List<Account> accs = nz(b.accounts());
        List<Category> cats = nz(b.categories());
        List<Transaction> txs = nz(b.transactions());
        List<Budget> buds = nz(b.budgets());
        List<ExchangeRate> rates = nz(b.manualRates());
        List<ImportTemplate> tpls = nz(b.importTemplates());

        // 舊 id → 新 id；同時檢查參照都存在，有問題就整個不還原
        Map<String, String> accId = new HashMap<>();
        for (Account a : accs) accId.put(require(a.id, "帳戶缺少 id"), newId());
        Map<String, String> catId = new HashMap<>();
        for (Category c : cats) catId.put(require(c.id, "分類缺少 id"), newId());
        for (Category c : cats) if (c.parentId != null) map(catId, c.parentId, "分類「" + c.name + "」的上層分類不存在");
        for (Transaction t : txs) {
            if (t.entries == null || t.entries.isEmpty()) throw bad("交易「" + t.title + "」（" + t.date + "）沒有分錄");
            if (t.categoryId != null) map(catId, t.categoryId, "交易「" + t.title + "」的分類不存在");
            for (Entry e : t.entries) {
                map(accId, e.accountId, "交易「" + t.title + "」（" + t.date + "）的帳戶不存在");
                if (e.categoryId != null) map(catId, e.categoryId, "交易「" + t.title + "」的手續費分類不存在");
            }
        }
        for (Budget x : buds) if (x.categoryId != null) map(catId, x.categoryId, "預算的分類不存在");

        // 清掉目前的設定（帳號沒有交易，所以帳戶都沒有餘額變動）
        accountRepo.deleteAll(accountRepo.findByUserId(user.id));
        categoryRepo.deleteAll(categoryRepo.findByUserId(user.id));
        budgetRepo.deleteAll(budgetRepo.findByUserId(user.id));
        fxRepo.deleteAll(fxRepo.findByUserIdOrderByDateDesc(user.id));
        templateRepo.deleteAll(templateRepo.findByUserIdOrderByNameAsc(user.id));

        try {
            for (Account a : accs) {
                a.id = accId.get(a.id);
                a.userId = user.id;
                a.balance = null;
                a.hasTransactions = null;
            }
            accountRepo.saveAll(accs);
            for (Category c : cats) {
                c.id = catId.get(c.id);
                c.userId = user.id;
                if (c.parentId != null) c.parentId = catId.get(c.parentId);
            }
            categoryRepo.saveAll(cats);
            for (Transaction t : txs) {
                t.id = newId();
                t.userId = user.id;
                t.version = null;
                t.importBatchId = null;
                if (t.categoryId != null) t.categoryId = catId.get(t.categoryId);
                for (Entry e : t.entries) {
                    e.accountId = accId.get(e.accountId);
                    if (e.categoryId != null) e.categoryId = catId.get(e.categoryId);
                }
            }
            for (int i = 0; i < txs.size(); i += CHUNK) txRepo.saveAll(txs.subList(i, Math.min(i + CHUNK, txs.size())));
            for (Budget x : buds) {
                x.id = null;
                x.userId = user.id;
                if (x.categoryId != null) x.categoryId = catId.get(x.categoryId);
            }
            budgetRepo.saveAll(buds);
            for (ExchangeRate r : rates) {
                r.id = null;
                r.userId = user.id;
            }
            fxRepo.saveAll(rates);
            for (ImportTemplate t : tpls) {
                t.id = null;
                t.userId = user.id;
            }
            templateRepo.saveAll(tpls);

            User u = users.findById(user.id).orElseThrow();
            if (b.baseCurrency() != null && Currencies.supported(b.baseCurrency())) u.baseCurrency = b.baseCurrency();
            if (b.settings() != null) u.settings = b.settings();
            users.save(u);
        } catch (RuntimeException e) {
            log.error("還原備份失敗，清除已寫入的資料", e);
            cleanup(user.id);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "還原失敗，資料未變更，請重試");
        }
        return new RestoreResult(accs.size(), cats.size(), txs.size(), buds.size(), rates.size(), tpls.size());
    }

    /** 還原失敗：回到「空帳號＋預設分類」 */
    private void cleanup(String userId) {
        try {
            txRepo.deleteAll(ledger.all(userId));
            accountRepo.deleteAll(accountRepo.findByUserId(userId));
            categoryRepo.deleteAll(categoryRepo.findByUserId(userId));
            budgetRepo.deleteAll(budgetRepo.findByUserId(userId));
            fxRepo.deleteAll(fxRepo.findByUserIdOrderByDateDesc(userId));
            templateRepo.deleteAll(templateRepo.findByUserIdOrderByNameAsc(userId));
            categories.ensureDefaults(userId);
        } catch (RuntimeException e) {
            log.error("還原失敗後清除資料也失敗，使用者 {} 需人工處理", userId, e);
        }
    }

    private static <T> List<T> nz(List<T> l) {
        return l == null ? new ArrayList<>() : new ArrayList<>(l.stream().filter(Objects::nonNull).toList());
    }

    private static String require(String v, String msg) {
        if (v == null || v.isBlank()) throw bad(msg);
        return v;
    }

    private static void map(Map<String, String> ids, String id, String msg) {
        if (!ids.containsKey(id)) throw bad(msg);
    }

    private static String newId() {
        return new ObjectId().toHexString();
    }

    private static ResponseStatusException bad(String m) {
        return new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, m);
    }
}
