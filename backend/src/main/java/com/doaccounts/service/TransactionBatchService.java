package com.doaccounts.service;

import com.doaccounts.model.Transaction;
import com.doaccounts.repository.TransactionRepository;
import com.doaccounts.service.TransactionService.TransactionRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Service;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 表格模式一次儲存（SA FR-3.10～3.20、SD 4.2.1）。
 * 先全部檢查（衝突、每一列的規則），全部通過才寫入；寫入途中失敗就補償，資料回到儲存前的樣子。
 * 不使用 MongoDB 多文件交易（ADR-14）。
 */
@Service
public class TransactionBatchService {

    public static final int MAX_ROWS = 100;
    private static final Logger log = LoggerFactory.getLogger(TransactionBatchService.class);

    private final TransactionRepository repo;
    private final LedgerQueries ledger;
    private final TransactionService transactions;

    public TransactionBatchService(TransactionRepository repo, LedgerQueries ledger, TransactionService transactions) {
        this.repo = repo;
        this.ledger = ledger;
        this.transactions = transactions;
    }

    /** 新增或修改的一列；row 是前端表格的列號，只用來對應錯誤。修改時 id、version 必填。 */
    public record BatchRow(Integer row, String id, Long version, String kind, String date, String title, String note,
                           List<String> tags, String categoryId, String accountId, BigDecimal amount,
                           String toAccountId, BigDecimal toAmount, BigDecimal fee, String feeCategoryId) {
        TransactionRequest toRequest() {
            return new TransactionRequest(kind, date, title, note, tags, categoryId, accountId, amount, toAccountId, toAmount, fee, feeCategoryId, version);
        }
    }

    public record BatchDelete(Integer row, String id, Long version) {}

    public record BatchRequest(List<BatchRow> creates, List<BatchRow> updates, List<BatchDelete> deletes) {}

    public record RowError(Integer row, String field, String message) {}

    public record RowConflict(Integer row, String id) {}

    public record RowResult(Integer row, Transaction transaction) {}

    public record BatchResult(List<RowResult> created, List<RowResult> updated, List<String> deletedIds) {}

