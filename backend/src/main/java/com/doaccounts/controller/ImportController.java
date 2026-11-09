package com.doaccounts.controller;

import com.doaccounts.config.AuthFilter;
import com.doaccounts.model.ImportBatch;
import com.doaccounts.model.ImportTemplate;
import com.doaccounts.model.User;
import com.doaccounts.service.BackupService;
import com.doaccounts.service.ImportService;
import com.doaccounts.service.ImportService.ImportRequest;
import com.doaccounts.service.ImportService.Preview;
import com.doaccounts.service.ImportService.TemplateRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 匯入 API（SD 3.10）。CSV 由前端解析成資料列後送來（ADR-17）。 */
@RestController
@RequestMapping("/api/import")
public class ImportController {

    private final ImportService service;
    private final BackupService backup;

    public ImportController(ImportService service, BackupService backup) {
        this.service = service;
        this.backup = backup;
    }

    @PostMapping("/transactions/preview")
    public Preview preview(@RequestAttribute(AuthFilter.USER) User user, @RequestBody ImportRequest req) {
        return service.preview(user.id, req);
    }

    @PostMapping("/transactions/commit")
    public ImportBatch commit(@RequestAttribute(AuthFilter.USER) User user, @RequestBody ImportRequest req) {
        return service.commit(user.id, req);
    }

    @GetMapping("/batches")
    public List<ImportBatch> batches(@RequestAttribute(AuthFilter.USER) User user) {
        return service.batches(user.id);
    }

    @PostMapping("/batches/{id}/revert")
    public ImportBatch revert(@RequestAttribute(AuthFilter.USER) User user, @PathVariable String id) {
        return service.revert(user.id, id);
    }

    @GetMapping("/templates")
    public List<ImportTemplate> templates(@RequestAttribute(AuthFilter.USER) User user) {
        return service.templates(user.id);
    }

    @PostMapping("/templates")
    public ImportTemplate createTemplate(@RequestAttribute(AuthFilter.USER) User user, @RequestBody TemplateRequest req) {
        return service.saveTemplate(user.id, null, req);
    }

    @PutMapping("/templates/{id}")
    public ImportTemplate updateTemplate(@RequestAttribute(AuthFilter.USER) User user, @PathVariable String id, @RequestBody TemplateRequest req) {
        return service.saveTemplate(user.id, id, req);
    }

    @DeleteMapping("/templates/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTemplate(@RequestAttribute(AuthFilter.USER) User user, @PathVariable String id) {
        service.deleteTemplate(user.id, id);
    }

    /** 從 JSON 備份還原（只限還沒有任何交易的帳號） */
    @PostMapping("/backup")
    public BackupService.RestoreResult restore(@RequestAttribute(AuthFilter.USER) User user, @RequestBody BackupService.Backup body) {
        return backup.restore(user, body);
    }
}
