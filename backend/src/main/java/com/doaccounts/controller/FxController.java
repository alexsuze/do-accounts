package com.doaccounts.controller;

import com.doaccounts.config.AuthFilter;
import com.doaccounts.model.ExchangeRate;
import com.doaccounts.model.User;
import com.doaccounts.service.FxService;
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

    private final FxService service;

    public FxController(FxService service) {
        this.service = service;
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
