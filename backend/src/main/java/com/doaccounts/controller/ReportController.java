package com.doaccounts.controller;

import com.doaccounts.config.AuthFilter;
import com.doaccounts.model.User;
import com.doaccounts.service.DashboardService;
import com.doaccounts.service.DashboardService.Dashboard;
import com.doaccounts.service.ReportService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 報表 API（SD 3.7）。 */
@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final DashboardService dashboard;
    private final ReportService reports;

    public ReportController(DashboardService dashboard, ReportService reports) {
        this.dashboard = dashboard;
        this.reports = reports;
    }

    @GetMapping("/dashboard")
    public Dashboard dashboard(@RequestAttribute(AuthFilter.USER) User user) {
        return dashboard.get(user);
    }

    /** months：6、12…；0 = 全部期間 */
    @GetMapping("/net-worth")
    public ReportService.NetWorth netWorth(@RequestAttribute(AuthFilter.USER) User user, @RequestParam(defaultValue = "12") int months) {
        return reports.netWorth(user, months);
    }

    /** kind：expense（預設）/ income */
    @GetMapping("/breakdown")
    public ReportService.Breakdown breakdown(@RequestAttribute(AuthFilter.USER) User user, @RequestParam String from,
                                             @RequestParam String to, @RequestParam(defaultValue = "expense") String kind) {
        return reports.breakdown(user, from, to, kind);
    }

    @GetMapping("/daily")
    public ReportService.Daily daily(@RequestAttribute(AuthFilter.USER) User user, @RequestParam String month) {
        return reports.daily(user, month);
    }

    @GetMapping("/category-trend")
    public ReportService.CategoryTrend categoryTrend(@RequestAttribute(AuthFilter.USER) User user,
                                                     @RequestParam("category_id") String categoryId,
                                                     @RequestParam(defaultValue = "12") int months) {
        return reports.categoryTrend(user, categoryId, months);
    }
}
