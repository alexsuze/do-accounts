package com.doaccounts.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;

/**
 * 某證券某個月的行情已經抓過（避免重複向交易所查詢）。過去的月份抓過一次就不再抓；本月隔一段時間才重抓。
 * 唯一鍵 { security_id, month }。
 */
@Document("candle_months")
public class CandleMonth {

    @Id
    public String id;
    @Field("security_id")
    public String securityId;
    /** YYYY-MM */
    public String month;
    @Field("fetched_at")
    public Instant fetchedAt;
}
