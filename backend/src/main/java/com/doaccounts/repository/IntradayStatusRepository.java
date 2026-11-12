package com.doaccounts.repository;

import com.doaccounts.model.IntradayStatus;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface IntradayStatusRepository extends MongoRepository<IntradayStatus, String> {
}
