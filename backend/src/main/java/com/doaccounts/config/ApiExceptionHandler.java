package com.doaccounts.config;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 資料庫層的例外轉成中文 Problem Details（訊息在 detail）。
 * 業務錯誤由 service 直接丟 ResponseStatusException，不經過這裡。
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    /** 樂觀鎖：載入後被別處修改（SD ADR-13） */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    ProblemDetail optimisticLock() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "此筆紀錄已在其他地方被修改，請重新整理");
    }

    @ExceptionHandler(DuplicateKeyException.class)
    ProblemDetail duplicate() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "資料重複，請重新整理後再試");
    }
}
