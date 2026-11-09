package com.doaccounts.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/**
 * 供前端驗證 API 網址與 Token 是否正確。
 */
@RestController
@RequestMapping("/api/ping")
public class PingController {

    @GetMapping
    public Map<String, Object> ping() {
        return Map.of("ok", true, "time", Instant.now().toString());
    }
}
