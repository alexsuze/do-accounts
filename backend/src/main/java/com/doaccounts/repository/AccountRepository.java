package com.doaccounts.repository;

import com.doaccounts.model.Account;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface AccountRepository extends MongoRepository<Account, String> {

    List<Account> findByUserId(String userId);
}
