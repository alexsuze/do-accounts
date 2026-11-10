package com.doaccounts.repository;

import com.doaccounts.model.CandleMonth;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface CandleMonthRepository extends MongoRepository<CandleMonth, String> {

    Optional<CandleMonth> findFirstBySecurityIdAndMonth(String securityId, String month);
}
