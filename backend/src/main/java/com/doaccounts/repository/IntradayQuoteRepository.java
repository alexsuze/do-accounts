package com.doaccounts.repository;

import com.doaccounts.model.IntradayQuote;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface IntradayQuoteRepository extends MongoRepository<IntradayQuote, String> {

    List<IntradayQuote> findBySecurityIdInAndDate(Collection<String> securityIds, String date);

    Optional<IntradayQuote> findFirstBySecurityIdAndDate(String securityId, String date);
}
