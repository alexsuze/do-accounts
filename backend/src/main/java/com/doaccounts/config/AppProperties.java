package com.doaccounts.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * application.properties 中 app.* 的型別化設定。
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        Cors cors,
        Resend resend,
        String frontendUrl,
        Auth auth
) {
    public record Cors(List<String> allowedOrigins) {}

    public record Resend(String apiKey, String fromEmail) {}

    /**
     * @param googleClientId Google OAuth 用戶端 ID（驗證登入憑證的 aud）
     * @param adminEmail     管理員的 Google Email，第一次登入自動成為已核准的管理員
     * @param jwtSecret      通行證簽章用的密鑰，至少 32 字元
     */
    public record Auth(String googleClientId, String adminEmail, String jwtSecret) {}
}
