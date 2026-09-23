package com.cloudcostai.billing;

import com.cloudcostai.billing.service.BudgetService;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class BudgetCalculationTest {

    @Test
    void calculatesUsagePercentageAndStatusFromRealSpend() {
        BigDecimal budgetAmount = new BigDecimal("1000.00");
        BigDecimal actualSpend = new BigDecimal("620.00");

        BigDecimal usage = actualSpend.divide(budgetAmount, 4, java.math.RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));

        assertThat(usage).isEqualByComparingTo(new BigDecimal("62.0000"));
        assertThat(BudgetService.calculateStatus(usage)).isEqualTo("NORMAL");
        assertThat(actualSpend.compareTo(budgetAmount)).isLessThan(0);
    }
}
