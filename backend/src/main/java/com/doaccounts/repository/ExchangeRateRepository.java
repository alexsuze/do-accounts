package com.doaccounts.repository;

import com.doaccounts.model.ExchangeRate;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ExchangeRateRepository extends MongoRepository<ExchangeRate, String> {

    /** 系統匯率（全站共用），某日期以前 */
    List<ExchangeRate> findByUserIdIsNullAndCurrencyInAndDateLessThanEqual(Collection<String> currencies, String date);

    /** 使用者的手動匯率，某日期以前 */
    List<ExchangeRate> findByUserIdAndCurrencyInAndDateLessThanEqual(String userId, Collection<String> currencies, String date);

    List<ExchangeRate> findByUserIdOrderByDateDesc(String userId);

    Optional<ExchangeRate> findFirstByUserIdIsNullAndCurrencyAndDate(String currency, String date);

    Optional<ExchangeRate> findFirstByUserIdAndCurrencyAndDate(String userId, String currency, String date);

    boolean existsByUserIdIsNullAndCurrency(String currency);

    /** 某天是否已有真正抓到的匯率（不含初始值） */
    boolean existsByUserIdIsNullAndDateAndSourceNot(String date, String source);
}
