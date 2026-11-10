package com.doaccounts.config;

import com.doaccounts.model.Account;
import com.doaccounts.model.Budget;
import com.doaccounts.model.Candle;
import com.doaccounts.model.CandleMonth;
import com.doaccounts.model.Category;
import com.doaccounts.model.ExchangeRate;
import com.doaccounts.model.ImportBatch;
import com.doaccounts.model.ImportTemplate;
import com.doaccounts.model.Price;
import com.doaccounts.model.Security;
import com.doaccounts.model.Transaction;
import com.doaccounts.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.stereotype.Component;

/**
 * 伺服器啟動完成後在背景建立索引（SD 2.1-6、ADR-10）。
 * 不用 auto-index-creation：本機沒有 MongoDB 時那會讓啟動失敗；這裡失敗只記錄日誌。
 * 各集合的索引在對應階段加入 {@link #ensureIndexes()}。
 */
@Component
public class IndexInitializer {

    private static final Logger log = LoggerFactory.getLogger(IndexInitializer.class);

    private final MongoTemplate mongo;

    public IndexInitializer(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        Thread.ofVirtual().name("index-initializer").start(this::ensureIndexesSafely);
    }

    void ensureIndexesSafely() {
        try {
            ensureIndexes();
            log.info("索引檢查完成");
        } catch (RuntimeException e) {
            log.warn("建立索引失敗（不影響啟動）：{}", e.getMessage());
        }
    }

    void ensureIndexes() {
        mongo.indexOps(User.class).createIndex(new Index().on("google_sub", Sort.Direction.ASC).unique());
        mongo.indexOps(Account.class).createIndex(new Index().on("user_id", Sort.Direction.ASC).on("kind", Sort.Direction.ASC).on("order", Sort.Direction.ASC));
        mongo.indexOps(Category.class).createIndex(new Index().on("user_id", Sort.Direction.ASC).on("kind", Sort.Direction.ASC).on("order", Sort.Direction.ASC));
        // SD 2.5：依日期查明細、依帳戶算餘額、依分類篩選
        mongo.indexOps(Transaction.class).createIndex(new Index().on("user_id", Sort.Direction.ASC).on("date", Sort.Direction.DESC));
        mongo.indexOps(Transaction.class).createIndex(new Index().on("entries.account_id", Sort.Direction.ASC).on("date", Sort.Direction.ASC));
        mongo.indexOps(Transaction.class).createIndex(new Index().on("user_id", Sort.Direction.ASC).on("category_id", Sort.Direction.ASC).on("date", Sort.Direction.DESC));
        // SD 2.7、2.8
        mongo.indexOps(Budget.class).createIndex(new Index().on("user_id", Sort.Direction.ASC).on("month", Sort.Direction.ASC).on("category_id", Sort.Direction.ASC).unique());
        mongo.indexOps(ExchangeRate.class).createIndex(new Index().on("date", Sort.Direction.ASC).on("currency", Sort.Direction.ASC).on("base", Sort.Direction.ASC).on("user_id", Sort.Direction.ASC).unique());
        mongo.indexOps(ExchangeRate.class).createIndex(new Index().on("currency", Sort.Direction.ASC).on("user_id", Sort.Direction.ASC).on("date", Sort.Direction.ASC));
        // SD 2.9：匯入紀錄、範本、依批次撤銷
        mongo.indexOps(ImportBatch.class).createIndex(new Index().on("user_id", Sort.Direction.ASC).on("created_at", Sort.Direction.DESC));
        mongo.indexOps(ImportTemplate.class).createIndex(new Index().on("user_id", Sort.Direction.ASC));
        mongo.indexOps(Transaction.class).createIndex(new Index().on("user_id", Sort.Direction.ASC).on("import_batch_id", Sort.Direction.ASC).sparse());
        // SD 2.6：證券、報價、投資交易
        mongo.indexOps(Security.class).createIndex(new Index().on("market", Sort.Direction.ASC).on("symbol", Sort.Direction.ASC).unique());
        mongo.indexOps(Price.class).createIndex(new Index().on("security_id", Sort.Direction.ASC).on("date", Sort.Direction.ASC).on("user_id", Sort.Direction.ASC).unique());
        mongo.indexOps(Transaction.class).createIndex(new Index().on("user_id", Sort.Direction.ASC).on("kind", Sort.Direction.ASC).on("date", Sort.Direction.ASC));
        // SD 4.10：K 線資料
        mongo.indexOps(Candle.class).createIndex(new Index().on("security_id", Sort.Direction.ASC).on("date", Sort.Direction.ASC).unique());
        mongo.indexOps(CandleMonth.class).createIndex(new Index().on("security_id", Sort.Direction.ASC).on("month", Sort.Direction.ASC).unique());
    }
}
