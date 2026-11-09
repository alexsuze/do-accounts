package com.doaccounts.repository;

import com.doaccounts.model.User;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends MongoRepository<User, String> {

    Optional<User> findByGoogleSub(String googleSub);

    List<User> findAllByOrderByCreatedAtDesc();
}
