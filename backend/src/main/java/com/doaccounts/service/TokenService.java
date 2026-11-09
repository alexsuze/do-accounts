package com.doaccounts.service;

import com.doaccounts.config.AppProperties;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

/**
 * 登入後發給前端的通行證：「使用者 id:到期秒數」加 HMAC-SHA256 簽章，有效 30 天。
 * 取捨：只需要「誰、何時過期」，不用整套 JWT 函式庫。
 */
@Service
public class TokenService {

    static final Duration TTL = Duration.ofDays(30);
    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder B64D = Base64.getUrlDecoder();

    private final byte[] key;

    public TokenService(AppProperties props) {
        String secret = props.auth().jwtSecret();
        key = secret.getBytes(StandardCharsets.UTF_8);
        if (key.length < 32) throw new IllegalStateException("JWT_SECRET 至少需要 32 字元");
        // 正式環境忘了設定就拒絕啟動，避免用公開的預設密鑰簽通行證
        String front = props.frontendUrl();
        if (secret.startsWith("local-dev-secret") && front != null && !front.contains("localhost")) {
            throw new IllegalStateException("正式環境必須設定 JWT_SECRET");
        }
    }

    public String issue(String userId) {
        return issue(userId, Instant.now().plus(TTL));
    }

    String issue(String userId, Instant expiresAt) {
        String payload = userId + ":" + expiresAt.getEpochSecond();
        return B64.encodeToString(payload.getBytes(StandardCharsets.UTF_8)) + "." + B64.encodeToString(sign(payload));
    }

    /** @return 使用者 id；簽章不符、格式錯誤或已過期回傳 null */
    public String verify(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 2) return null;
            String payload = new String(B64D.decode(parts[0]), StandardCharsets.UTF_8);
            if (!MessageDigest.isEqual(sign(payload), B64D.decode(parts[1]))) return null;
            int i = payload.lastIndexOf(':');
            if (Instant.now().getEpochSecond() >= Long.parseLong(payload.substring(i + 1))) return null;
            return payload.substring(0, i);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private byte[] sign(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
