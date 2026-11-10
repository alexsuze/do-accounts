package com.doaccounts.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

import java.math.BigDecimal;

/**
 * 台股每日行情（K 線，SD 4.10），全站共用。唯一鍵 { security_id, date }。
 * 只在使用者開啟 K 線圖時，依需要向證交所／櫃買中心補抓。
 */
@Document("candles")
public class Candle {

    @Id
    public String id;
    @Field("security_id")
    public String securityId;
    public String date;
    @Field(targetType = FieldType.DECIMAL128)
    public BigDecimal open;
    @Field(targetType = FieldType.DECIMAL128)
    public BigDecimal high;
    @Field(targetType = FieldType.DECIMAL128)
    public BigDecimal low;
    @Field(targetType = FieldType.DECIMAL128)
    public BigDecimal close;
    /** 成交股數 */
    public long volume;
}
