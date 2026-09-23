package com.cloudcostai.billing.repository;

import com.cloudcostai.billing.model.BudgetAlert;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface BudgetAlertRepository extends JpaRepository<BudgetAlert, UUID> {
    List<BudgetAlert> findByBudgetIdOrderByTriggeredAtDesc(UUID budgetId);
    List<BudgetAlert> findByStatusOrderByTriggeredAtDesc(String status);
    boolean existsByBudgetIdAndThresholdPercentAndAlertPeriod(UUID budgetId, Integer thresholdPercent, String alertPeriod);
}
