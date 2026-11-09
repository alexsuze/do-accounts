package com.doaccounts.repository;

import com.doaccounts.model.ImportBatch;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface ImportBatchRepository extends MongoRepository<ImportBatch, String> {

    List<ImportBatch> findTop20ByUserIdOrderByCreatedAtDesc(String userId);
}
