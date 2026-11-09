package com.doaccounts.repository;

import com.doaccounts.model.Price;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PriceRepository extends MongoRepository<Price, String> {

    /** 系統報價，某日期以前 */
    List<Price> findByUserIdIsNullAndSecurityIdInAndDateLessThanEqual(Collection<String> securityIds, String date);

    /** 使用者手動報價，某日期以前 */
    List<Price> findByUserIdAndSecurityIdInAndDateLessThanEqual(String userId, Collection<String> securityIds, String date);

    Optional<Price> findFirstByUserIdIsNullAndSecurityIdAndDate(String securityId, String date);

    Optional<Price> findFirstByUserIdAndSecurityIdAndDate(String userId, String securityId, String date);
}
