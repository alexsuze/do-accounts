package com.doaccounts.repository;

import com.doaccounts.model.ImportTemplate;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface ImportTemplateRepository extends MongoRepository<ImportTemplate, String> {

    List<ImportTemplate> findByUserIdOrderByNameAsc(String userId);
}
