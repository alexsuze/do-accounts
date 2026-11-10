package com.doaccounts.controller;

import com.doaccounts.config.AuthFilter;
import com.doaccounts.model.ExchangeRate;
import com.doaccounts.model.User;
import com.doaccounts.service.FxService;
import com.doaccounts.service.FxUpdater;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.server.ResponseStatusException;
import com.doaccounts.service.FxService.LatestRate;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

/** 匯率 API（SD 3.8）。匯率一律為「1 幣別 = ? TWD」。 */
@RestController
@RequestMapping("/api/fx")
public class FxController {

    /** 「立即更新」的最短間隔（全站共用一份匯率，避免一直打外部 API） */
    private static final long REFRESH_INTERVAL_MS = 5 * 60_000L;

    private final FxService service;
    private final FxUpdater updater;
    private volatile long lastRefresh;

    public FxController(FxService service, FxUpdater updater) {
        this.service = service;
        this.updater = updater;
    }

    /** 立即重新抓取匯率（與排程相同；5 分鐘內只能一次，否則 429） */
    @PostMapping("/refresh")
    public FxUpdater.Result refresh() {
        long now = System.currentTimeMillis();
        if (now - lastRefresh < REFRESH_INTERVAL_MS) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "匯率剛更新過，請 5 分鐘後再試");
        }
        lastRefresh = now;
        return updater.update();
    }

    @GetMapping("/latest")
    public List<LatestRate> latest(@RequestAttribute(AuthFilter.USER) User user) {
        return service.latestRates(user);
    }

    @GetMapping("/history")
    public List<LatestRate> history(@RequestAttribute(AuthFilter.USER) User user, @RequestParam String currency,
                                    @RequestParam String from, @RequestParam String to) {
        return service.history(user, currency, from, to);
    }

    @GetMapping("/manual")
    public List<ExchangeRate> manual(@RequestAttribute(AuthFilter.USER) User user) {
        return service.manual(user.id);
    }

    public record ManualRequest(String date, String currency, BigDecimal rate) {}

    @PutMapping("/manual")
    public ExchangeRate setManual(@RequestAttribute(AuthFilter.USER) User user, @RequestBody ManualRequest req) {
        return service.setManual(user.id, req.date(), req.currency(), req.rate());
    }

    @DeleteMapping("/manual/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteManual(@RequestAttribute(AuthFilter.USER) User user, @PathVariable String id) {
        service.deleteManual(user.id, id);
    }
}
