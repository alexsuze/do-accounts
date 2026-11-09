package com.doaccounts.service;

import com.doaccounts.model.Transaction;
import org.bson.Document;
import org.bson.types.Decimal128;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import static org.springframework.data.mongodb.core.aggregation.Aggregation.group;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.match;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.unwind;
import static org.springframework.data.mongodb.core.query.Criteria.where;

/**
 * transactions 集合的查詢（MongoTemplate）。集中在這裡，service 單元測試可以 mock 掉、不需要 MongoDB。
 * 所有方法第一個參數都是 userId（資料隔離）。
 */
@Component
public class LedgerQueries {

    private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("date"), Sort.Order.desc("created_at"));
    private static final Sort OLDEST_FIRST = Sort.by(Sort.Order.asc("date"), Sort.Order.asc("created_at"));

    private final MongoTemplate mongo;

    public LedgerQueries(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    /** 每個帳戶的分錄加總（SD 2.5 帳戶餘額查詢）。沒有分錄的帳戶不在結果裡。 */
    public Map<String, BigDecimal> sumByAccount(String userId) {
        Aggregation agg = Aggregation.newAggregation(
                match(where("user_id").is(userId)),
                unwind("entries"),
                group("entries.account_id").sum("entries.amount").as("sum"));
        Map<String, BigDecimal> out = new HashMap<>();
        for (Document d : mongo.aggregate(agg, "transactions", Document.class).getMappedResults()) {
            out.put(d.getString("_id"), toBigDecimal(d.get("sum")));
        }
        return out;
    }

    /** 每個帳戶每個月的分錄加總（淨資產趨勢，SD 4.6 步驟 1） */
    public List<AccountMonth> sumByAccountMonth(String userId) {
        Aggregation agg = Aggregation.newAggregation(
                match(where("user_id").is(userId)),
                unwind("entries"),
                Aggregation.project().and("entries.account_id").as("account_id").and("entries.amount").as("amount")
                        .and(org.springframework.data.mongodb.core.aggregation.StringOperators.Substr.valueOf("date").substring(0, 7)).as("month"),
                group("account_id", "month").sum("amount").as("sum"));
        List<AccountMonth> out = new ArrayList<>();
        for (Document d : mongo.aggregate(agg, "transactions", Document.class).getMappedResults()) {
            Document id = d.get("_id", Document.class);
            out.add(new AccountMonth(id.getString("account_id"), id.getString("month"), toBigDecimal(d.get("sum"))));
        }
        return out;
    }

    public record AccountMonth(String accountId, String month, BigDecimal sum) {}

    /** 有分錄，或是投資交易的持有帳戶（股票股利、分割沒有現金分錄） */
    public boolean hasTransactions(String userId, String accountId) {
        return mongo.exists(Query.query(where("user_id").is(userId).orOperator(
                where("entries.account_id").is(accountId), where("investment.account_id").is(accountId))), Transaction.class);
    }

    /** 某使用者全部的投資交易（持倉計算），舊到新 */
    public List<Transaction> investments(String userId) {
        return mongo.find(Query.query(where("user_id").is(userId).and("kind").is(Transaction.INVESTMENT)).with(OLDEST_FIRST), Transaction.class);
    }

    /** 該帳戶最早一筆交易的日期 */
    public Optional<String> earliestDate(String userId, String accountId) {
        Query q = Query.query(where("user_id").is(userId).and("entries.account_id").is(accountId)).with(OLDEST_FIRST).limit(1);
        q.fields().include("date");
        return Optional.ofNullable(mongo.findOne(q, Transaction.class)).map(t -> t.date);
    }

    /** 該帳戶的全部交易，舊到新（帳戶詳細頁的每日餘額與交易後餘額） */
    public List<Transaction> byAccount(String userId, String accountId) {
        return mongo.find(Query.query(where("user_id").is(userId).and("entries.account_id").is(accountId)).with(OLDEST_FIRST), Transaction.class);
    }

    /**
     * 明細查詢，新到舊。categoryIds 已展開子分類；q 為關鍵字（項目或備註，不分大小寫）。
     * 日期用字串範圍比較（ADR-06），不用正規表達式。
     */
    public List<Transaction> search(String userId, Filter f) {
        List<Criteria> and = new ArrayList<>();
        and.add(where("user_id").is(userId));
        if (f.from() != null || f.to() != null) {
            Criteria d = where("date");
            if (f.from() != null) d = d.gte(f.from());
            if (f.to() != null) d = d.lte(f.to());
            and.add(d);
        }
        if (notEmpty(f.kinds())) and.add(where("kind").in(f.kinds()));
        if (notEmpty(f.accountIds())) and.add(where("entries.account_id").in(f.accountIds()));
        if (notEmpty(f.categoryIds())) {
            and.add(new Criteria().orOperator(where("category_id").in(f.categoryIds()), where("entries.category_id").in(f.categoryIds())));
        }
        if (notEmpty(f.tags())) and.add(where("tags").in(f.tags()));
        if (f.q() != null && !f.q().isBlank()) {
            Pattern p = Pattern.compile(Pattern.quote(f.q().trim()), Pattern.CASE_INSENSITIVE);
            and.add(new Criteria().orOperator(where("title").regex(p), where("note").regex(p)));
        }
        return mongo.find(Query.query(new Criteria().andOperator(and)).with(NEWEST_FIRST), Transaction.class);
    }

    public record Filter(String from, String to, Collection<String> kinds, Collection<String> accountIds,
                         Collection<String> categoryIds, Collection<String> tags, String q) {}

    public List<Transaction> recent(String userId, int limit) {
        return mongo.find(Query.query(where("user_id").is(userId)).with(NEWEST_FIRST).limit(limit), Transaction.class);
    }

    /** 項目名稱以 prefix 開頭的近期交易（常用項目建議，SA FR-3.7） */
    public List<Transaction> titleStartsWith(String userId, String prefix, int limit) {
        Pattern p = Pattern.compile("^" + Pattern.quote(prefix.trim()), Pattern.CASE_INSENSITIVE);
        return mongo.find(Query.query(where("user_id").is(userId).and("title").regex(p)).with(NEWEST_FIRST).limit(limit), Transaction.class);
    }

    public List<Transaction> findByIds(String userId, Collection<String> ids) {
        return mongo.find(Query.query(where("user_id").is(userId).and("_id").in(ids)), Transaction.class);
    }

    /** 使用到這些分類的交易數（含手續費分錄的分類） */
    public long countUsingCategories(String userId, Collection<String> categoryIds) {
        return mongo.count(Query.query(where("user_id").is(userId).orOperator(
                where("category_id").in(categoryIds), where("entries.category_id").in(categoryIds))), Transaction.class);
    }

    /** 刪除分類前，把交易搬到另一個分類（SA FR-4.3） */
    public void moveCategories(String userId, Collection<String> from, String to) {
        mongo.updateMulti(Query.query(where("user_id").is(userId).and("category_id").in(from)),
                new Update().set("category_id", to), Transaction.class);
        // 手續費分錄的分類在陣列裡，數量少，讀出來改完寫回
        for (Transaction t : mongo.find(Query.query(where("user_id").is(userId).and("entries.category_id").in(from)), Transaction.class)) {
            t.entries.stream().filter(e -> e.categoryId != null && from.contains(e.categoryId)).forEach(e -> e.categoryId = to);
            mongo.save(t);
        }
    }

    /**
     * 多筆儲存失敗時的補償用（SD 4.2.1 步驟 6）：直接把原始文件寫回，不經過版本檢查。
     * 先刪掉同 id 的文件（可能是已被更新過的版本），再插入原始內容。
     */
    public void restore(Transaction original) {
        mongo.remove(Query.query(where("_id").is(original.id)), Transaction.class);
        mongo.insert(original);
    }

    /** 刪除某次匯入的全部交易（整批撤銷、匯入失敗補償，SD 4.7），回傳刪除筆數 */
    public long removeByBatch(String userId, String batchId) {
        return mongo.remove(Query.query(where("user_id").is(userId).and("import_batch_id").is(batchId)), Transaction.class).getDeletedCount();
    }

    /** 該使用者是否有任何交易（JSON 還原只限空帳號） */
    public boolean hasAny(String userId) {
        return mongo.exists(Query.query(where("user_id").is(userId)), Transaction.class);
    }

    /** 某使用者的全部交易，舊到新（備份、匯出） */
    public List<Transaction> all(String userId) {
        return mongo.find(Query.query(where("user_id").is(userId)).with(OLDEST_FIRST), Transaction.class);
    }

    /** 補償用：刪掉這次新增的文件 */
    public void removeByIds(String userId, Collection<String> ids) {
        if (ids.isEmpty()) return;
        mongo.remove(Query.query(where("user_id").is(userId).and("_id").in(ids)), Transaction.class);
    }

    private static boolean notEmpty(Collection<?> c) {
        return c != null && !c.isEmpty();
    }

    private static BigDecimal toBigDecimal(Object o) {
        if (o instanceof Decimal128 d) return d.bigDecimalValue();
        if (o instanceof Number n) return new BigDecimal(n.toString());
        return BigDecimal.ZERO;
    }
}
