package com.doaccounts.model;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 一筆交易 = 一份文件，分錄內嵌在 entries（SD 2.5、ADR-02）。一次記帳只寫一份文件。
 */
@Document("transactions")
public class Transaction {

    public static final String INCOME = "income";
    public static final String EXPENSE = "expense";
    public static final String TRANSFER = "transfer";
    public static final String INVESTMENT = "investment";

    public static final String ROLE_MAIN = "main";
    public static final String ROLE_TRANSFER_OUT = "transfer_out";
    public static final String ROLE_TRANSFER_IN = "transfer_in";
    public static final String ROLE_FEE = "fee";

    @Id
    public String id;
    @Field("user_id")
    public String userId;
    public String kind;
    /** YYYY-MM-DD */
    public String date;
    public String title;
    @Field("category_id")
    public String categoryId;
    public String note = "";
    public List<String> tags = new ArrayList<>();
    public List<Entry> entries = new ArrayList<>();
    @Field("import_batch_id")
    public String importBatchId;
    /** 樂觀鎖（SD ADR-13） */
    @Version
    public Long version;
    @CreatedDate
    @Field("created_at")
    public Instant createdAt;
    @LastModifiedDate
    @Field("updated_at")
    public Instant updatedAt;

    /** 分錄：交易對某個帳戶的影響，帳戶幣別、流入為正（SD 2.5）。 */
    public static class Entry {
        @Field("account_id")
        public String accountId;
        @Field(targetType = FieldType.DECIMAL128)
        public BigDecimal amount;
        public String role;
        /** 只有 fee 使用：手續費的支出分類 */
        @Field("category_id")
        public String categoryId;

        public Entry() {}

        public Entry(String accountId, BigDecimal amount, String role, String categoryId) {
            this.accountId = accountId;
            this.amount = amount;
            this.role = role;
            this.categoryId = categoryId;
        }
    }
}
