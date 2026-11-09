package com.doaccounts.service;

import org.springframework.http.HttpStatusCode;
import org.springframework.web.server.ResponseStatusException;

/**
 * 帶欄位名稱的業務錯誤（snake_case，與 API 欄位一致），多筆編輯時用來把錯誤標到對應的格子（SD 4.2.1）。
 * 單筆 API 照常回 Problem Details（訊息在 detail）。
 */
public class FieldException extends ResponseStatusException {

    private final String field;

    public FieldException(HttpStatusCode status, String field, String reason) {
        super(status, reason);
        this.field = field;
    }

    public String getField() {
        return field;
    }

    /** 把一般的 ResponseStatusException 補上欄位名稱 */
    static FieldException of(String field, ResponseStatusException e) {
        return e instanceof FieldException fe ? fe : new FieldException(e.getStatusCode(), field, e.getReason());
    }
}
