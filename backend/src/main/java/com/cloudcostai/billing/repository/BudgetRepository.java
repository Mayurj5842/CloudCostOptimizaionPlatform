package com.cloudcostai.billing.repository;

import com.cloudcostai.billing.model.Budget;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BudgetRepository extends JpaRepository<Budget, UUID> {
    List<Budget> findByUserIdOrderByCreatedAtDesc(String userId);
    List<Budget> findByAwsAccountIdOrderByCreatedAtDesc(String awsAccountId);
    Optional<Budget> findFirstByAwsAccountIdAndStatusOrderByCreatedAtDesc(String awsAccountId, String status);
    Optional<Budget> findFirstByAwsAccountIdAndServiceNameIsNullAndEnabledTrueAndStartDateLessThanEqualAndEndDateGreaterThanEqualOrderByCreatedAtDesc(String awsAccountId, LocalDate endBound, LocalDate startBound);
    Optional<Budget> findFirstByAwsAccountIdAndEnabledTrueAndStartDateLessThanEqualAndEndDateGreaterThanEqualOrderByCreatedAtDesc(String awsAccountId, LocalDate endBound, LocalDate startBound);
    boolean existsByAwsAccountIdAndName(String awsAccountId, String name);
    List<Budget> findByAwsAccountId(String awsAccountId);
}
