package com.doaccounts.controller;

import com.doaccounts.config.AuthFilter;
import com.doaccounts.model.User;
import com.doaccounts.service.UserService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 管理員 API：使用者列表、核准 / 停用（SA FR-1.6）。非管理員回 403。
 */
@RestController
@RequestMapping("/api/admin/users")
public class AdminController {

    private final UserService service;

    public AdminController(UserService service) {
        this.service = service;
    }

    @GetMapping
    public List<User> list(@RequestAttribute(AuthFilter.USER) User admin) {
        return service.list(admin);
    }

    /** body：{ status: active / disabled / pending } */
    @PutMapping("/{id}/status")
    public User setStatus(@RequestAttribute(AuthFilter.USER) User admin, @PathVariable String id,
                          @RequestBody Map<String, String> body) {
        return service.setStatus(admin, id, body.get("status"));
    }
}
