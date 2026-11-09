package com.doaccounts.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

/**
 * 收支分類，最多兩層（SD 2.4）。system_key 非 null 為系統分類，不可刪除。
 */
@Document("categories")
public class Category {

    public static final String INCOME = "income";
    public static final String EXPENSE = "expense";

    public static final String SYS_OTHER = "other";
    public static final String SYS_ADJUSTMENT = "adjustment";
    public static final String SYS_FEE = "fee";
    public static final String SYS_INVESTMENT_PNL = "investment_pnl";

    @Id
    public String id;
    @Field("user_id")
    public String userId;
    public String kind;
    @Field("parent_id")
    public String parentId;
    public String name;
    /** 前端圖示代碼（frontend/src/utils/icons.js） */
    public String icon;
    /** #RRGGBB（大寫） */
    public String color;
    public int order;
    @Field("system_key")
    public String systemKey;
}
