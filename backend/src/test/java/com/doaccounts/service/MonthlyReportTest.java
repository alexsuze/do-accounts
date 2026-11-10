package com.doaccounts.service;

import com.doaccounts.config.AppProperties;
import com.doaccounts.model.Account;
import com.doaccounts.model.Category;
import com.doaccounts.model.User;
import com.doaccounts.service.BudgetService.BudgetRequest;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

import static com.doaccounts.service.Fixture.ME;
import static com.doaccounts.service.Fixture.expense;
import static com.doaccounts.service.Fixture.income;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 每月報表 Email：內容、HTML 跳脫、寄送與重試（階段 5）。 */
class MonthlyReportTest {

    private final Fixture f = new Fixture();
    private final EmailService email = mock(EmailService.class);
    private final UserService users = mock(UserService.class);
    private final AppProperties props = new AppProperties(null, null, "https://app.example.test", null);
    private final MonthlyReportService service = new MonthlyReportService(f.reportService, f.budgetService, f.categoryService, users, email, props);
    private final String month = YearMonth.from(Dates.today()).minusMonths(1).toString();
    private final Category food;

    MonthlyReportTest() {
        f.categoryService.ensureDefaults(ME);
        food = f.categories.stream().filter(c -> c.name.equals("餐飲")).findFirst().orElseThrow();
        f.me.email = "me@example.test";
        Account cash = f.account(ME, "錢包", "cash", "TWD", "1000", "2020-01-01");
        String d = month + "-05";
        f.txService.create(ME, income(cash.id, f.categories.stream().filter(c -> c.name.equals("薪資")).findFirst().orElseThrow().id, "50000", d));
        f.txService.create(ME, expense(cash.id, food.id, "1200", d));
        f.budgetService.upsert(f.me, new BudgetRequest(month, null, new BigDecimal("1000"), false));
    }

    @Test
    void buildsAndRendersReport() {
        var r = service.build(f.me, month);
        assertEquals(0, new BigDecimal("50000").compareTo(r.income()));
        assertEquals(0, new BigDecimal("48800").compareTo(r.net()));
        assertEquals("餐飲", r.topExpenses().getFirst().name());
        assertEquals(0, new BigDecimal("120.0").compareTo(r.budget().percentage()));
        assertEquals(0, new BigDecimal("49800").compareTo(r.netWorth()));

        var mail = service.render(r);
        assertTrue(mail.subject().contains("支出 1,200"), mail.subject());
        assertTrue(mail.subject().contains("結餘 +48,800"));
        assertTrue(mail.html().contains("超支 200"));
        assertTrue(mail.html().contains("https://app.example.test/#/reports"));
        assertTrue(mail.text().contains("餐飲 1,200"));
    }

    @Test
    void categoryNamesAreEscaped() {
        food.name = "<b>餐飲</b>";
        var mail = service.render(service.build(f.me, month));
        assertFalse(mail.html().contains("<b>餐飲</b>"));
        assertTrue(mail.html().contains("&lt;b&gt;"));
    }

    @Test
    void sendAllSkipsWithoutResendAndRetriesOnce() {
        when(email.configured()).thenReturn(false);
        assertEquals(0, service.sendAll(month).sent());
        assertThrows(ResponseStatusException.class, () -> service.send(f.me, month));
        verify(email, never()).send(anyString(), anyString(), anyString(), anyString());

        when(email.configured()).thenReturn(true);
        when(users.monthlyReportSubscribers()).thenReturn(List.of(f.me));
        when(email.send(anyString(), anyString(), anyString(), anyString())).thenThrow(new IllegalStateException("暫時失敗")).thenReturn("id-1");
        var r = service.sendAll(month);
        assertEquals(1, r.sent());
        verify(email, times(2)).send(anyString(), anyString(), anyString(), anyString());

        User other = Fixture.user("u9");
        other.email = "x@example.test";
        when(users.monthlyReportSubscribers()).thenReturn(List.of(other));
        when(email.send(anyString(), anyString(), anyString(), anyString())).thenThrow(new IllegalStateException("壞掉"));
        var failed = service.sendAll(month);
        assertEquals(1, failed.failed());
    }

    @Test
    void moneyFormatting() {
        assertEquals("1,234,568", MonthlyReportService.money(new BigDecimal("1234567.5"), "TWD"));
        assertEquals("12.30", MonthlyReportService.money(new BigDecimal("-12.3"), "USD"));
        assertEquals("−5", MonthlyReportService.signed(new BigDecimal("-5"), "TWD"));
    }
}
