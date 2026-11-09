package com.doaccounts.controller;

import com.doaccounts.config.AuthFilter;
import com.doaccounts.model.User;
import com.doaccounts.service.DashboardService;
import com.doaccounts.service.DashboardService.Dashboard;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 報表 API（SD 3.7）。階段 1 只有總覽。 */
@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final DashboardService dashboard;

    public ReportController(DashboardService dashboard) {
        this.dashboard = dashboard;
    }

    @GetMapping("/dashboard")
    public Dashboard dashboard(@RequestAttribute(AuthFilter.USER) User user) {
        return dashboard.get(user);
    }
}
