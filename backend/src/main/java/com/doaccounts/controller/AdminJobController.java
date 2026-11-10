package com.doaccounts.controller;

import com.doaccounts.config.AuthFilter;
import com.doaccounts.model.User;
import com.doaccounts.service.FxUpdater;
import com.doaccounts.service.QuoteService;
import com.doaccounts.service.MonthlyReportService;
import com.doaccounts.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** 管理員手動執行排程（SD 3.2）：fx（匯率）、quotes（台股報價）、monthly-report（寄上個月的每月報表）。 */
@RestController
@RequestMapping("/api/admin/jobs")
public class AdminJobController {

    private final FxUpdater fx;
    private final QuoteService quotes;
    private final MonthlyReportService monthly;

    public AdminJobController(FxUpdater fx, QuoteService quotes, MonthlyReportService monthly) {
        this.fx = fx;
        this.quotes = quotes;
        this.monthly = monthly;
    }

    @PostMapping("/{name}")
    public Object run(@RequestAttribute(AuthFilter.USER) User admin, @PathVariable String name) {
        UserService.requireAdmin(admin);
        return switch (name) {
            case "fx" -> fx.update();
            case "quotes" -> quotes.refresh(true);
            case "monthly-report" -> monthly.sendAll(java.time.YearMonth.from(com.doaccounts.service.Dates.today()).minusMonths(1).toString());
            default -> throw new ResponseStatusException(HttpStatus.NOT_FOUND, "沒有這個排程：" + name);
        };
    }
}
