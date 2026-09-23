package com.cloudcostai.aws.service.impl;

import com.cloudcostai.aws.dto.response.AwsRecommendationResponse;
import com.cloudcostai.aws.dto.response.AwsResourceResponse;
import com.cloudcostai.aws.model.AwsBilling;
import com.cloudcostai.aws.model.AwsRecommendation;
import com.cloudcostai.aws.repository.AwsBillingRepository;
import com.cloudcostai.aws.repository.AwsRecommendationRepository;
import com.cloudcostai.aws.service.AwsConnectionSessionService;
import com.cloudcostai.aws.service.AwsBillingSourceResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AwsRecommendationServiceImplTest {
    @Mock private AwsConnectionSessionService sessionService;
    @Mock private AwsBillingRepository billingRepository;
    @Mock private AwsRecommendationRepository recommendationRepository;
    @Mock private AwsBillingSourceResolver billingSourceResolver;

    @Test
    void noCurDataReturnsNoDataWithoutRecommendations() {
        AwsRecommendationServiceImpl service = service(List.of());

        AwsRecommendationResponse response = service.getRecommendations("token");

        assertThat(response.recommendations()).isEmpty();
    }

    @Test
    void selectedAugustUsesAugustRowsOnlyAndDoesNotInventSaving() {
        AwsRecommendationServiceImpl service = service(List.of(
                row("Amazon EC2", "2026-07-01", "100.00", "july"),
                row("Amazon EC2", "2026-08-01", "120.00", "august"),
                row("Amazon RDS", "2026-08-31", "80.00", "august-rds"),
                row("Amazon EC2", "2026-09-01", "1000.00", "september")));
        AwsRecommendation recommendation = new AwsRecommendation("123456789012", "key", "Review high-cost service", "OPTIMIZATION_OPPORTUNITY", "HIGH", "Amazon EC2", null, "ap-south-1", new BigDecimal("120.00"), "reason", "recommendation", "action", null, "NEW");
        when(recommendationRepository.findByAwsAccountIdOrderByCreatedAtDesc("123456789012")).thenReturn(List.of(recommendation));

        AwsRecommendationResponse response = service.getRecommendations("token");

        assertThat(response.recommendations()).singleElement().satisfies(item -> {
            assertThat(item.currentCost()).isEqualByComparingTo("120.00");
            assertThat(item.estimatedMonthlySaving()).isNull();
            assertThat(item.reason()).isEqualTo("reason");
        });
    }

    @Test
    void unattachedEbsUsesPositiveMatchingBillingEvidence() {
        AwsRecommendationServiceImpl service = service(List.of(row("Amazon EBS", "2026-08-15", "16.00", "ebs")));
        AwsRecommendation recommendation = new AwsRecommendation("123456789012", "ebs-key", "Review unused EBS volume", "UNUSED_RESOURCE", "MEDIUM", "Amazon EBS", "vol-123", "ap-south-1", new BigDecimal("16.00"), "reason", "recommendation", "action", new BigDecimal("16.00"), "NEW");
        when(recommendationRepository.findByAwsAccountIdOrderByCreatedAtDesc("123456789012")).thenReturn(List.of(recommendation));

        AwsRecommendationResponse response = service.getRecommendations("token");

        assertThat(response.recommendations()).singleElement().satisfies(item -> assertThat(item.estimatedMonthlySaving()).isEqualByComparingTo("16.00"));
        assertThat(response.recommendations()).singleElement().extracting(item -> item.service()).isEqualTo("Amazon EBS");
    }

    private AwsRecommendationServiceImpl service(List<AwsBilling> rows) {
        when(sessionService.getSession("token")).thenReturn(new AwsConnectionSessionService.ConnectionSession("access", "secret", "ap-south-1", "123456789012", Instant.now().plusSeconds(600)));
        when(billingRepository.findByAwsAccountId("123456789012")).thenReturn(rows);
        return new AwsRecommendationServiceImpl(sessionService, billingRepository, recommendationRepository, billingSourceResolver);
    }

    private AwsResourceResponse resources() {
        return new AwsResourceResponse("123456789012", "ap-south-1", new AwsResourceResponse.AwsResourceSummary(1, 0, 0, 0, 0, 1, 0, 1, 0, 0, 0, 0, 0, 0, 0), List.of(), List.of(new AwsResourceResponse.EbsVolumeDto("vol-123", "gp3", 100, "available", "ap-south-1a", null)), List.of(), List.of(), List.of(), List.of(), List.of());
    }

    private AwsBilling row(String service, String date, String cost, String key) {
        LocalDate usage = LocalDate.parse(date);
        return new AwsBilling("123456789012", usage, usage.plusDays(1), service, "ap-south-1", BigDecimal.ONE, new BigDecimal(cost), "USD", false, service, "Usage", key.equals("ebs") ? "vol-123" : key, "Usage", usage, usage.plusDays(1), key);
    }
}
