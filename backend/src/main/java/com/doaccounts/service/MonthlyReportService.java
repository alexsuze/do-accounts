package com.doaccounts.service;

import com.doaccounts.config.AppProperties;
import com.doaccounts.model.Category;
import com.doaccounts.model.User;
import com.doaccounts.service.BudgetService.BudgetItem;
import com.doaccounts.service.ReportService.NetWorthPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.HtmlUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 每月報表 Email（SD 4.9、階段 5）：每月 1 日 08:07（台北）寄上個月的收支、支出前五名、預算達成與淨資產變化。
 * 使用者在設定頁開啟；寄信透過 Resend（{@link EmailService}），沒有設定 RESEND_API_KEY 時排程什麼都不做。
 */
@Service
public class MonthlyReportService {

    private static final Logger log = LoggerFactory.getLogger(MonthlyReportService.class);
    static final int TOP = 5;

    private final ReportService reports;
    private final BudgetService budgets;
    private final CategoryService categories;
    private final UserService users;
    private final EmailService email;
    private final AppProperties props;

    public MonthlyReportService(ReportService reports, BudgetService budgets, CategoryService categories, UserService users,
                                EmailService email, AppProperties props) {
        this.reports = reports;
        this.budgets = budgets;
        this.categories = categories;
        this.users = users;
        this.email = email;
        this.props = props;
    }

    public record Line(String name, BigDecimal amount, BigDecimal percentage) {}

    /** 一個月的報表內容（本位幣） */
    public record Report(String month, String baseCurrency, BigDecimal income, BigDecimal expense, BigDecimal net,
                         List<Line> topExpenses, BudgetItem budget, BigDecimal netWorth, BigDecimal netWorthChange,
                         BigDecimal investments) {}

    public record Mail(String subject, String html, String text) {}

    public Report build(User user, String month) {
        YearMonth m = BudgetService.parseMonth(month);
        String from = m.atDay(1).toString();
        String to = m.atEndOfMonth().toString();
        ReportService.Daily daily = reports.daily(user, m.toString());
        ReportService.Breakdown mix = reports.breakdown(user, from, to, Category.EXPENSE);
        Map<String, String> names = new HashMap<>();
        List<Category> cats = categories.list(user.id);
        for (Category c : cats) names.put(c.id, c.name);
        List<Line> top = mix.items().stream().limit(TOP)
                .map(i -> new Line(i.categoryId() == null ? "未分類" : names.getOrDefault(i.categoryId(), "（已刪除的分類）"), i.amount(), i.percentage()))
                .toList();
        BudgetItem budget = budgets.view(user, m.toString()).total();

        BigDecimal nw = null, change = null, invest = null;
        List<NetWorthPoint> points = reports.netWorth(user, 0).points();
        for (int i = 0; i < points.size(); i++) {
            if (!points.get(i).month().equals(m.toString())) continue;
            nw = points.get(i).netWorth();
            invest = points.get(i).investments();
            change = i > 0 ? nw.subtract(points.get(i - 1).netWorth()) : null;
        }
        return new Report(m.toString(), daily.baseCurrency(), daily.income(), daily.expense(), daily.income().subtract(daily.expense()),
                top, budget, nw, change, invest);
    }

