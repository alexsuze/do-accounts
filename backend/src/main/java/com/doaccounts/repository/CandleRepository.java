package com.doaccounts.repository;

import com.doaccounts.model.Candle;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface CandleRepository extends MongoRepository<Candle, String> {

    List<Candle> findBySecurityIdAndDateGreaterThanEqualOrderByDateAsc(String securityId, String from);

    Optional<Candle> findFirstBySecurityIdAndDate(String securityId, String date);
}
