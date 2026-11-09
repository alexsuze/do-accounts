package com.doaccounts.config;

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
    }
}
