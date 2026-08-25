package com.cloudcostai.aws.repository;

import com.cloudcostai.aws.model.AwsBillingSummary;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AwsBillingSummaryRepository extends JpaRepository<AwsBillingSummary, UUID> {
    Optional<AwsBillingSummary> findByAwsAccountIdAndBillingPeriod(String awsAccountId, String billingPeriod);

    List<AwsBillingSummary> findByAwsAccountIdOrderByBillingPeriodDesc(String awsAccountId, Pageable pageable);
}
