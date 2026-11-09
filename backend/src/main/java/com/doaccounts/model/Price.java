package com.doaccounts.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 每日收盤價（SD 2.6）。user_id 為 null 是排程抓取的（全站共用）；有值是該使用者手動輸入的，只影響自己（與手動匯率相同）。
 * 唯一鍵 { security_id, date, user_id }。
 */
@Document("prices")
public class Price {

    public static final String TWSE = "twse";
    public static final String TPEX = "tpex";
    public static final String MANUAL = "manual";

    @Id
    public String id;
    @Field("security_id")
    public String securityId;
    public String date;
    @Field(targetType = FieldType.DECIMAL128)
    public BigDecimal close;
    public String source;
    @Field("user_id")
    public String userId;
    @Field("updated_at")
    public Instant updatedAt;
}
