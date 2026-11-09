package com.doaccounts.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

import java.math.BigDecimal;

/**
 * 預算（SD 2.7）。category_id 為 null 是當月總預算。
 * 某分類在某月的有效預算 = 月份 ≤ 該月中最新的一筆：同月直接用；較早的月份只有 recurring 才沿用。
 * amount 為 null 是「停止」標記：從這個月起不再沿用之前的循環預算。
 */
@Document("budgets")
public class Budget {

    @Id
    public String id;
    @Field("user_id")
    public String userId;
    public String month;
    @Field("category_id")
    public String categoryId;
    @Field(targetType = FieldType.DECIMAL128)
    public BigDecimal amount;
    public boolean recurring;
}
