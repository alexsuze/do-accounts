package com.doaccounts.controller;

import com.doaccounts.config.AuthFilter;
import com.doaccounts.model.Account;
import com.doaccounts.model.User;
import com.doaccounts.service.AccountService;
import com.doaccounts.service.AccountService.AccountRequest;
import com.doaccounts.service.TransactionService;
import com.doaccounts.service.TransactionService.AccountLedger;
import com.doaccounts.service.TransactionService.DayBalance;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 帳戶 API（SD 3.3），只限目前登入者自己的帳戶。 */
@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    private final AccountService service;
    private final TransactionService transactions;

    public AccountController(AccountService service, TransactionService transactions) {
        this.service = service;
        this.transactions = transactions;
    }

    @GetMapping
    public List<Account> list(@RequestAttribute(AuthFilter.USER) User user,
                              @RequestParam(name = "include_archived", defaultValue = "false") boolean includeArchived) {
        return service.list(user.id, includeArchived);
    }

    @PostMapping
    public Account create(@RequestAttribute(AuthFilter.USER) User user, @RequestBody AccountRequest req) {
        return service.create(user.id, req);
    }

    public record ReorderRequest(String kind, List<String> ids) {}

    /** 放在 /{id} 之前 */
    @PutMapping("/reorder")
    public List<Account> reorder(@RequestAttribute(AuthFilter.USER) User user, @RequestBody ReorderRequest req) {
        return service.reorder(user.id, req.kind(), req.ids());
    }

    @GetMapping("/{id}")
    public Account get(@RequestAttribute(AuthFilter.USER) User user, @PathVariable String id) {
        return service.getWithBalance(user.id, id);
    }

    @PutMapping("/{id}")
    public Account update(@RequestAttribute(AuthFilter.USER) User user, @PathVariable String id, @RequestBody AccountRequest req) {
        return service.update(user.id, id, req);
    }

    @DeleteMapping("/{id}")
    public void delete(@RequestAttribute(AuthFilter.USER) User user, @PathVariable String id) {
        service.delete(user.id, id);
    }

    @PostMapping("/{id}/archive")
    public Account archive(@RequestAttribute(AuthFilter.USER) User user, @PathVariable String id) {
        return service.setArchived(user.id, id, true);
    }

    @PostMapping("/{id}/unarchive")
    public Account unarchive(@RequestAttribute(AuthFilter.USER) User user, @PathVariable String id) {
        return service.setArchived(user.id, id, false);
    }

    /** 此帳戶的交易與交易後餘額（帳戶詳細頁） */
    @GetMapping("/{id}/ledger")
    public AccountLedger ledger(@RequestAttribute(AuthFilter.USER) User user, @PathVariable String id,
                                @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
        return transactions.accountLedger(user.id, id, page, size);
    }

    /** 最近 days 天的每日餘額 */
    @GetMapping("/{id}/history")
    public List<DayBalance> history(@RequestAttribute(AuthFilter.USER) User user, @PathVariable String id,
                                    @RequestParam(defaultValue = "90") int days) {
        return transactions.dailyBalances(user.id, id, days);
    }
}
