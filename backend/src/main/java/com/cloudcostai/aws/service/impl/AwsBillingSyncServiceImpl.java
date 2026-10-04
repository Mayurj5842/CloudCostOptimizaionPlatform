package com.cloudcostai.aws.service.impl;

import com.cloudcostai.aws.config.AwsBillingSyncProperties;
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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class AwsBillingSyncServiceImpl implements AwsBillService {
    private static final Logger logger = LoggerFactory.getLogger(AwsBillingSyncServiceImpl.class);
    private static final String DEFAULT_CURRENCY = "USD";
    private final AwsConnectionSessionService sessionService;
    private final AwsBillingRepository billingRepository;
    private final AwsBillingSummaryRepository summaryRepository;
    private final AwsBillingSyncProperties syncProperties;
    private final CurBillingImportService curBillingImportService;

    public AwsBillingSyncServiceImpl(AwsConnectionSessionService sessionService,
                                     AwsBillingRepository billingRepository,
                                     AwsBillingSummaryRepository summaryRepository,
                                     AwsBillingSyncProperties syncProperties,
                                     CurBillingImportService curBillingImportService) {
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
            throw new AwsConnectionException("CUR billing import is disabled. Enable the existing CUR 2.0 S3 sync configuration and retry.");
        }
        try {
            return toResponse(curBillingImportService.importLatest(session.accessKey(), session.secretKey(), session.accountId()));
        } catch (RuntimeException exception) {
            logger.error("CUR billing import failed for account={}: {}", session.accountId(), exception.getMessage());
            String message = exception.getMessage();
            if ("AWS_BILLING_S3_BUCKET is not configured.".equals(message)
                    || "AWS_BILLING_S3_PREFIX is not configured.".equals(message)) {
                throw new AwsConnectionException(message, exception);
            }
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
                "NO_CUR_DATA",
                DEFAULT_CURRENCY,
                "Amazon Web Services",
                BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                "No CUR billing data is available for this account.",
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

}
