package com.doaccounts.repository;

import com.doaccounts.model.Category;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface CategoryRepository extends MongoRepository<Category, String> {

    List<Category> findByUserId(String userId);

    long countByUserId(String userId);
}
