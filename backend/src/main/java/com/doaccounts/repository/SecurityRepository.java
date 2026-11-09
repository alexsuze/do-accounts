package com.doaccounts.repository;

import com.doaccounts.model.Security;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface SecurityRepository extends MongoRepository<Security, String> {

    Optional<Security> findFirstByMarketAndSymbol(String market, String symbol);
}
