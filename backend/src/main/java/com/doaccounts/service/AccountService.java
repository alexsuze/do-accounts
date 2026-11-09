package com.doaccounts.service;

import com.doaccounts.model.Account;
import com.doaccounts.model.Account.Credit;
import com.doaccounts.repository.AccountRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 帳戶管理（SA FR-2、SD 3.3）。只能操作自己的帳戶；餘額由分錄推導。
 */
@Service
public class AccountService {

    /** 帳戶分組的顯示順序（總覽、帳戶頁） */
    public static final List<String> KIND_ORDER = List.of("cash", "bank", "credit_card", "e_wallet", "investment", "loan", "other");

    private final AccountRepository repo;
    private final LedgerQueries ledger;

    public AccountService(AccountRepository repo, LedgerQueries ledger) {
        this.repo = repo;
        this.ledger = ledger;
    }

    public record AccountRequest(String name, String kind, String currency, BigDecimal openingBalance, String openingDate,
                                 Boolean visible, String note, CreditRequest credit) {}

    public record CreditRequest(BigDecimal limit, Integer statementDay, Integer dueDay) {}

    /** 含餘額，依類型 → 順序排序 */
    public List<Account> list(String userId, boolean includeArchived) {
        Map<String, BigDecimal> sums = ledger.sumByAccount(userId);
        return repo.findByUserId(userId).stream()
                .filter(a -> includeArchived || a.archivedAt == null)
                .peek(a -> {
                    BigDecimal s = sums.get(a.id);
                    a.balance = a.openingBalance.add(s == null ? BigDecimal.ZERO : s);
                    a.hasTransactions = s != null;
                })
                .sorted(Comparator.comparingInt((Account a) -> KIND_ORDER.indexOf(a.kind)).thenComparingInt(a -> a.order))
                .toList();
    }

    /** 自己的帳戶；別人的或不存在一律 404 */
    public Account get(String userId, String id) {
        return repo.findById(id == null ? "" : id).filter(a -> userId.equals(a.userId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "找不到此帳戶"));
    }

    /** 含餘額的單一帳戶 */
    public Account getWithBalance(String userId, String id) {
        Account a = get(userId, id);
        BigDecimal s = ledger.sumByAccount(userId).get(a.id);
        a.balance = a.openingBalance.add(s == null ? BigDecimal.ZERO : s);
        a.hasTransactions = s != null;
        return a;
    }

    public Account create(String userId, AccountRequest req) {
        Account a = new Account();
        a.userId = userId;
        a.kind = checkKind(req.kind());
        a.currency = checkCurrency(req.currency());
        apply(userId, a, req);
        a.order = repo.findByUserId(userId).stream().filter(x -> a.kind.equals(x.kind)).mapToInt(x -> x.order + 1).max().orElse(0);
        Account saved = repo.save(a);
        saved.balance = saved.openingBalance;
        saved.hasTransactions = false;
        return saved;
    }

    /** 完整覆寫。有交易時不能改幣別（409），開戶日期不能晚於最早交易（422）。 */
    public Account update(String userId, String id, AccountRequest req) {
        Account a = get(userId, id);
        boolean hasTx = ledger.hasTransactions(userId, id);
        String currency = checkCurrency(req.currency());
        if (hasTx && !currency.equals(a.currency)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "帳戶已有交易，不能變更幣別");
        }
        String kind = checkKind(req.kind());
        if (!kind.equals(a.kind)) {
            a.order = repo.findByUserId(userId).stream().filter(x -> kind.equals(x.kind)).mapToInt(x -> x.order + 1).max().orElse(0);
        }
        a.kind = kind;
        a.currency = currency;
        apply(userId, a, req);
        if (hasTx) {
            ledger.earliestDate(userId, id).filter(first -> a.openingDate.compareTo(first) > 0).ifPresent(first -> {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, "開戶日期不能晚於最早一筆交易（" + first + "）");
            });
        }
        repo.save(a);
        return getWithBalance(userId, id);
    }

    /** 有交易的帳戶不能刪除，只能封存（SA FR-2.3） */
    public void delete(String userId, String id) {
        Account a = get(userId, id);
        if (ledger.hasTransactions(userId, id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "帳戶已有交易，不能刪除，請改用封存");
        }
        repo.delete(a);
    }

    public Account setArchived(String userId, String id, boolean archived) {
        Account a = get(userId, id);
        a.archivedAt = archived ? Instant.now() : null;
        repo.save(a);
        return getWithBalance(userId, id);
    }

    /** ids 為同一類型帳戶的新順序 */
    public List<Account> reorder(String userId, String kind, List<String> ids) {
        List<Account> same = repo.findByUserId(userId).stream().filter(a -> a.kind.equals(kind)).toList();
        if (ids == null || ids.size() != same.size() || !ids.containsAll(same.stream().map(a -> a.id).toList())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "帳戶順序與現有帳戶不符，請重新整理");
        }
        same.forEach(a -> a.order = ids.indexOf(a.id));
        repo.saveAll(same);
        return list(userId, true);
    }

    private void apply(String userId, Account a, AccountRequest req) {
        String name = req.name() == null ? "" : req.name().trim();
        if (name.isEmpty() || name.length() > 30) throw bad("帳戶名稱需為 1～30 字");
        boolean dup = repo.findByUserId(userId).stream().anyMatch(x -> !x.id.equals(a.id) && x.name.equals(name));
        if (dup) throw new ResponseStatusException(HttpStatus.CONFLICT, "已有同名的帳戶「" + name + "」");
        a.name = name;
        a.openingBalance = Currencies.check(req.openingBalance() == null ? BigDecimal.ZERO : req.openingBalance(), a.currency, false, "初始餘額");
        a.openingDate = Dates.check(req.openingDate() == null ? Dates.today().toString() : req.openingDate(), "開戶日期");
        a.visible = req.visible() == null || req.visible();
        a.note = req.note() == null ? "" : req.note().trim();
        if (a.note.length() > 200) throw bad("備註最多 200 字");
        a.credit = "credit_card".equals(a.kind) ? credit(req.credit(), a.currency) : null;
    }

    private static Credit credit(CreditRequest r, String currency) {
        Credit c = new Credit();
        if (r == null) return c;
        if (r.limit() != null) c.limit = Currencies.check(r.limit(), currency, false, "信用額度");
        c.statementDay = day(r.statementDay(), "結帳日");
        c.dueDay = day(r.dueDay(), "繳款日");
        return c;
    }

    private static Integer day(Integer d, String label) {
        if (d != null && (d < 1 || d > 31)) throw bad(label + "需為 1～31");
        return d;
    }

    private static String checkKind(String kind) {
        if (kind == null || !Account.KINDS.contains(kind)) throw bad("帳戶類型不正確");
        return kind;
    }

    private static String checkCurrency(String c) {
        if (!Currencies.supported(c)) throw bad("不支援的幣別：" + c);
        return c;
    }

    private static ResponseStatusException bad(String m) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, m);
    }
}
