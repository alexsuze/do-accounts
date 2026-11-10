package com.doaccounts.controller;

import com.doaccounts.config.AuthFilter;
import com.doaccounts.model.User;
import com.doaccounts.service.DashboardService;
import com.doaccounts.service.DashboardService.Dashboard;
import com.doaccounts.service.ReportService;
import com.doaccounts.service.Dates;
import com.doaccounts.service.MonthlyReportService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.server.ResponseStatusException;
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
    private final MonthlyReportService monthly;
    /** 測試信：每位使用者 1 分鐘一封 */
    private final java.util.Map<String, Long> lastTest = new java.util.concurrent.ConcurrentHashMap<>();

    public ReportController(DashboardService dashboard, ReportService reports, MonthlyReportService monthly) {
        this.dashboard = dashboard;
        this.reports = reports;
        this.monthly = monthly;
    }

    /** 每月報表 Email 的內容預覽（不寄出）；month 省略為上個月 */
    @GetMapping("/monthly-email/preview")
    public MonthlyReportService.Mail monthlyPreview(@RequestAttribute(AuthFilter.USER) User user, @RequestParam(required = false) String month) {
        return monthly.preview(user, month == null ? lastMonth() : month);
    }

    /** 立刻寄一封上個月的報表給自己（測試用） */
    @PostMapping("/monthly-email/test")
    public java.util.Map<String, String> monthlyTest(@RequestAttribute(AuthFilter.USER) User user) {
        long now = System.currentTimeMillis();
        Long last = lastTest.get(user.id);
        if (last != null && now - last < 60_000) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "剛寄過測試信，請 1 分鐘後再試");
        }
        lastTest.put(user.id, now);
        String month = lastMonth();
        monthly.send(user, month);
        return java.util.Map.of("month", month, "to", user.email);
    }

    private static String lastMonth() {
        return java.time.YearMonth.from(Dates.today()).minusMonths(1).toString();
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
