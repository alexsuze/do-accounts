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
    /** 本位幣（SA FR-1.7）；階段 2 開放修改。 */
    @Field("base_currency")
    public String baseCurrency = "TWD";
    @Field("created_at")
    public Instant createdAt;
    @Field("last_login_at")
    public Instant lastLoginAt;
}
