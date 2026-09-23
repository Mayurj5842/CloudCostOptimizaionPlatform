package com.cloudcostai.aws.repository;

import com.cloudcostai.aws.model.AwsBilling;
import org.springframework.data.jpa.repository.JpaRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AwsBillingRepository extends JpaRepository<AwsBilling, UUID> {
        List<AwsBilling> findByAwsAccountId(String awsAccountId);

    Optional<AwsBilling> findByAwsAccountIdAndBillingPeriodStartAndBillingPeriodEndAndServiceNameAndRegion(
            String awsAccountId,
            LocalDate billingPeriodStart,
            LocalDate billingPeriodEnd,
            String serviceName,
            String region);

    List<AwsBilling> findByAwsAccountIdAndBillingPeriodStartAndBillingPeriodEnd(
            String awsAccountId,
            LocalDate billingPeriodStart,
            LocalDate billingPeriodEnd);

        List<AwsBilling> findByAwsAccountIdAndUsageStartDateBetween(String awsAccountId, LocalDate start, LocalDate end);

        boolean existsByRecordKey(String recordKey);

        @Query("select coalesce(sum(b.costAmount), 0) from AwsBilling b where b.awsAccountId = :accountId and b.usageStartDate >= :start and b.usageStartDate < :end")
        BigDecimal sumCost(String accountId, LocalDate start, LocalDate end);

        @Query("select distinct b.currency from AwsBilling b where b.awsAccountId = :accountId")
        List<String> findCurrencies(@Param("accountId") String accountId);

        @Query(value = "select cast(coalesce(usage_start_date, billing_period_start) as date) as period, coalesce(sum(cost_amount), 0) as cost from aws_billing where aws_account_id = :accountId and coalesce(usage_start_date, billing_period_start) >= :startDate and coalesce(usage_start_date, billing_period_start) < :endDate group by cast(coalesce(usage_start_date, billing_period_start) as date) order by period", nativeQuery = true)
        List<CostTrendProjection> findDailyCostTrend(@Param("accountId") String accountId, @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

        @Query(value = "select date_trunc('month', coalesce(usage_start_date, billing_period_start))::date as period, coalesce(sum(cost_amount), 0) as cost from aws_billing where aws_account_id = :accountId and coalesce(usage_start_date, billing_period_start) >= :startDate and coalesce(usage_start_date, billing_period_start) < :endDate group by date_trunc('month', coalesce(usage_start_date, billing_period_start))::date order by period", nativeQuery = true)
        List<CostTrendProjection> findMonthlyCostTrend(@Param("accountId") String accountId, @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

        @Query(value = "select coalesce(service_name, 'Others') as service, coalesce(sum(cost_amount), 0) as cost from aws_billing where aws_account_id = :accountId and coalesce(usage_start_date, billing_period_start) >= :startDate and coalesce(usage_start_date, billing_period_start) < :endDate group by coalesce(service_name, 'Others') order by cost desc", nativeQuery = true)
        List<ServiceDistributionProjection> findServiceDistribution(@Param("accountId") String accountId, @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

        interface CostTrendProjection {
                LocalDate getPeriod();
                BigDecimal getCost();
        }

        interface ServiceDistributionProjection {
                String getService();
                BigDecimal getCost();
        }
}
