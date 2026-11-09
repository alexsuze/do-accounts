package com.doaccounts.controller;

import com.doaccounts.config.AuthFilter;
import com.doaccounts.model.User;
import com.doaccounts.service.FxUpdater;
import com.doaccounts.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** 管理員手動執行排程（SD 3.2）。目前只有 fx（匯率）；quotes（報價）於階段 4 加入。 */
@RestController
@RequestMapping("/api/admin/jobs")
public class AdminJobController {

    private final FxUpdater fx;

    public AdminJobController(FxUpdater fx) {
        this.fx = fx;
    }

    @PostMapping("/{name}")
    public FxUpdater.Result run(@RequestAttribute(AuthFilter.USER) User admin, @PathVariable String name) {
        UserService.requireAdmin(admin);
        if (!"fx".equals(name)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "沒有這個排程：" + name);
        return fx.update();
    }
}
