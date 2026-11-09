package com.doaccounts.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;

/**
 * 使用者（Google 登入，SD 2.2）。
 */
@Document("users")
public class User {

    public static final String PENDING = "pending";
    public static final String ACTIVE = "active";
    public static final String DISABLED = "disabled";

    @Id
    public String id;
    /** Google 帳號的唯一 id（Email 可能會變，用這個認人）。 */
    @Field("google_sub")
    public String googleSub;
    public String email;
    public String name;
    public String picture;
    /** pending 待核准 / active 使用中 / disabled 已停用 */
    public String status = PENDING;
    public boolean admin;
    /** 本位幣（SA FR-1.7）：總覽、預算、報表都換算成這個幣別 */
    @Field("base_currency")
    public String baseCurrency = "TWD";
    /** 記帳偏好（SD 2.2）；舊文件沒有這個欄位時用預設值 */
    public Settings settings = new Settings();
    @Field("created_at")
    public Instant createdAt;
    @Field("last_login_at")
    public Instant lastLoginAt;

    public static class Settings {
        /** 每週起始日：mon / sun */
        @Field("week_start")
        public String weekStart = "mon";
        /** 預算警示門檻（%），達到時進度條變橘色（SA FR-5.5） */
        @Field("budget_warn_pct")
        public int budgetWarnPct = 80;
    }

    /** 讀取偏好設定，舊文件沒有 settings 時回預設值 */
    public Settings settingsOrDefault() {
        return settings == null ? new Settings() : settings;
    }
}
