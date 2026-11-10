package com.doaccounts.controller;

import com.doaccounts.config.AuthFilter;
import com.doaccounts.model.Price;
import com.doaccounts.model.Security;
import com.doaccounts.model.Transaction;
import com.doaccounts.model.User;
import com.doaccounts.service.InvestmentService;
import com.doaccounts.service.InvestmentService.Holding;
import com.doaccounts.service.InvestmentService.SecurityRequest;
import com.doaccounts.service.InvestmentService.Summary;
import com.doaccounts.service.InvestmentService.TradePage;
import com.doaccounts.service.InvestmentService.TradeRequest;
import com.doaccounts.service.QuoteService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;

/** 投資 API（SD 3.9）。 */
@RestController
@RequestMapping("/api/investments")
public class InvestmentController {

    /** 「更新報價」按鈕的最短間隔，避免一直打交易所的 API */
    private static final long REFRESH_INTERVAL_MS = 5 * 60_000L;

    private final InvestmentService service;
    private final QuoteService quotes;
    private volatile long lastRefresh;

    public InvestmentController(InvestmentService service, QuoteService quotes) {
        this.service = service;
        this.quotes = quotes;
    }

    @GetMapping("/securities")
    public List<Security> securities(@RequestParam(required = false) String q, @RequestParam(required = false) String market) {
        return service.searchSecurities(q, market);
    }

    @GetMapping("/securities/{id}")
    public Security security(@PathVariable String id) {
        return service.security(id);
    }

    /** K 線圖資料；months 預設 6 */
    @GetMapping("/securities/{id}/chart")
    public InvestmentService.Chart chart(@RequestAttribute(AuthFilter.USER) User user, @PathVariable String id,
                                         @RequestParam(name = "account_id", required = false) String accountId,
                                         @RequestParam(defaultValue = "6") int months) {
        return service.chart(user, id, accountId, months);
    }

    @PostMapping("/securities")
    public Security createSecurity(@RequestBody SecurityRequest req) {
        return service.createSecurity(req);
    }

    @GetMapping("/holdings")
    public List<Holding> holdings(@RequestAttribute(AuthFilter.USER) User user, @RequestParam(name = "account_id", required = false) String accountId) {
        return service.holdings(user, accountId);
    }

    @GetMapping("/summary")
    public Summary summary(@RequestAttribute(AuthFilter.USER) User user, @RequestParam(name = "account_id", required = false) String accountId) {
        return service.summary(user, accountId);
    }

    @GetMapping("/trades")
    public TradePage trades(@RequestAttribute(AuthFilter.USER) User user,
                            @RequestParam(name = "account_id", required = false) String accountId,
                            @RequestParam(name = "security_id", required = false) String securityId,
                            @RequestParam(required = false) String from, @RequestParam(required = false) String to,
                            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
        if (size > 200) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "每頁筆數需為 1～200");
        return service.trades(user.id, accountId, securityId, from, to, page, size);
    }

    @GetMapping("/trades/{id}")
    public Transaction trade(@RequestAttribute(AuthFilter.USER) User user, @PathVariable String id) {
        return service.trade(user.id, id);
    }

    @PostMapping("/trades")
    public Transaction create(@RequestAttribute(AuthFilter.USER) User user, @RequestBody TradeRequest req) {
        return service.create(user.id, req);
    }

    @PutMapping("/trades/{id}")
    public Transaction update(@RequestAttribute(AuthFilter.USER) User user, @PathVariable String id, @RequestBody TradeRequest req) {
        return service.update(user.id, id, req);
    }

    @DeleteMapping("/trades/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@RequestAttribute(AuthFilter.USER) User user, @PathVariable String id) {
        service.delete(user.id, id);
    }

    public record PriceRequest(String securityId, String date, BigDecimal close) {}

    /** 手動報價（只影響自己） */
    @PutMapping("/prices")
    public Price setPrice(@RequestAttribute(AuthFilter.USER) User user, @RequestBody PriceRequest req) {
        return service.setPrice(user.id, req.securityId(), req.date(), req.close());
    }

    /** 立即更新台股報價（同一台伺服器 5 分鐘內只執行一次） */
    @PostMapping("/prices/refresh")
    public QuoteService.Result refresh() {
        long now = System.currentTimeMillis();
        if (now - lastRefresh < REFRESH_INTERVAL_MS) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "報價剛更新過，請 5 分鐘後再試");
        }
        lastRefresh = now;
        return quotes.refresh(true);
    }
}