    public BatchResult save(String userId, BatchRequest req) {
        List<BatchRow> creates = req.creates() == null ? List.of() : req.creates();
        List<BatchRow> updates = req.updates() == null ? List.of() : req.updates();
        List<BatchDelete> deletes = req.deletes() == null ? List.of() : req.deletes();

        // 0. 筆數上限
        int total = creates.size() + updates.size() + deletes.size();
        if (total == 0) throw bad("沒有要儲存的變更");
        if (total > MAX_ROWS) throw bad("一次最多儲存 " + MAX_ROWS + " 列（目前 " + total + " 列），請分批儲存");
        Set<String> touched = new HashSet<>();
        for (String id : concat(updates.stream().map(BatchRow::id).toList(), deletes.stream().map(BatchDelete::id).toList())) {
            if (id == null || id.isBlank()) throw bad("修改或刪除的列缺少交易 id");
            if (!touched.add(id)) throw bad("同一筆交易在這次儲存中出現兩次，請重新載入");
        }

        // 1. 載入既有交易（只會拿到自己的）
        Map<String, Transaction> existing = ledger.findByIds(userId, touched).stream()
                .collect(Collectors.toMap(t -> t.id, Function.identity()));

        // 2. 衝突：不存在／不是自己的、版本不符、投資交易
        List<RowConflict> conflicts = new ArrayList<>();
        for (BatchRow u : updates) checkConflict(existing.get(u.id()), u.row(), u.id(), u.version(), conflicts);
        for (BatchDelete d : deletes) checkConflict(existing.get(d.id()), d.row(), d.id(), d.version(), conflicts);

        // 3. 逐列檢查，一次回報全部錯誤；通過的列先組好要寫入的文件（不動到載入的原始文件）
        List<RowError> errors = new ArrayList<>();
        List<Transaction> toCreate = new ArrayList<>();
        List<Transaction> toUpdate = new ArrayList<>();
        for (BatchRow c : creates) {
            Transaction t = new Transaction();
            t.userId = userId;
            if (prepare(userId, t, c, errors)) toCreate.add(t);
        }
        for (BatchRow u : updates) {
            Transaction orig = existing.get(u.id());
            if (orig == null) continue; // 已列為衝突
            Transaction t = copyIdentity(orig);
            if (prepare(userId, t, u, errors)) toUpdate.add(t);
        }

        // 4. 有任何問題就整批不寫入
        if (!conflicts.isEmpty()) {
            throw rejected(HttpStatus.CONFLICT, "有 " + conflicts.size() + " 筆紀錄已在其他地方被修改或刪除，請重新載入，未儲存任何資料", "conflicts", conflicts);
        }
        if (!errors.isEmpty()) {
            long rows = errors.stream().map(RowError::row).distinct().count();
            throw rejected(HttpStatus.UNPROCESSABLE_CONTENT, "有 " + rows + " 列資料有誤，未儲存任何資料", "errors", errors);
        }

        // 5. 寫入；6. 失敗就補償
        List<String> createdIds = new ArrayList<>();
        List<Transaction> updatedOriginals = new ArrayList<>();
        List<Transaction> deletedOriginals = new ArrayList<>();
        List<RowResult> created = new ArrayList<>();
        List<RowResult> updated = new ArrayList<>();
        try {
            for (int i = 0; i < toCreate.size(); i++) {
                Transaction saved = repo.save(toCreate.get(i));
                createdIds.add(saved.id);
                created.add(new RowResult(creates.get(i).row(), saved));
            }
            for (int i = 0; i < toUpdate.size(); i++) {
                Transaction t = toUpdate.get(i);
                Transaction saved = repo.save(t);
                updatedOriginals.add(existing.get(t.id));
                updated.add(new RowResult(updates.get(i).row(), saved));
            }
            for (BatchDelete d : deletes) {
                Transaction orig = existing.get(d.id());
                repo.delete(orig);
                deletedOriginals.add(orig);
            }
        } catch (RuntimeException e) {
            compensate(userId, createdIds, updatedOriginals, deletedOriginals, e);
            if (e instanceof OptimisticLockingFailureException) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "儲存途中有紀錄被其他地方修改，資料未變更，請重新載入");
            }
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "儲存失敗，資料未變更，請重試");
        }
        return new BatchResult(created, updated, deletes.stream().map(BatchDelete::id).toList());
    }

    private static void checkConflict(Transaction t, Integer row, String id, Long version, List<RowConflict> out) {
        if (t == null || !Objects.equals(t.version, version) || Transaction.INVESTMENT.equals(t.kind)) {
            out.add(new RowConflict(row, id));
        }
    }

    private boolean prepare(String userId, Transaction t, BatchRow r, List<RowError> errors) {
        try {
            transactions.fill(userId, t, r.toRequest());
            return true;
        } catch (ResponseStatusException e) {
            errors.add(new RowError(r.row(), e instanceof FieldException fe ? fe.getField() : null, e.getReason()));
            return false;
        }
    }

    /** 修改用的新物件：保留身分欄位，其餘由 fill 重建，原始文件留著補償用 */
    private static Transaction copyIdentity(Transaction o) {
        Transaction t = new Transaction();
        t.id = o.id;
        t.userId = o.userId;
        t.version = o.version;
        t.createdAt = o.createdAt;
        t.importBatchId = o.importBatchId;
        return t;
    }

    private void compensate(String userId, List<String> createdIds, List<Transaction> updatedOriginals,
                            List<Transaction> deletedOriginals, RuntimeException cause) {
        try {
            ledger.removeByIds(userId, createdIds);
            for (Transaction o : updatedOriginals) ledger.restore(o);
            for (Transaction o : deletedOriginals) ledger.restore(o);
            log.warn("多筆儲存失敗，已還原：新增 {} 筆刪除、修改 {} 筆還原、刪除 {} 筆寫回",
                    createdIds.size(), updatedOriginals.size(), deletedOriginals.size(), cause);
        } catch (RuntimeException e) {
            // 補償也失敗：記下全部原始內容，供人工還原
            log.error("多筆儲存補償失敗！需人工還原。user={} 新增的 id={} 修改前原始文件 id={} 刪除前原始文件 id={}",
                    userId, createdIds, updatedOriginals.stream().map(t -> t.id).toList(),
                    deletedOriginals.stream().map(t -> t.id).toList(), e);
        }
    }

    private static ErrorResponseException rejected(HttpStatus status, String detail, String key, Object value) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setProperty(key, value);
        return new ErrorResponseException(status, pd, null);
    }

    private static <T> List<T> concat(List<T> a, List<T> b) {
        List<T> out = new ArrayList<>(a);
        out.addAll(b);
        return out;
    }

    private static ResponseStatusException bad(String m) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, m);
    }
}
