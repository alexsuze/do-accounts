package com.doaccounts.service;

import com.doaccounts.config.AppProperties;
import com.doaccounts.config.AppProperties.Auth;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 通行證：簽發 / 驗證、竄改、過期、密鑰不同、密鑰太短。 */
class TokenServiceTest {

    private static TokenService service(String secret) {
        return new TokenService(new AppProperties(null, null, null, new Auth(null, null, secret)));
    }

    private final TokenService tokens = service("0123456789abcdef0123456789abcdef");

    @Test
    void roundTrip() {
        assertEquals("u1", tokens.verify(tokens.issue("u1")));
    }

    @Test
    void rejectsBadTokens() {
        String t = tokens.issue("u1");
        assertNull(tokens.verify(t.substring(0, t.length() - 2) + "xx"), "簽章被改");
        assertNull(tokens.verify(tokens.issue("u2").split("\\.")[0] + "." + t.split("\\.")[1]), "換了使用者");
        assertNull(tokens.verify(tokens.issue("u1", Instant.now().minusSeconds(1))), "過期");
        assertNull(service("another-secret-0123456789abcdefXYZ").verify(t), "密鑰不同");
        assertNull(tokens.verify("garbage"));
        assertNull(tokens.verify(""));
    }

    @Test
    void secretTooShort() {
        assertThrows(IllegalStateException.class, () -> service("short"));
    }
}
