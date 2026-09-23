package com.cloudcostai.aws.service;

import com.cloudcostai.aws.config.AwsBillingSyncProperties;
import com.cloudcostai.aws.repository.AwsBillingRepository;
import com.cloudcostai.aws.repository.AwsConnectionSessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.Instant;

@Component
@ConditionalOnProperty(name = "billing.sync.enabled", havingValue = "true")
public class CurBillingSyncScheduler {
    private static final Logger logger = LoggerFactory.getLogger(CurBillingSyncScheduler.class);

    private final AwsConnectionSessionRepository sessionRepository;
    private final CurBillingImportService importService;
    private final AwsBillingRepository billingRepository;
    private final AwsBillingSyncProperties properties;

    public CurBillingSyncScheduler(AwsConnectionSessionRepository sessionRepository,
                                   CurBillingImportService importService,
                                   AwsBillingRepository billingRepository,
                                   AwsBillingSyncProperties properties) {
        this.sessionRepository = sessionRepository;
        this.importService = importService;
        this.billingRepository = billingRepository;
        this.properties = properties;
    }

    @Scheduled(
            fixedDelayString = "${billing.sync.interval-hours:24}h",
            initialDelayString = "${billing.sync.initial-delay-minutes:5}m")
    public void synchronizeActiveSessions() {
        logger.info("CUR sync started");
        int attempted = 0;
        int imported = 0;
        for (var session : sessionRepository.findAll()) {
            if (!session.getExpiresAt().isAfter(Instant.now())) continue;
            attempted++;
            try {
                if (importService.importLatestIfNewer(session.getAccessKey(), session.getSecretKey(), session.getAccountId()).isPresent()) {
                    imported++;
                    LocalDate start = YearMonth.now(properties.getTimeZone()).atDay(1);
                    LocalDate end = start.plusMonths(1);
                    logger.info("Current month imported cost for account={}: {}", session.getAccountId(),
                            billingRepository.sumCost(session.getAccountId(), start, end));
                }
            } catch (RuntimeException exception) {
                logger.error("CUR sync failed for account={}: {}", session.getAccountId(), exception.getMessage());
            }
        }
        logger.info("CUR sync completed attemptedSessions={} importedDeliveries={}", attempted, imported);
    }
}