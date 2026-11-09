package com.doaccounts.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 每日匯率（SD 2.8）。一律以新台幣為中介：1 currency = rate TWD。
 * user_id 為 null 是排程抓取的（全站共用）；有值是該使用者的手動覆寫。
 */
@Document("exchange_rates")
public class ExchangeRate {

    public static final String SOURCE_PRIMARY = "currency-api";
    public static final String SOURCE_BACKUP = "currency-api-mirror";
    public static final String SOURCE_SEED = "seed";
    public static final String SOURCE_MANUAL = "manual";

    @Id
    public String id;
    public String date;
    public String currency;
    public String base = "TWD";
    @Field(targetType = FieldType.DECIMAL128)
    public BigDecimal rate;
    public String source;
    @Field("user_id")
    public String userId;
    @Field("updated_at")
    public Instant updatedAt;
}
