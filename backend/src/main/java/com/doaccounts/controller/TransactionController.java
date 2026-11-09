package com.doaccounts.controller;

import com.doaccounts.config.AuthFilter;
import com.doaccounts.model.Transaction;
import com.doaccounts.model.User;
import com.doaccounts.service.TransactionService;
import com.doaccounts.service.TransactionService.Page;
import com.doaccounts.service.TransactionService.Suggestion;
import com.doaccounts.service.TransactionService.TransactionRequest;
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
import java.util.Map;

/** 交易 API（SD 3.4），只限目前登入者自己的交易。可複選的篩選參數以逗號分隔。 */
@RestController
@RequestMapping("/api/transactions")
public class TransactionController {

    private final TransactionService service;

    public TransactionController(TransactionService service) {
        this.service = service;
    }

    @GetMapping
    public Page list(@RequestAttribute(AuthFilter.USER) User user,
                     @RequestParam(required = false) String from,
                     @RequestParam(required = false) String to,
                     @RequestParam(name = "kind", required = false) List<String> kinds,
                     @RequestParam(name = "account_id", required = false) List<String> accountIds,
                     @RequestParam(name = "category_id", required = false) List<String> categoryIds,
                     @RequestParam(name = "tag", required = false) List<String> tags,
                     @RequestParam(required = false) String q,
                     @RequestParam(defaultValue = "0") int page,
                     @RequestParam(defaultValue = "50") int size) {
        return service.list(user.id, from, to, kinds, accountIds, categoryIds, tags, q, page, size);
    }

    @PostMapping
    public Transaction create(@RequestAttribute(AuthFilter.USER) User user, @RequestBody TransactionRequest req) {
        return service.create(user.id, req);
    }

    @GetMapping("/suggest")
    public List<Suggestion> suggest(@RequestAttribute(AuthFilter.USER) User user, @RequestParam String q) {
        return service.suggest(user.id, q);
    }

    public record BulkDeleteRequest(List<String> ids) {}

    @PostMapping("/bulk-delete")
    public Map<String, Integer> bulkDelete(@RequestAttribute(AuthFilter.USER) User user, @RequestBody BulkDeleteRequest req) {
        return Map.of("deleted", service.bulkDelete(user.id, req.ids()));
    }

    public record BulkUpdateRequest(List<String> ids, String categoryId, List<String> addTags, List<String> removeTags) {}

    @PostMapping("/bulk-update")
    public Map<String, Integer> bulkUpdate(@RequestAttribute(AuthFilter.USER) User user, @RequestBody BulkUpdateRequest req) {
        return Map.of("updated", service.bulkUpdate(user.id, req.ids(), req.categoryId(), req.addTags(), req.removeTags()));
    }

    @GetMapping("/{id}")
    public Transaction get(@RequestAttribute(AuthFilter.USER) User user, @PathVariable String id) {
        return service.get(user.id, id);
    }

    @PutMapping("/{id}")
    public Transaction update(@RequestAttribute(AuthFilter.USER) User user, @PathVariable String id, @RequestBody TransactionRequest req) {
        return service.update(user.id, id, req);
    }

    @DeleteMapping("/{id}")
    public void delete(@RequestAttribute(AuthFilter.USER) User user, @PathVariable String id) {
        service.delete(user.id, id);
    }
}
