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

    public boolean hasTransactions(String userId, String accountId) {
        return mongo.exists(Query.query(where("user_id").is(userId).and("entries.account_id").is(accountId)), Transaction.class);
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

    private static boolean notEmpty(Collection<?> c) {
        return c != null && !c.isEmpty();
    }

    private static BigDecimal toBigDecimal(Object o) {
        if (o instanceof Decimal128 d) return d.bigDecimalValue();
        if (o instanceof Number n) return new BigDecimal(n.toString());
        return BigDecimal.ZERO;
    }
}
