package com.doaccounts.controller;

import com.doaccounts.config.AuthFilter;
import com.doaccounts.model.User;
import com.doaccounts.service.BackupService;
import com.doaccounts.service.Dates;
import com.doaccounts.service.ExportService;
import com.doaccounts.service.TransactionService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.nio.charset.StandardCharsets;
import java.util.List;

/** 匯出 API（SD 3.10）：交易 CSV（篩選條件與明細列表相同）、完整 JSON 備份。 */
@RestController
@RequestMapping("/api/export")
public class ExportController {

    private final TransactionService transactions;
    private final ExportService export;
    private final BackupService backup;

    public ExportController(TransactionService transactions, ExportService export, BackupService backup) {
        this.transactions = transactions;
        this.export = export;
        this.backup = backup;
    }

    /** 舊到新寫出，方便在 Excel 依日期閱讀 */
    @GetMapping("/transactions")
    public ResponseEntity<StreamingResponseBody> csv(@RequestAttribute(AuthFilter.USER) User user,
                                                     @RequestParam(required = false) String from,
                                                     @RequestParam(required = false) String to,
                                                     @RequestParam(name = "kind", required = false) List<String> kinds,
                                                     @RequestParam(name = "account_id", required = false) List<String> accountIds,
                                                     @RequestParam(name = "category_id", required = false) List<String> categoryIds,
                                                     @RequestParam(name = "tag", required = false) List<String> tags,
                                                     @RequestParam(required = false) String q) {
        var list = transactions.search(user.id, from, to, kinds, accountIds, categoryIds, tags, q).reversed();
        String name = "do-accounts-" + (from != null && to != null ? from + "_" + to : Dates.today().toString()) + ".csv";
        StreamingResponseBody body = out -> export.writeCsv(user.id, list, out);
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(name, StandardCharsets.UTF_8).build().toString())
                .body(body);
    }

    @GetMapping("/backup")
    public ResponseEntity<BackupService.Backup> backup(@RequestAttribute(AuthFilter.USER) User user) {
        String name = "do-accounts-backup-" + Dates.today() + ".json";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(name, StandardCharsets.UTF_8).build().toString())
                .body(backup.export(user));
    }
}
