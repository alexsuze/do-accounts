package com.doaccounts.controller;

import com.doaccounts.config.AuthFilter;
import com.doaccounts.model.Budget;
import com.doaccounts.model.User;
import com.doaccounts.service.BudgetService;
import com.doaccounts.service.BudgetService.BudgetRequest;
import com.doaccounts.service.BudgetService.BudgetView;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 預算 API（SD 3.6）。category_id 省略或空字串 = 當月總預算。 */
@RestController
@RequestMapping("/api/budgets")
public class BudgetController {

    private final BudgetService service;

    public BudgetController(BudgetService service) {
        this.service = service;
    }

    @GetMapping
    public BudgetView view(@RequestAttribute(AuthFilter.USER) User user, @RequestParam String month) {
        return service.view(user, month);
    }

    @PutMapping
    public Budget upsert(@RequestAttribute(AuthFilter.USER) User user, @RequestBody BudgetRequest req) {
        return service.upsert(user, req);
    }

    /** 從這個月起移除（之前的循環預算不會再沿用到這個月以後） */
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@RequestAttribute(AuthFilter.USER) User user, @RequestParam String month,
                       @RequestParam(name = "category_id", required = false) String categoryId) {
        service.delete(user, month, categoryId);
    }

    /** body：{ from_month, to_month } → { copied } */
    @PostMapping("/copy")
    public Map<String, Integer> copy(@RequestAttribute(AuthFilter.USER) User user, @RequestBody Map<String, String> body) {
        return Map.of("copied", service.copy(user, body.get("from_month"), body.get("to_month")));
    }
}