    /** Email 內容（HTML 用行內樣式，深淺色信箱都看得清楚） */
    public Mail render(Report r) {
        String cur = r.baseCurrency();
        String monthLabel = r.month().substring(0, 4) + " 年 " + Integer.parseInt(r.month().substring(5)) + " 月";
        String subject = "do-accounts " + monthLabel + "月報：支出 " + money(r.expense(), cur) + "，結餘 " + signed(r.net(), cur);
        String link = props.frontendUrl() == null ? "" : props.frontendUrl();

        StringBuilder h = new StringBuilder();
        h.append("<div style=\"font-family:-apple-system,'Segoe UI','Noto Sans TC',sans-serif;max-width:560px;margin:0 auto;padding:24px;color:#0F172A;background:#FFFFFF\">");
        h.append("<div style=\"font-size:12px;letter-spacing:.12em;color:#475569\">DO-ACCOUNTS · 每月報表</div>");
        h.append("<h1 style=\"margin:4px 0 20px;font-size:22px\">").append(monthLabel).append("</h1>");
        h.append("<table style=\"width:100%;border-collapse:collapse;margin-bottom:20px\"><tr>");
        cell(h, "收入", "+" + money(r.income(), cur), "#047857");
        cell(h, "支出", "−" + money(r.expense(), cur), "#BE123C");
        cell(h, "結餘", signed(r.net(), cur), r.net().signum() >= 0 ? "#047857" : "#BE123C");
        h.append("</tr></table>");
        if (!r.topExpenses().isEmpty()) {
            h.append("<h2 style=\"font-size:15px;margin:0 0 8px\">支出前 ").append(r.topExpenses().size()).append(" 名</h2>");
            h.append("<table style=\"width:100%;border-collapse:collapse;margin-bottom:20px;font-size:14px\">");
            for (Line l : r.topExpenses()) {
                h.append("<tr><td style=\"padding:6px 0;border-bottom:1px solid #DDE4EE\">").append(HtmlUtils.htmlEscape(l.name())).append("</td>")
                        .append("<td style=\"padding:6px 0;border-bottom:1px solid #DDE4EE;text-align:right;color:#475569\">").append(l.percentage()).append("%</td>")
                        .append("<td style=\"padding:6px 0 6px 12px;border-bottom:1px solid #DDE4EE;text-align:right;font-family:monospace\">").append(money(l.amount(), cur)).append("</td></tr>");
            }
            h.append("</table>");
        }
        if (r.budget() != null) {
            BudgetItem b = r.budget();
            boolean over = b.percentage().compareTo(BigDecimal.valueOf(100)) > 0;
            h.append("<p style=\"font-size:14px;margin:0 0 8px\">").append(b.derived() ? "分類預算合計" : "總預算").append(" ")
                    .append(money(b.amount(), cur)).append("，已使用 <b style=\"color:").append(over ? "#BE123C" : "#047857").append("\">")
                    .append(b.percentage()).append("%</b>").append(over ? "（超支 " + money(b.spent().subtract(b.amount()), cur) + "）" : "").append("</p>");
        }
        if (r.netWorth() != null) {
            h.append("<p style=\"font-size:14px;margin:0 0 8px\">月底淨資產 <b>").append(money(r.netWorth(), cur)).append("</b>");
            if (r.netWorthChange() != null) h.append("，比上月 ").append(signed(r.netWorthChange(), cur));
            if (r.investments() != null && r.investments().signum() != 0) h.append("（其中投資市值 ").append(money(r.investments(), cur)).append("）");
            h.append("</p>");
        }
        if (!link.isBlank()) {
            h.append("<p style=\"margin:24px 0 0\"><a href=\"").append(HtmlUtils.htmlEscape(link)).append("/#/reports\" style=\"display:inline-block;padding:10px 18px;border-radius:8px;background:#0E7490;color:#FFFFFF;text-decoration:none\">查看完整報表</a></p>");
        }
        h.append("<p style=\"margin:24px 0 0;font-size:12px;color:#475569\">金額以 ").append(cur)
                .append(" 計，外幣依交易當天匯率換算。不想收到這封信，可以在設定頁關閉「每月報表 Email」。</p></div>");

        StringBuilder t = new StringBuilder();
        t.append("do-accounts 每月報表 · ").append(monthLabel).append("\n\n")
                .append("收入 +").append(money(r.income(), cur)).append("\n支出 −").append(money(r.expense(), cur))
                .append("\n結餘 ").append(signed(r.net(), cur)).append("\n");
        if (!r.topExpenses().isEmpty()) {
            t.append("\n支出前 ").append(r.topExpenses().size()).append(" 名\n");
            for (Line l : r.topExpenses()) t.append("- ").append(l.name()).append(" ").append(money(l.amount(), cur)).append("（").append(l.percentage()).append("%）\n");
        }
        if (r.budget() != null) t.append("\n預算 ").append(money(r.budget().amount(), cur)).append("，已使用 ").append(r.budget().percentage()).append("%\n");
        if (r.netWorth() != null) {
            t.append("\n月底淨資產 ").append(money(r.netWorth(), cur));
            if (r.netWorthChange() != null) t.append("，比上月 ").append(signed(r.netWorthChange(), cur));
            t.append("\n");
        }
        if (!link.isBlank()) t.append("\n查看完整報表：").append(link).append("/#/reports\n");
        return new Mail(subject, h.toString(), t.toString());
    }

    public Mail preview(User user, String month) {
        return render(build(user, month));
    }

    /** 寄某個月的報表給這位使用者 */
    public void send(User user, String month) {
        if (!email.configured()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "伺服器尚未設定寄信（RESEND_API_KEY）");
        Mail mail = preview(user, month);
        try {
            email.send(user.email, mail.subject(), mail.html(), mail.text());
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "寄信失敗：" + e.getMessage());
        }
    }

    public record RunResult(int sent, int failed, List<String> errors) {}

    @Scheduled(cron = "0 7 8 1 * *", zone = "Asia/Taipei")
    public void scheduled() {
        sendAll(YearMonth.from(Dates.today()).minusMonths(1).toString());
    }

    /** 寄給所有開啟每月報表的使用者；失敗的再試一次（NFR-7） */
    public RunResult sendAll(String month) {
        if (!email.configured()) {
            log.info("沒有設定 RESEND_API_KEY，略過每月報表");
            return new RunResult(0, 0, List.of("伺服器尚未設定寄信（RESEND_API_KEY）"));
        }
        int sent = 0;
        List<String> errors = new ArrayList<>();
        for (User u : users.monthlyReportSubscribers()) {
            String err = null;
            for (int attempt = 1; attempt <= 2; attempt++) {
                try {
                    send(u, month);
                    err = null;
                    break;
                } catch (RuntimeException e) {
                    err = e.getMessage();
                    log.warn("每月報表寄給 {} 失敗（第 {} 次）：{}", u.id, attempt, err);
                }
            }
            if (err == null) sent++;
            else errors.add(u.id + "：" + err);
        }
        log.info("每月報表 {}：寄出 {} 封，失敗 {} 封", month, sent, errors.size());
        return new RunResult(sent, errors.size(), errors);
    }

    private static void cell(StringBuilder h, String label, String value, String color) {
        h.append("<td style=\"padding:12px;background:#F7F9FC;border:1px solid #DDE4EE\"><div style=\"font-size:12px;color:#475569\">")
                .append(label).append("</div><div style=\"font-size:20px;font-family:monospace;color:").append(color).append("\">")
                .append(value).append("</div></td>");
    }

    /** 千分位、依幣別小數位數、不帶正負號 */
    static String money(BigDecimal v, String currency) {
        int digits = Currencies.digits(currency);
        DecimalFormat f = new DecimalFormat(digits == 0 ? "#,##0" : "#,##0." + "0".repeat(digits));
        f.setRoundingMode(RoundingMode.HALF_UP);
        return f.format(v.abs());
    }

    static String signed(BigDecimal v, String currency) {
        return (v.signum() < 0 ? "−" : v.signum() > 0 ? "+" : "") + money(v, currency);
    }
}
