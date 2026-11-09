package com.doaccounts.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 一次 CSV 匯入（SD 2.9）。匯入的交易都帶 import_batch_id，可以整批撤銷。
 */
@Document("import_batches")
public class ImportBatch {

    public static final String COMPLETED = "completed";
    public static final String FAILED = "failed";
    public static final String REVERTED = "reverted";

    @Id
    public String id;
    @Field("user_id")
    public String userId;
    @Field("file_name")
    public String fileName;
    /** 檔案的資料列數 */
    @Field("row_count")
    public int rowCount;
    @Field("imported_count")
    public int importedCount;
    /** 沒有匯入的列（重複、錯誤、使用者取消勾選） */
    @Field("skipped_count")
    public int skippedCount;
    /** 這次自動新增的分類名稱（撤銷時不會刪除分類） */
    @Field("created_categories")
    public List<String> createdCategories = new ArrayList<>();
    public String status;
    @Field("created_at")
    public Instant createdAt;
    @Field("reverted_at")
    public Instant revertedAt;
    @Field("reverted_count")
    public int revertedCount;
}
