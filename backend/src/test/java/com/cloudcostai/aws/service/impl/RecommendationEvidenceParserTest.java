package com.cloudcostai.aws.service.impl;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class RecommendationEvidenceParserTest {

    @Test
    void parsesCostConcentrationEvidenceFromStoredReason() {
        String reason = "High service cost concentration: Amazon EC2 represents 75.00% of stored "
            + "billing-period spend, meeting the existing 25% cost-concentration threshold. Billing evidence: "
            + "service=Amazon EC2; region=us-east-1; usage type=USE1-BoxUsage; product=Amazon EC2; "
            + "actual cost=75.00 USD; total cost=100.00 USD; cost share=75.00%; billing period=2026-09; "
            + "source=stored Cost Explorer billing data in PostgreSQL aws_billing.";

        RecommendationEvidenceParser.Evidence evidence =
            RecommendationEvidenceParser.parse(reason, "High service cost concentration: Amazon EC2", null);

        assertThat(evidence.costSharePercentage()).isEqualByComparingTo("75.00");
        assertThat(evidence.periodTotalCost()).isEqualByComparingTo("100.00");
        assertThat(evidence.concentrationThreshold()).isEqualByComparingTo("25.00");
        assertThat(evidence.resourceState()).isNull();
        assertThat(evidence.savingsBasis()).isEqualTo(RecommendationEvidenceParser.SAVINGS_BASIS_INSUFFICIENT);
    }

    @Test
    void marksPositiveSavingsAsHeuristicEstimate() {
        RecommendationEvidenceParser.Evidence evidence = RecommendationEvidenceParser.parse(
            "Stored billing evidence", "Unattached volume is incurring cost", new BigDecimal("16.00"));

        assertThat(evidence.savingsBasis()).isEqualTo(RecommendationEvidenceParser.SAVINGS_BASIS_HEURISTIC);
    }

    @Test
    void marksZeroSavingsAsInsufficientData() {
        RecommendationEvidenceParser.Evidence evidence = RecommendationEvidenceParser.parse(
            "Stored billing evidence", "Unattached volume is incurring cost", BigDecimal.ZERO);

        assertThat(evidence.savingsBasis()).isEqualTo(RecommendationEvidenceParser.SAVINGS_BASIS_INSUFFICIENT);
    }

    @Test
    void detectsUnattachedAndStoppedResourceStatesFromTitle() {
        assertThat(RecommendationEvidenceParser.parse("evidence", "Unattached EBS volume detected", null).resourceState())
            .isEqualTo("UNATTACHED");
        assertThat(RecommendationEvidenceParser.parse("evidence", "Stopped EC2 instance detected", null).resourceState())
            .isEqualTo("STOPPED");
        assertThat(RecommendationEvidenceParser.parse("evidence", "EC2 Cost Increase Opportunity", null).resourceState())
            .isNull();
    }

    @Test
    void returnsNoStructuredFieldsWhenEvidenceIsAbsent() {
        RecommendationEvidenceParser.Evidence evidence = RecommendationEvidenceParser.parse(
            "The volume state is verified, but its individual cost and potential savings are unknown (NO_DATA).",
            "Unattached EBS volume detected", null);

        assertThat(evidence.costSharePercentage()).isNull();
        assertThat(evidence.periodTotalCost()).isNull();
        assertThat(evidence.concentrationThreshold()).isNull();
    }
}
