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
    /** 投資買進、賣出的交割現金（不計入收支）；現金股利與投資費用用 main，才會計入收支（SD 2.6） */
    public static final String ROLE_SETTLEMENT = "settlement";

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
    /** kind = investment 時的投資明細（SD 2.6） */
    public Investment investment;
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

    /** 投資交易明細。金額都是證券幣別；交割現金在 entries（帳戶幣別） */
    public static class Investment {
        public static final String BUY = "buy";
        public static final String SELL = "sell";
        public static final String CASH_DIVIDEND = "cash_dividend";
        public static final String STOCK_DIVIDEND = "stock_dividend";
        public static final String SPLIT = "split";
        public static final String FEE = "fee";

        /** 持有證券的投資帳戶 */
        @Field("account_id")
        public String accountId;
        @Field("security_id")
        public String securityId;
        @Field("trade_type")
        public String tradeType;
        /** 股數（買、賣、股票股利）；現金股利為參與配息的股數；分割為比例（1 股變幾股） */
        @Field(targetType = FieldType.DECIMAL128)
        public BigDecimal quantity;
        /** 成交價；現金股利為每股配息 */
        @Field(targetType = FieldType.DECIMAL128)
        public BigDecimal price;
        @Field(targetType = FieldType.DECIMAL128)
        public BigDecimal fee;
        @Field(targetType = FieldType.DECIMAL128)
        public BigDecimal tax;
    }
}
