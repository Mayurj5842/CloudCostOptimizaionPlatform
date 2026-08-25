package com.cloudcostai.aws.service.impl;

import com.cloudcostai.aws.config.AwsBillingSyncProperties;
import com.cloudcostai.aws.config.AwsClientFactory;
import com.cloudcostai.aws.dto.response.AwsBillSummaryResponse;
import com.cloudcostai.aws.dto.response.AwsBillingServiceCostResponse;
import com.cloudcostai.aws.exception.AwsConnectionException;
import com.cloudcostai.aws.model.AwsBilling;
import com.cloudcostai.aws.model.AwsBillingSummary;
import com.cloudcostai.aws.repository.AwsBillingRepository;
import com.cloudcostai.aws.repository.AwsBillingSummaryRepository;
import com.cloudcostai.aws.service.AwsBillService;
import com.cloudcostai.aws.service.AwsConnectionSessionService;
import com.cloudcostai.aws.service.CurBillingImportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.costexplorer.CostExplorerClient;
import software.amazon.awssdk.services.costexplorer.model.DateInterval;
import software.amazon.awssdk.services.costexplorer.model.Dimension;
import software.amazon.awssdk.services.costexplorer.model.GetCostAndUsageRequest;
import software.amazon.awssdk.services.costexplorer.model.GetCostAndUsageResponse;
import software.amazon.awssdk.services.costexplorer.model.Granularity;
import software.amazon.awssdk.services.costexplorer.model.GroupDefinition;
import software.amazon.awssdk.services.costexplorer.model.GroupDefinitionType;
import software.amazon.awssdk.services.costexplorer.model.Group;
import software.amazon.awssdk.services.costexplorer.model.Metric;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class AwsBillingSyncServiceImpl implements AwsBillService {
    private static final Logger logger = LoggerFactory.getLogger(AwsBillingSyncServiceImpl.class);
    // AWS Cost Explorer metric key must be the SDK v2 metric name "UnblendedCost"
    private static final String COST_METRIC = "UnblendedCost";
    private static final String DEFAULT_CURRENCY = "USD";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;

    private final AwsClientFactory clientFactory;
    private final AwsConnectionSessionService sessionService;
    private final AwsBillingRepository billingRepository;
    private final AwsBillingSummaryRepository summaryRepository;
    private final AwsBillingSyncProperties syncProperties;
    private final CurBillingImportService curBillingImportService;

    public AwsBillingSyncServiceImpl(AwsClientFactory clientFactory,
                                     AwsConnectionSessionService sessionService,
                                     AwsBillingRepository billingRepository,
                                     AwsBillingSummaryRepository summaryRepository,
                                     AwsBillingSyncProperties syncProperties,
                                     CurBillingImportService curBillingImportService) {
        this.clientFactory = clientFactory;
        this.sessionService = sessionService;
        this.billingRepository = billingRepository;
        this.summaryRepository = summaryRepository;
        this.syncProperties = syncProperties;
        this.curBillingImportService = curBillingImportService;
    }

    @Override
    public AwsBillSummaryResponse getBillSummary(String connectionToken) {
        var session = sessionService.getSession(connectionToken);
        AwsBillingSummary summary = findLatestSummary(session.accountId());
        return summary == null ? createEmptySummary(session.accountId()) : toResponse(summary);
    }

    @Override
    public AwsBillSummaryResponse syncBilling(String connectionToken) {
        var session = sessionService.getSession(connectionToken);
        if (!syncProperties.isEnabled()) {
            return createEmptySummary(session.accountId());
        }
        try {
            return toResponse(curBillingImportService.importLatest(session.accessKey(), session.secretKey(), session.accountId()));
        } catch (RuntimeException exception) {
            logger.error("CUR billing import failed for account={}: {}", session.accountId(), exception.getMessage());
            throw new AwsConnectionException("CUR 2.0 billing data could not be imported from S3.", exception);
        }
        /*
         * COST EXPLORER TEMPORARILY DISABLED
         * CLOUDCOSTAI USES CUR 2.0 + S3 FOR BILLING DATA
         * DO NOT CALL AWS COST EXPLORER FROM DASHBOARD/BILLING FLOW
         */
        /*

            logger.info("ResultsByTime Count: {}", response.resultsByTime().size());
            logger.info("Cost Explorer response received successfully");
            response.resultsByTime().forEach(result -> {
                logger.info("Result period: {} - {}", result.timePeriod().start(), result.timePeriod().end());
                logger.info("Total: {}", result.total());
            });

            List<CostBreakdown> breakdown = collectBreakdown(response);
            BigDecimal totalCost = extractTotalAmount(response);
            String billingPeriod = String.format("%s - %s", start.format(DATE_FORMAT), end.minusDays(1).format(DATE_FORMAT));
            LocalDateTime now = LocalDateTime.now();
            boolean estimated = true;
            String currency = DEFAULT_CURRENCY;

            persistBillingRecords(session.accountId(), start, end.minusDays(1), breakdown, currency, estimated);
            var updatedSummary = persistBillingSummary(session.accountId(), billingPeriod, totalCost, currency, estimated, now);
            logger.info("Billing data synchronized for account={} period={} totalCost={}", session.accountId(), billingPeriod, totalCost);
            logger.info("=======================================");
            return toResponse(updatedSummary);
        } catch (AwsConnectionException exception) {
            logger.error("AWS BILLING FETCH FAILED for account={}: {}", session.accountId(), exception.getMessage());
            throw exception;
        } catch (RuntimeException exception) {
            logger.error("AWS BILLING FETCH FAILED for account={}", session.accountId(), exception);
            throw new AwsConnectionException("AWS billing data could not be synchronized. Please verify Cost Explorer permissions.", exception);
        }
        */
    }

    @Override
    public List<AwsBillingServiceCostResponse> getServiceBreakdown(String connectionToken) {
        var session = sessionService.getSession(connectionToken);
        AwsBillingSummary summary = findLatestSummary(session.accountId());
        if (summary == null) {
            return List.of();
        }
        LocalDate start = YearMonth.now().atDay(1);
        LocalDate end = start.plusMonths(1).minusDays(1);
        List<AwsBilling> billing = billingRepository.findByAwsAccountIdAndBillingPeriodStartAndBillingPeriodEnd(
                session.accountId(), start, end);
        return billing.stream()
                .collect(Collectors.groupingBy(AwsBilling::getServiceName, Collectors.mapping(AwsBilling::getCostAmount, Collectors.reducing(BigDecimal.ZERO, BigDecimal::add))))
                .entrySet().stream()
                .map(entry -> new AwsBillingServiceCostResponse(entry.getKey(), entry.getValue().setScale(2, RoundingMode.HALF_UP), DEFAULT_CURRENCY))
                .sorted((a, b) -> b.costAmount().compareTo(a.costAmount()))
                .toList();
    }

    private AwsBillingSummary findLatestSummary(String accountId) {
        return summaryRepository.findByAwsAccountIdOrderByBillingPeriodDesc(accountId, Pageable.ofSize(1))
                .stream().findFirst().orElse(null);
    }

    private boolean shouldSync(LocalDateTime lastSyncedAt) {
        return !syncProperties.isEnabled() || lastSyncedAt.plusHours(syncProperties.getIntervalHours()).isBefore(LocalDateTime.now());
    }

    private AwsBillSummaryResponse createEmptySummary(String accountId) {
        return new AwsBillSummaryResponse(accountId,
                new AwsBillSummaryResponse.BillingPeriod("", ""),
                "COST_EXPLORER_DISABLED",
                DEFAULT_CURRENCY,
                "Amazon Web Services",
                BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                "AWS Cost Explorer API is temporarily disabled.",
                "");
    }

    private AwsBillSummaryResponse toResponse(AwsBillingSummary summary) {
        var periodParts = summary.getBillingPeriod().split(" - ");
        return new AwsBillSummaryResponse(summary.getAwsAccountId(),
                new AwsBillSummaryResponse.BillingPeriod(periodParts[0], periodParts[1]),
                summary.isEstimated() ? "ESTIMATED" : "FINAL",
                summary.getCurrency(),
                "Amazon Web Services",
                summary.getTotalCost(),
                summary.getTotalCost(),
                "Billing data stored in PostgreSQL.",
                summary.getLastSyncedAt().toString());
    }

    private void persistBillingRecords(String awsAccountId,
                                       LocalDate start,
                                       LocalDate end,
                                       List<CostBreakdown> breakdown,
                                       String currency,
                                       boolean estimated) {
        for (CostBreakdown item : breakdown) {
            String serviceName = item.serviceName();
            String region = item.region();
            BigDecimal usageAmount = BigDecimal.ZERO;
            BigDecimal costAmount = item.costAmount();
            var existing = billingRepository.findByAwsAccountIdAndBillingPeriodStartAndBillingPeriodEndAndServiceNameAndRegion(
                    awsAccountId, start, end, serviceName, region).orElse(null);
            if (existing == null) {
                billingRepository.save(new AwsBilling(awsAccountId, start, end, serviceName, region, usageAmount, costAmount, currency, estimated,
                    serviceName, null, null, "", start, end, awsAccountId + "|" + start + "|" + end + "|" + serviceName + "|" + region));
            } else {
                existing.update(usageAmount, costAmount, currency, estimated);
                billingRepository.save(existing);
            }
        }
    }

    private AwsBillingSummary persistBillingSummary(String awsAccountId,
                                                    String billingPeriod,
                                                    BigDecimal totalCost,
                                                    String currency,
                                                    boolean estimated,
                                                    LocalDateTime lastSyncedAt) {
        var existing = summaryRepository.findByAwsAccountIdAndBillingPeriod(awsAccountId, billingPeriod).orElse(null);
        if (existing == null) {
            return summaryRepository.save(new AwsBillingSummary(awsAccountId, billingPeriod, totalCost, currency, estimated, lastSyncedAt));
        }
        existing.update(totalCost, currency, estimated, lastSyncedAt);
        return summaryRepository.save(existing);
    }

    private BigDecimal extractTotalAmount(GetCostAndUsageResponse response) {
        if (response == null || response.resultsByTime() == null || response.resultsByTime().isEmpty()) {
            throw new AwsConnectionException("Cost Explorer returned no results to extract total amount.");
        }
        var first = response.resultsByTime().get(0);
        // Prefer the top-level total if present
        if (first.total() != null && first.total().containsKey(COST_METRIC) && first.total().get(COST_METRIC) != null && first.total().get(COST_METRIC).amount() != null) {
            var metricValue = first.total().get(COST_METRIC);
            try {
                return new BigDecimal(metricValue.amount()).setScale(2, RoundingMode.HALF_UP);
            } catch (NumberFormatException nfe) {
                logger.error("Invalid cost amount returned by Cost Explorer: {}", metricValue.amount());
                throw new AwsConnectionException("Invalid cost amount returned by Cost Explorer.", nfe);
            }
        }

        // If top-level total is missing (common when grouping is requested), sum grouped metrics as a fallback
        logger.warn("Top-level total metric '{}' missing; attempting to sum group metrics as fallback.", COST_METRIC);
        BigDecimal sum = response.resultsByTime().stream()
                .flatMap(result -> result.groups().stream())
                .map(group -> {
                    var mv = group.metrics().get(COST_METRIC);
                    return mv == null || mv.amount() == null ? null : mv.amount();
                })
                .filter(amount -> amount != null)
                .map(amount -> {
                    try {
                        return new BigDecimal(amount);
                    } catch (NumberFormatException nfe) {
                        logger.error("Invalid grouped cost amount returned by Cost Explorer: {}", amount);
                        return null;
                    }
                })
                .filter(bd -> bd != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        if (sum.signum() == 0) {
            // Log available metric keys per result for diagnosis
            response.resultsByTime().forEach(r -> logger.error("Result totals keys: {} groups metrics keys: {}", r.total() == null ? "[]" : r.total().keySet(), r.groups().stream().map(g -> g.metrics().keySet()).toList()));
            throw new AwsConnectionException("Cost Explorer response missing total metric: " + COST_METRIC + ". Available metrics: " + (first.total() == null ? "[]" : first.total().keySet()));
        }

        return sum;
    }

    private List<CostBreakdown> collectBreakdown(GetCostAndUsageResponse response) {
        if (response.resultsByTime().isEmpty()) {
            return List.of();
        }
        return response.resultsByTime().stream()
                .flatMap(result -> result.groups().stream())
                .map(group -> new CostBreakdown(
                        group.keys().isEmpty() ? "Others" : group.keys().get(0),
                        null,
                        group.metrics().get(COST_METRIC) == null ? BigDecimal.ZERO : new BigDecimal(group.metrics().get(COST_METRIC).amount()).setScale(2, RoundingMode.HALF_UP)))
                .collect(Collectors.toList());
    }

    private record CostBreakdown(String serviceName, String region, BigDecimal costAmount) {}
}
