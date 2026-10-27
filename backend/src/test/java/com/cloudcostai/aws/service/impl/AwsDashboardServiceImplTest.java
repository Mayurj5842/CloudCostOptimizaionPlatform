package com.cloudcostai.aws.service.impl;

import com.cloudcostai.aws.config.AwsClientFactory;
import com.cloudcostai.aws.config.AwsBillingSyncProperties;
import com.cloudcostai.aws.dto.response.AwsDashboardResponse;
import com.cloudcostai.aws.dto.response.AwsResourceResponse;
import com.cloudcostai.aws.model.AwsBilling;
import com.cloudcostai.aws.repository.AwsBillingRepository;
import com.cloudcostai.aws.repository.AwsRecommendationRepository;
import com.cloudcostai.aws.service.AwsConnectionSessionService;
import com.cloudcostai.aws.service.AwsBillingSourceResolver;
import com.cloudcostai.aws.service.AwsInsightService;
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

    @Mock
    private AwsInsightService insightService;

        @Test
        void dashboardAndChartsUseFetchedLiveCostsAndKeepCurTrendHistory() {
        String accountId = "123456789012";
        when(sessionService.getSession("token")).thenReturn(new AwsConnectionSessionService.ConnectionSession(
            "access", "secret", "us-east-1", accountId, Instant.now().plusSeconds(600)));
        AwsBilling augustCur = sourcedRow(accountId, LocalDate.of(2026, 8, 10), "10.00", "CUR S3", "CUR_2_0");
        AwsBilling septemberCur = sourcedRow(accountId, LocalDate.of(2026, 9, 10), "20.00", "CUR EC2", "CUR_2_0");
        AwsBilling septemberLive = sourcedRow(accountId, LocalDate.of(2026, 9, 10), "12.50", "Live EC2", "COST_EXPLORER");
        when(billingRepository.findByAwsAccountIdAndDataSourceOrderByBillingPeriodStartAsc(accountId, "CUR_2_0"))
            .thenReturn(List.of(augustCur, septemberCur));
        when(billingRepository.findByAwsAccountIdAndDataSourceOrderByBillingPeriodStartAsc(accountId, "COST_EXPLORER"))
            .thenReturn(List.of(septemberLive));
        when(resourceService.discoverResources("token")).thenReturn(new AwsResourceResponse(accountId, "us-east-1",
            new AwsResourceResponse.AwsResourceSummary(1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
            List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of()));
        when(recommendationRepository.findByAwsAccountIdAndBillingMonthOrderByCreatedAtDesc(accountId, "2026-09"))
            .thenReturn(List.of());

        AwsDashboardServiceImpl service = new AwsDashboardServiceImpl(sessionService, resourceService, billingRepository,
            recommendationRepository, budgetRepository, new AwsBillingSourceResolver(billingRepository));

        AwsDashboardResponse dashboard = service.getDashboard("token", "2026-09");
        var trend = service.getCostTrend("token", "3M", "2026-09");
        var distribution = service.getServiceDistribution("token", "3M", "2026-09");

        assertThat(dashboard.totalCloudSpend().value()).isEqualByComparingTo("12.50");
        assertThat(dashboard.billing().dataSource()).isEqualTo("COST_EXPLORER");
        assertThat(trend.dataSource()).isEqualTo("MIXED");
        assertThat(trend.points()).extracting("cost").asList().contains(new BigDecimal("10.00"), new BigDecimal("12.50"));
        assertThat(distribution.totalCost()).isEqualByComparingTo("22.50");
        assertThat(distribution.services()).extracting("service").containsExactly("Live EC2", "CUR S3");
        }

    @Test
    void dashboardEstimatedSavingsUsesInsightImpact() {
        String accountId = "123456789012";
        when(sessionService.getSession("token")).thenReturn(new AwsConnectionSessionService.ConnectionSession(
            "access", "secret", "us-east-1", accountId, Instant.now().plusSeconds(600)));
        AwsBilling septemberCur = sourcedRow(accountId, LocalDate.of(2026, 9, 10), "20.00", "CUR EC2", "CUR_2_0");
        when(billingRepository.findByAwsAccountIdAndDataSourceOrderByBillingPeriodStartAsc(accountId, "CUR_2_0"))
            .thenReturn(List.of(septemberCur));
        when(billingRepository.findByAwsAccountIdAndDataSourceOrderByBillingPeriodStartAsc(accountId, "COST_EXPLORER"))
            .thenReturn(List.of());
        when(resourceService.discoverResources("token")).thenReturn(new AwsResourceResponse(accountId, "us-east-1",
            new AwsResourceResponse.AwsResourceSummary(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
            List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of()));
        when(insightService.estimatedSavings("token", "2026-09")).thenReturn(new BigDecimal("0.70"));

        AwsDashboardServiceImpl service = new AwsDashboardServiceImpl(sessionService, resourceService, billingRepository,
            recommendationRepository, budgetRepository, new AwsBillingSourceResolver(billingRepository), insightService);

        AwsDashboardResponse dashboard = service.getDashboard("token", "2026-09");

        assertThat(dashboard.estimatedSavings()).isNotNull();
        assertThat(dashboard.estimatedSavings().value()).isEqualByComparingTo("0.70");
    }

    @Test
    void dashboardEstimatedSavingsIsNullWithoutInsightOrStoredEstimate() {
        String accountId = "123456789012";
        when(sessionService.getSession("token")).thenReturn(new AwsConnectionSessionService.ConnectionSession(
            "access", "secret", "us-east-1", accountId, Instant.now().plusSeconds(600)));
        when(billingRepository.findByAwsAccountIdAndDataSourceOrderByBillingPeriodStartAsc(accountId, "CUR_2_0"))
            .thenReturn(List.of(sourcedRow(accountId, LocalDate.of(2026, 9, 10), "20.00", "CUR EC2", "CUR_2_0")));
        when(billingRepository.findByAwsAccountIdAndDataSourceOrderByBillingPeriodStartAsc(accountId, "COST_EXPLORER"))
            .thenReturn(List.of());
        when(resourceService.discoverResources("token")).thenReturn(new AwsResourceResponse(accountId, "us-east-1",
            new AwsResourceResponse.AwsResourceSummary(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
            List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of()));
        when(insightService.estimatedSavings("token", "2026-09")).thenReturn(null);
        when(recommendationRepository.findByAwsAccountIdAndBillingMonthOrderByCreatedAtDesc(accountId, "2026-09"))
            .thenReturn(List.of());

        AwsDashboardServiceImpl service = new AwsDashboardServiceImpl(sessionService, resourceService, billingRepository,
            recommendationRepository, budgetRepository, new AwsBillingSourceResolver(billingRepository), insightService);

        AwsDashboardResponse dashboard = service.getDashboard("token", "2026-09");

        assertThat(dashboard.estimatedSavings()).isNull();
    }

    private AwsBilling sourcedRow(String accountId, LocalDate date, String cost, String service, String source) {
        return new AwsBilling(accountId, date, date, service, "us-east-1", BigDecimal.ONE, new BigDecimal(cost),
            "USD", false, service, "Usage", null, "Usage", date, date, source + date + service, source);
        }

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

    @Test
    void forecastProjectsTheCurrentMonthFromTheObservedDailyRunRate() {
        String accountId = "123456789012";
        LocalDate today = LocalDate.now();
        YearMonth current = YearMonth.from(today);
        int observedDays = Math.min(10, today.getDayOfMonth());

        when(sessionService.getSession("token")).thenReturn(
            new AwsConnectionSessionService.ConnectionSession(
                "access-key", "secret-key", "us-east-1", accountId, Instant.now().plusSeconds(600)));
        when(billingRepository.findByAwsAccountId(accountId)).thenReturn(dailyRows(accountId, current, observedDays));
        when(resourceService.discoverResources("token")).thenReturn(emptyResources(accountId));
        when(recommendationRepository.findByAwsAccountIdAndBillingMonthOrderByCreatedAtDesc(accountId, current.toString()))
            .thenReturn(List.of());

        AwsDashboardServiceImpl service = new AwsDashboardServiceImpl(
            sessionService, resourceService, billingRepository, recommendationRepository, budgetRepository);
        AwsDashboardResponse response = service.getDashboard("token", current.toString());

        AwsDashboardResponse.Forecast forecast = response.forecast();
        assertThat(forecast).isNotNull();
        assertThat(forecast.method()).isEqualTo("RUN_RATE_PROJECTION");
        assertThat(forecast.dataStatus()).isEqualTo("AVAILABLE");
        assertThat(forecast.daysObserved()).isEqualTo(observedDays);
        assertThat(forecast.daysInMonth()).isEqualTo(current.lengthOfMonth());
        assertThat(forecast.remainingDays()).isEqualTo(current.lengthOfMonth() - observedDays);
        assertThat(forecast.actualToDate()).isEqualByComparingTo(new BigDecimal(observedDays).setScale(2));
        assertThat(forecast.dailyRunRate()).isEqualByComparingTo("1.000000");
        assertThat(forecast.projectedRemaining()).isEqualByComparingTo(new BigDecimal(current.lengthOfMonth() - observedDays).setScale(2));
        assertThat(forecast.value()).isEqualByComparingTo(new BigDecimal(current.lengthOfMonth()).setScale(2));
        assertThat(forecast.previousMonth()).isNull();
        assertThat(forecast.percentageChange()).isNull();
    }

    @Test
    void forecastOfACompletedMonthEqualsTheFinalActual() {
        String accountId = "123456789012";
        YearMonth completed = YearMonth.now().minusMonths(1);
        int days = completed.lengthOfMonth();

        when(sessionService.getSession("token")).thenReturn(
            new AwsConnectionSessionService.ConnectionSession(
                "access-key", "secret-key", "us-east-1", accountId, Instant.now().plusSeconds(600)));
        when(billingRepository.findByAwsAccountId(accountId))
            .thenReturn(List.of(monthBucketRow(accountId, completed, new BigDecimal(days).multiply(new BigDecimal("2.00")))));
        when(resourceService.discoverResources("token")).thenReturn(emptyResources(accountId));
        when(recommendationRepository.findByAwsAccountIdAndBillingMonthOrderByCreatedAtDesc(accountId, completed.toString()))
            .thenReturn(List.of());

        AwsDashboardServiceImpl service = new AwsDashboardServiceImpl(
            sessionService, resourceService, billingRepository, recommendationRepository, budgetRepository);
        AwsDashboardResponse response = service.getDashboard("token", completed.toString());

        AwsDashboardResponse.Forecast forecast = response.forecast();
        assertThat(forecast).isNotNull();
        assertThat(forecast.method()).isEqualTo("FINAL_ACTUAL");
        assertThat(forecast.remainingDays()).isZero();
        assertThat(forecast.projectedRemaining()).isEqualByComparingTo(BigDecimal.ZERO.setScale(2));
        assertThat(forecast.value()).isEqualByComparingTo(new BigDecimal(days).multiply(new BigDecimal("2.00")).setScale(2));
        assertThat(forecast.dailyRunRate()).isEqualByComparingTo("2.000000");
    }

    @Test
    void forecastFallsBackToThePriorMonthRunRateWhenTheCurrentMonthHasNoRows() {
        String accountId = "123456789012";
        YearMonth current = YearMonth.now();
        YearMonth previous = current.minusMonths(1);
        int previousDays = previous.lengthOfMonth();

        when(sessionService.getSession("token")).thenReturn(
            new AwsConnectionSessionService.ConnectionSession(
                "access-key", "secret-key", "us-east-1", accountId, Instant.now().plusSeconds(600)));
        when(billingRepository.findByAwsAccountId(accountId))
            .thenReturn(List.of(monthBucketRow(accountId, previous, new BigDecimal(previousDays).setScale(2))));
        when(resourceService.discoverResources("token")).thenReturn(emptyResources(accountId));
        when(recommendationRepository.findByAwsAccountIdAndBillingMonthOrderByCreatedAtDesc(accountId, current.toString()))
            .thenReturn(List.of());

        AwsDashboardServiceImpl service = new AwsDashboardServiceImpl(
            sessionService, resourceService, billingRepository, recommendationRepository, budgetRepository);
        AwsDashboardResponse response = service.getDashboard("token", current.toString());

        AwsDashboardResponse.Forecast forecast = response.forecast();
        assertThat(forecast).isNotNull();
        assertThat(forecast.method()).isEqualTo("PRIOR_MONTH_RUN_RATE");
        assertThat(forecast.daysObserved()).isZero();
        assertThat(forecast.dailyRunRate()).isEqualByComparingTo("1.000000");
        assertThat(forecast.value()).isEqualByComparingTo(new BigDecimal(current.lengthOfMonth()).setScale(2));
    }

    @Test
    void forecastIsUnavailableWhenTheSelectedMonthHasNoBillingData() {
        String accountId = "123456789012";
        YearMonth future = YearMonth.now().plusMonths(3);

        when(sessionService.getSession("token")).thenReturn(
            new AwsConnectionSessionService.ConnectionSession(
                "access-key", "secret-key", "us-east-1", accountId, Instant.now().plusSeconds(600)));
        when(billingRepository.findByAwsAccountId(accountId)).thenReturn(List.of());
        when(resourceService.discoverResources("token")).thenReturn(emptyResources(accountId));
        when(recommendationRepository.findByAwsAccountIdAndBillingMonthOrderByCreatedAtDesc(accountId, future.toString()))
            .thenReturn(List.of());

        AwsDashboardServiceImpl service = new AwsDashboardServiceImpl(
            sessionService, resourceService, billingRepository, recommendationRepository, budgetRepository);
        AwsDashboardResponse response = service.getDashboard("token", future.toString());

        AwsDashboardResponse.Forecast forecast = response.forecast();
        assertThat(forecast).isNotNull();
        assertThat(forecast.value()).isNull();
        assertThat(forecast.method()).isEqualTo("NO_BILLING_DATA");
        assertThat(forecast.dataStatus()).isEqualTo("NO_BILLING_DATA");
    }

    private AwsResourceResponse emptyResources(String accountId) {
        return new AwsResourceResponse(accountId, "us-east-1",
            new AwsResourceResponse.AwsResourceSummary(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
            List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
    }

    private List<AwsBilling> dailyRows(String accountId, YearMonth month, int days) {
        List<AwsBilling> rows = new java.util.ArrayList<>();
        for (int day = 1; day <= days; day++) {
            LocalDate date = month.atDay(day);
            rows.add(new AwsBilling(accountId, date, date, "EC2", "us-east-1", BigDecimal.ONE, BigDecimal.ONE,
                "USD", false, "EC2", "BoxUsage", "i-" + day, "Usage", date, date, "forecast-" + month + "-" + day));
        }
        return rows;
    }

    private AwsBilling monthBucketRow(String accountId, YearMonth month, BigDecimal cost) {
        LocalDate start = month.atDay(1);
        LocalDate end = month.atEndOfMonth();
        return new AwsBilling(accountId, start, end, "EC2", "us-east-1", BigDecimal.ONE, cost,
            "USD", false, "EC2", "BoxUsage", "i-bucket", "Usage", start, end, "forecast-bucket-" + month);
    }
}
