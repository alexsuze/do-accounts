package com.doaccounts.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 台股盤中價（SD 4.11），全站共用。每檔每天只留最新一筆，不寫進收盤價的歷史（prices）。
 * 唯一鍵 { security_id, date }。當天的正式收盤價進來後就不再使用。
 */
@Document("intraday_quotes")
public class IntradayQuote {

    @Id
    public String id;
    @Field("security_id")
    public String securityId;
    public String date;
    @Field(targetType = FieldType.DECIMAL128)
    public BigDecimal price;
    /** 成交時間 HH:mm:ss（證交所回傳） */
    public String time;
    @Field("fetched_at")
    public Instant fetchedAt;
}
