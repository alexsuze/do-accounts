package com.doaccounts.controller;

import com.doaccounts.config.AuthFilter;
import com.doaccounts.model.User;
import com.doaccounts.service.UserService;
import com.doaccounts.service.UserService.LoginResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 登入 API。/api/auth/google 不需通行證（AuthFilter 放行）。
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService service;

    public AuthController(UserService service) {
        this.service = service;
    }

    /** body：{ credential: Google 回傳的身分憑證 } */
    @PostMapping("/google")
    public LoginResult google(@RequestBody Map<String, String> body) {
        return service.login(body.get("credential"));
    }

    @GetMapping("/me")
    public User me(@RequestAttribute(AuthFilter.USER) User user) {
        return user;
    }
}
