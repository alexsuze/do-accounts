package com.doaccounts.model;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Transient;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;

/**
 * 資金帳戶（SD 2.3）。不存餘額：餘額 = opening_balance ＋ 所有分錄加總（CLAUDE.md 帳務核心規則）。
 */
@Document("accounts")
public class Account {

    public static final Set<String> KINDS = Set.of("cash", "bank", "credit_card", "e_wallet", "investment", "loan", "other");
    /** 這些類型的帳戶一律算負債（SD 4.6） */
    public static final Set<String> LIABILITY_KINDS = Set.of("credit_card", "loan");

    @Id
    public String id;
    @Field("user_id")
    public String userId;
    public String name;
    public String kind;
    public String currency;
    @Field(name = "opening_balance", targetType = FieldType.DECIMAL128)
    public BigDecimal openingBalance = BigDecimal.ZERO;
    /** YYYY-MM-DD；交易日期不可早於此日（SA FR-2.7） */
    @Field("opening_date")
    public String openingDate;
    public int order;
    public boolean visible = true;
    @Field("archived_at")
    public Instant archivedAt;
    /** 只有信用卡使用 */
    public Credit credit;
    public String note = "";
    @CreatedDate
    @Field("created_at")
    public Instant createdAt;
    @LastModifiedDate
    @Field("updated_at")
    public Instant updatedAt;

    /** 計算出的目前餘額（不存 DB）。 */
    @Transient
    public BigDecimal balance;
    /** 是否已有交易（不存 DB）；前端用來鎖住幣別、決定能否刪除。 */
    @Transient
    public Boolean hasTransactions;

    public static class Credit {
        @Field(name = "limit", targetType = FieldType.DECIMAL128)
        public BigDecimal limit;
        @Field("statement_day")
        public Integer statementDay;
        @Field("due_day")
        public Integer dueDay;
    }
}
