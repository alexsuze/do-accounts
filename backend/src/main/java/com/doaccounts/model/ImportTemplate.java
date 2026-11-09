package com.doaccounts.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;
import java.util.Map;

/**
 * 匯入範本（SD 2.9）：某個銀行對帳單的欄位對應。mapping 的內容由前端定義（欄位對應、日期格式、金額規則、預設帳戶），後端只保存。
 */
@Document("import_templates")
public class ImportTemplate {

    @Id
    public String id;
    @Field("user_id")
    public String userId;
    public String name;
    public Map<String, Object> mapping;
    @Field("updated_at")
    public Instant updatedAt;
}
