package com.cloudcostai.aws.service.impl;

import com.cloudcostai.aws.config.AwsClientFactory;
import com.cloudcostai.aws.config.AwsBillingSyncProperties;
import com.cloudcostai.aws.dto.response.AwsDashboardResponse;
import com.cloudcostai.aws.dto.response.AwsResourceResponse;
import com.cloudcostai.aws.model.AwsBilling;
import com.cloudcostai.aws.repository.AwsBillingRepository;
import com.cloudcostai.aws.repository.AwsRecommendationRepository;
import com.cloudcostai.aws.service.AwsConnectionSessionService;
import com.cloudcostai.aws.service.AwsResourceService;
import com.cloudcostai.billing.repository.BudgetRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AwsDashboardServiceImplTest {

    @Mock
    private AwsClientFactory clientFactory;

    @Mock
    private AwsConnectionSessionService sessionService;

    @Mock
    private AwsResourceService resourceService;

    @Mock
    private AwsBillingSyncProperties billingSyncProperties;

    @Mock
    private AwsBillingRepository billingRepository;


    @Mock
    private AwsRecommendationRepository recommendationRepository;

    @Mock
    private BudgetRepository budgetRepository;
    @Test
    void totalCloudSpendShowsCurrentMonthBillingData() {
        // Setup
        String accountId = "123456789012";
        LocalDate today = LocalDate.now();
        LocalDate monthStart = today.withDayOfMonth(1);
        
        when(sessionService.getSession("token")).thenReturn(
            new AwsConnectionSessionService.ConnectionSession(
                "access-key", "secret-key", "us-east-1", accountId, Instant.now().plusSeconds(600)
            )
        );

        // Create current month billing data
        AwsBilling currentMonthBilling = new AwsBilling(
            accountId,
            monthStart,
            monthStart.plusMonths(1).minusDays(1),
            "Amazon EC2",
            "us-east-1",
            BigDecimal.TEN,
            new BigDecimal("150.00"),
            "USD",
            false,
            "EC2",
            "BoxUsage",
            "i-123456",
            "Usage",
            monthStart,
            monthStart.plusMonths(1).minusDays(1),
            "record-key-current"
        );

        // Create previous month billing data
        LocalDate previousMonthStart = monthStart.minusMonths(1);
        AwsBilling previousMonthBilling = new AwsBilling(
            accountId,
            previousMonthStart,
            previousMonthStart.plusMonths(1).minusDays(1),
            "Amazon RDS",
            "us-east-1",
            BigDecimal.valueOf(5),
            new BigDecimal("100.00"),
            "USD",
            false,
            "RDS",
            "BoxUsage",
            "db-123",
            "Usage",
            previousMonthStart,
            previousMonthStart.plusMonths(1).minusDays(1),
            "record-key-previous"
        );

        // Mock repository to return all billing data
        when(billingRepository.findByAwsAccountId(accountId))
            .thenReturn(List.of(currentMonthBilling, previousMonthBilling));

        // Mock currency lookup

        // Mock resource discovery
        AwsResourceResponse resources = new AwsResourceResponse(
            accountId,
            "us-east-1",
            new AwsResourceResponse.AwsResourceSummary(
                5, 3, 2, 1, 1, 1, 1, 0, 1, 1, 1, 0, 0, 0, 0
            ),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of()
        );
        when(resourceService.discoverResources("token"))
            .thenReturn(resources);

        // Mock recommendations as empty
        when(recommendationRepository.findByAwsAccountIdAndBillingMonthOrderByCreatedAtDesc(accountId, YearMonth.now().toString()))
            .thenReturn(List.of());

        // Execute

        AwsDashboardServiceImpl service = new AwsDashboardServiceImpl(
            sessionService, resourceService, billingRepository, recommendationRepository, budgetRepository
        );
        AwsDashboardResponse response = service.getDashboard("token");
        // Assert
        assertThat(response).isNotNull();
        assertThat(response.accountId()).isEqualTo(accountId);
        
        // Verify Total Cloud Spend is NOT null (this is the fix!)
        assertThat(response.totalCloudSpend()).isNotNull();
        
        // Verify current month spend is 150.00 (EC2 cost)
        assertThat(response.totalCloudSpend().value())
            .isEqualByComparingTo(new BigDecimal("150.00"));
        
        // Verify previous month spend is 100.00 (RDS cost)
        assertThat(response.totalCloudSpend().previousMonth())
            .isEqualByComparingTo(new BigDecimal("100.00"));
        
        // Verify percentage change: (150 - 100) / 100 * 100 = 50%
        assertThat(response.totalCloudSpend().percentageChange())
            .isEqualByComparingTo(new BigDecimal("50.00"));
        assertThat(response.totalCloudSpend().changePercentage())
            .isEqualByComparingTo(new BigDecimal("50.00"));
        assertThat(response.totalCloudSpend().changeDirection())
            .isEqualTo("INCREASED");
        assertThat(response.totalCloudSpend().dataStatus())
            .isEqualTo("AVAILABLE");
        
        // Verify flag indicating previous month data exists
        assertThat(response.totalCloudSpend().hasPreviousMonthData())
            .isTrue();
        
        // Verify currency
        assertThat(response.totalCloudSpend().currency())
            .isEqualTo("USD");
    }

    @Test
    void currentMonthWithoutCurDataDoesNotReusePreviousMonthSpend() {
        String accountId = "123456789012";
        LocalDate currentMonthStart = LocalDate.now().withDayOfMonth(1);
        LocalDate previousMonthStart = currentMonthStart.minusMonths(1);

        when(sessionService.getSession("token")).thenReturn(
            new AwsConnectionSessionService.ConnectionSession(
                "access-key", "secret-key", "us-east-1", accountId, Instant.now().plusSeconds(600)
            )
        );

        AwsBilling previousMonthBilling = new AwsBilling(
            accountId,
            previousMonthStart,
            previousMonthStart.plusMonths(1).minusDays(1),
            "Amazon S3",
            "us-east-1",
            BigDecimal.valueOf(1000),
            new BigDecimal("7.12"),
            "USD",
            false,
            "S3",
            "TimedStorage-ByteHrs",
            "bucket-name",
            "Usage",
            previousMonthStart,
            previousMonthStart.plusMonths(1).minusDays(1),
            "record-key-prev"
        );

        when(billingRepository.findByAwsAccountId(accountId)).thenReturn(List.of(previousMonthBilling));

        AwsResourceResponse resources = new AwsResourceResponse(
            accountId, "us-east-1",
            new AwsResourceResponse.AwsResourceSummary(1, 1, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0),
            List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of()
        );
        when(resourceService.discoverResources("token")).thenReturn(resources);
        when(recommendationRepository.findByAwsAccountIdAndBillingMonthOrderByCreatedAtDesc(accountId, YearMonth.now().toString())).thenReturn(List.of());

        AwsDashboardServiceImpl service = new AwsDashboardServiceImpl(
            sessionService, resourceService, billingRepository, recommendationRepository, budgetRepository
        );
        AwsDashboardResponse response = service.getDashboard("token");

        assertThat(response.totalCloudSpend()).isNull();
    }

    @Test
    void totalCloudSpendHandlesNullUsageStartDate() {
        // Setup
        String accountId = "123456789012";
        LocalDate today = LocalDate.now();
        LocalDate monthStart = today.withDayOfMonth(1);
        
        when(sessionService.getSession("token")).thenReturn(
            new AwsConnectionSessionService.ConnectionSession(
                "access-key", "secret-key", "us-east-1", accountId, Instant.now().plusSeconds(600)
            )
        );

        // Create billing data with NULL usage_start_date (should fallback to billing_period_start)
        AwsBilling billingWithNullUsageDate = new AwsBilling(
            accountId,
            monthStart,
            monthStart.plusMonths(1).minusDays(1),
            "Amazon S3",
            "us-east-1",
            BigDecimal.valueOf(1000),
            new BigDecimal("50.00"),
            "USD",
            false,
            "S3",
            "TimedStorage-ByteHrs",
            "bucket-name",
            "Usage",
            null,  // usage_start_date is NULL - should use billing_period_start
            null,  // usage_end_date is NULL
            "record-key-s3"
        );

        when(billingRepository.findByAwsAccountId(accountId))
            .thenReturn(List.of(billingWithNullUsageDate));


        AwsResourceResponse resources = new AwsResourceResponse(
            accountId, "us-east-1",
            new AwsResourceResponse.AwsResourceSummary(1, 1, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0),
            List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of()
        );
        when(resourceService.discoverResources("token"))
            .thenReturn(resources);

        when(recommendationRepository.findByAwsAccountIdAndBillingMonthOrderByCreatedAtDesc(accountId, YearMonth.now().toString()))
            .thenReturn(List.of());

        // Execute

        AwsDashboardServiceImpl service = new AwsDashboardServiceImpl(
            sessionService, resourceService, billingRepository, recommendationRepository, budgetRepository
        );
        AwsDashboardResponse response = service.getDashboard("token");
        // Assert - Even with NULL usage_start_date, billing data should be included!
        assertThat(response.totalCloudSpend()).isNotNull();
        assertThat(response.totalCloudSpend().value())
            .isEqualByComparingTo(new BigDecimal("50.00"));
    }

    @Test
    void totalCloudSpendShowsUnavailableWhenNoBillingData() {
        // Setup
        String accountId = "123456789012";
        
        when(sessionService.getSession("token")).thenReturn(
            new AwsConnectionSessionService.ConnectionSession(
                "access-key", "secret-key", "us-east-1", accountId, Instant.now().plusSeconds(600)
            )
        );

        // No billing data for this account
        when(billingRepository.findByAwsAccountId(accountId))
            .thenReturn(List.of());


        AwsResourceResponse resources = new AwsResourceResponse(
            accountId, "us-east-1",
            new AwsResourceResponse.AwsResourceSummary(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
            List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of()
        );
        when(resourceService.discoverResources("token"))
            .thenReturn(resources);

        when(recommendationRepository.findByAwsAccountIdAndBillingMonthOrderByCreatedAtDesc(accountId, YearMonth.now().toString()))
            .thenReturn(List.of());

        AwsDashboardServiceImpl service = new AwsDashboardServiceImpl(
            sessionService, resourceService, billingRepository, recommendationRepository, budgetRepository
        );
        AwsDashboardResponse response = service.getDashboard("token");

        assertThat(response.totalCloudSpend()).isNull();
    }

    @Test
    void totalCloudSpendCalculatesPercentageChangeCorrectly() {
        // Setup
        String accountId = "123456789012";
        LocalDate today = LocalDate.now();
        LocalDate monthStart = today.withDayOfMonth(1);
        
        when(sessionService.getSession("token")).thenReturn(
            new AwsConnectionSessionService.ConnectionSession(
                "access-key", "secret-key", "us-east-1", accountId, Instant.now().plusSeconds(600)
            )
        );

        // Previous month: $100
        LocalDate previousMonthStart = monthStart.minusMonths(1);
        AwsBilling previousBilling = new AwsBilling(
            accountId, previousMonthStart, previousMonthStart.plusMonths(1).minusDays(1),
            "EC2", "us-east-1", BigDecimal.TEN, new BigDecimal("100.00"), "USD", false,
            "EC2", "BoxUsage", "i-old", "Usage", previousMonthStart, previousMonthStart.plusMonths(1).minusDays(1),
            "prev-key"
        );

        // Current month: $150 (50% increase)
        AwsBilling currentBilling = new AwsBilling(
            accountId, monthStart, monthStart.plusMonths(1).minusDays(1),
            "EC2", "us-east-1", BigDecimal.TEN, new BigDecimal("150.00"), "USD", false,
            "EC2", "BoxUsage", "i-new", "Usage", monthStart, monthStart.plusMonths(1).minusDays(1),
            "curr-key"
        );

        when(billingRepository.findByAwsAccountId(accountId))
            .thenReturn(List.of(previousBilling, currentBilling));


        AwsResourceResponse resources = new AwsResourceResponse(
            accountId, "us-east-1",
            new AwsResourceResponse.AwsResourceSummary(2, 2, 2, 2, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
            List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of()
        );
        when(resourceService.discoverResources("token"))
            .thenReturn(resources);

        when(recommendationRepository.findByAwsAccountIdAndBillingMonthOrderByCreatedAtDesc(accountId, YearMonth.now().toString()))
            .thenReturn(List.of());

        // Execute
        AwsDashboardServiceImpl service = new AwsDashboardServiceImpl(
            sessionService, resourceService, billingRepository, recommendationRepository, budgetRepository
        );
        AwsDashboardResponse response = service.getDashboard("token");

        // Assert
        assertThat(response.totalCloudSpend()).isNotNull();
        assertThat(response.totalCloudSpend().value())
            .isEqualByComparingTo(new BigDecimal("150.00"));
        assertThat(response.totalCloudSpend().previousMonth())
            .isEqualByComparingTo(new BigDecimal("100.00"));
        
        // (150 - 100) / 100 * 100 = 50.00%
        assertThat(response.totalCloudSpend().percentageChange())
            .isEqualByComparingTo(new BigDecimal("50.00"));
    }

    @Test
    void totalCloudSpendHandlesZeroPreviousMonthSpendWithoutDivisionByZero() {
        String accountId = "123456789012";
        LocalDate today = LocalDate.now();
        LocalDate monthStart = today.withDayOfMonth(1);

        when(sessionService.getSession("token")).thenReturn(
            new AwsConnectionSessionService.ConnectionSession(
                "access-key", "secret-key", "us-east-1", accountId, Instant.now().plusSeconds(600)
            )
        );

        LocalDate previousMonthStart = monthStart.minusMonths(1);
        AwsBilling previousBilling = new AwsBilling(
            accountId, previousMonthStart, previousMonthStart.plusMonths(1).minusDays(1),
            "EC2", "us-east-1", BigDecimal.TEN, BigDecimal.ZERO, "USD", false,
            "EC2", "BoxUsage", "i-old", "Usage", previousMonthStart, previousMonthStart.plusMonths(1).minusDays(1),
            "prev-zero-key"
        );

        AwsBilling currentBilling = new AwsBilling(
            accountId, monthStart, monthStart.plusMonths(1).minusDays(1),
            "S3", "us-east-1", BigDecimal.TEN, new BigDecimal("75.00"), "USD", false,
            "S3", "TimedStorage-ByteHrs", "bucket", "Usage", monthStart, monthStart.plusMonths(1).minusDays(1),
            "curr-zero-key"
        );

        when(billingRepository.findByAwsAccountId(accountId))
            .thenReturn(List.of(previousBilling, currentBilling));

        AwsResourceResponse resources = new AwsResourceResponse(
            accountId, "us-east-1",
            new AwsResourceResponse.AwsResourceSummary(2, 2, 1, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0),
            List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of()
        );
        when(resourceService.discoverResources("token")).thenReturn(resources);
        when(recommendationRepository.findByAwsAccountIdAndBillingMonthOrderByCreatedAtDesc(accountId, YearMonth.now().toString())).thenReturn(List.of());


        AwsDashboardServiceImpl service = new AwsDashboardServiceImpl(
            sessionService, resourceService, billingRepository, recommendationRepository, budgetRepository
        );
        AwsDashboardResponse response = service.getDashboard("token");

        assertThat(response.totalCloudSpend()).isNotNull();
        assertThat(response.totalCloudSpend().value()).isEqualByComparingTo(new BigDecimal("75.00"));
        assertThat(response.totalCloudSpend().previousMonth()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.totalCloudSpend().changePercentage()).isNull();
        assertThat(response.totalCloudSpend().changeDirection()).isEqualTo("NO_COMPARABLE_PREVIOUS_MONTH_SPEND");
        assertThat(response.totalCloudSpend().dataStatus()).isEqualTo("AVAILABLE");
    }
}
