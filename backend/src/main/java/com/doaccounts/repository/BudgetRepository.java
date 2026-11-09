package com.doaccounts.repository;

import com.doaccounts.model.Budget;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface BudgetRepository extends MongoRepository<Budget, String> {

    /** 某月份以前（含）的全部預算紀錄，用來算出每個分類的有效預算 */
    List<Budget> findByUserIdAndMonthLessThanEqual(String userId, String month);

    List<Budget> findByUserId(String userId);
}
