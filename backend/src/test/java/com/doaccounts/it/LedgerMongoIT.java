package com.doaccounts.it;

import com.doaccounts.model.Transaction;
import com.doaccounts.repository.TransactionRepository;
import com.doaccounts.service.LedgerQueries;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.repository.support.MongoRepositoryFactory;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 需要真的 MongoDB 才能驗證的行為（mock 測不出來）：
 * Decimal128 加總、刪除時比對版本號、補償時寫回舊版本文件。
 * 只在設定 MONGO_IT_URI 時執行，例如：
 * MONGO_IT_URI='mongodb://127.0.0.1:27017/?directConnection=true' ./mvnw test -Dtest=LedgerMongoIT
 */
@EnabledIfEnvironmentVariable(named = "MONGO_IT_URI", matches = ".+")
class LedgerMongoIT {

    private static MongoClient client;
    private static MongoTemplate mongo;
    private static TransactionRepository repo;
    private static LedgerQueries ledger;

    @BeforeAll
    static void setUp() {
        client = MongoClients.create(System.getenv("MONGO_IT_URI"));
        mongo = new MongoTemplate(client, "do_accounts_it_" + System.currentTimeMillis());
        repo = new MongoRepositoryFactory(mongo).getRepository(TransactionRepository.class);
        ledger = new LedgerQueries(mongo);
    }

    @AfterAll
    static void tearDown() {
        mongo.getDb().drop();
        client.close();
    }

    private static Transaction tx(String user, String account, String amount, String date) {
        Transaction t = new Transaction();
        t.userId = user;
        t.kind = "expense";
        t.date = date;
        t.title = "t";
        t.entries = List.of(new Transaction.Entry(account, new BigDecimal(amount), "main", null));
        return t;
    }

    @Test
    void decimal128SumIsExact() {
        repo.save(tx("u1", "a1", "0.10", "2026-11-01"));
        repo.save(tx("u1", "a1", "0.20", "2026-11-02"));
        repo.save(tx("u2", "a1", "99", "2026-11-02"));
        Map<String, BigDecimal> sums = ledger.sumByAccount("u1");
        assertEquals(0, new BigDecimal("0.30").compareTo(sums.get("a1")), "0.1 + 0.2 精確等於 0.3，且不含別人的");
        assertEquals("2026-11-01", ledger.earliestDate("u1", "a1").orElseThrow());
    }

    @Test
    void deleteWithStaleVersionFailsAndRestoreWritesOriginalBack() {
        Transaction t = repo.save(tx("u3", "a3", "-100", "2026-11-01"));
        Transaction original = repo.findById(t.id).orElseThrow();

        // 別處修改了（版本 +1）
        Transaction other = repo.findById(t.id).orElseThrow();
        other.title = "被別人改了";
        repo.save(other);

        assertThrows(OptimisticLockingFailureException.class, () -> repo.delete(original), "刪除時會比對版本號");
        assertThrows(OptimisticLockingFailureException.class, () -> repo.save(original), "更新時會比對版本號");

        ledger.restore(original);
        Transaction back = repo.findById(t.id).orElseThrow();
        assertEquals("t", back.title, "補償會不經版本檢查直接寫回原始內容");
        assertEquals(original.version, back.version);

        ledger.removeByIds("u3", List.of(t.id));
        assertTrue(repo.findById(t.id).isEmpty());
    }
}
