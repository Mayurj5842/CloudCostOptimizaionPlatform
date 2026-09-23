package com.cloudcostai.aws.service;

import com.cloudcostai.aws.config.AwsBillingSyncProperties;
import com.cloudcostai.aws.model.AwsBilling;
import com.cloudcostai.aws.model.AwsBillingSummary;
import com.cloudcostai.aws.repository.AwsBillingRepository;
import com.cloudcostai.aws.repository.AwsBillingSummaryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;

@Service
public class CurBillingImportService {
    private static final Logger logger = LoggerFactory.getLogger(CurBillingImportService.class);
    private final CurS3Service s3Service;
    private final AwsBillingRepository billingRepository;
    private final AwsBillingSummaryRepository summaryRepository;
    private final AwsBillingSyncProperties properties;

    public CurBillingImportService(CurS3Service s3Service, AwsBillingRepository billingRepository,
                                   AwsBillingSummaryRepository summaryRepository, AwsBillingSyncProperties properties) {
        this.s3Service = s3Service;
        this.billingRepository = billingRepository;
        this.summaryRepository = summaryRepository;
        this.properties = properties;
    }

    @Transactional
    public AwsBillingSummary importLatest(String accessKey, String secretKey, String accountId) {
        if (!properties.isEnabled()) throw new IllegalStateException("CUR billing import is disabled.");
        logger.info("CUR 2.0 import started for account={}", accountId);
        CurS3Service.Export export = s3Service.readLatest(accessKey, secretKey);
        int inserted = 0;
        int skipped = 0;
        BigDecimal total = BigDecimal.ZERO;
        LocalDate periodStart = null;
        LocalDate periodEnd = null;
        String currency = "USD";
        for (CurS3Service.Row row : export.rows()) {
            Map<String, String> value = row.values();
            String rowAccount = first(value, "line_item_usage_account_id", "bill_payer_account_id", "line_item_account_id");
            if (!accountId.equals(rowAccount)) continue;
            BigDecimal cost = decimal(value.get("line_item_unblended_cost"));
            LocalDate start = date(value.get("line_item_usage_start_date"));
            LocalDate end = date(value.get("line_item_usage_end_date"));
            if (start == null || cost == null) continue;
            if (end == null) end = start;
            total = total.add(cost);
            periodStart = periodStart == null || start.isBefore(periodStart) ? start : periodStart;
            periodEnd = periodEnd == null || end.isAfter(periodEnd) ? end : periodEnd;
            String key = hash(accountId + "|" + value);
            if (billingRepository.existsByRecordKey(key)) { skipped++; continue; }
            String rowCurrency = first(value, "line_item_currency_code", "pricing_currency");
            if (!rowCurrency.isBlank()) currency = rowCurrency;
            billingRepository.save(new AwsBilling(accountId, start, end,
                    valueOr(value, "product_product_name", "line_item_product_code", "Unknown service"),
                    valueOr(value, "product_region", "line_item_availability_zone", "Global / No Region"),
                    decimalOrZero(value.get("line_item_usage_amount")), cost, currency, false,
                    value.get("product_product_name"), value.get("line_item_usage_type"),
                    value.get("line_item_resource_id"), value.get("line_item_line_item_type"), start, end, key));
            inserted++;
        }
        logger.info("CUR records processed: {}", export.rows().size());
        logger.info("New records inserted: {}", inserted);
        logger.info("Duplicate records skipped: {}", skipped);
        if (periodStart == null) throw new IllegalStateException("CUR data is not available for account " + accountId + ".");
        String period = periodStart + " - " + periodEnd;
        AwsBillingSummary summary = summaryRepository.findByAwsAccountIdAndBillingPeriod(accountId, period).orElse(null);
        if (summary == null) summary = new AwsBillingSummary(accountId, period, total, currency, false, LocalDateTime.now());
        else summary.update(total, currency, false, LocalDateTime.now());
        logger.info("CUR import completed for account={} totalCost={}", accountId, total);
        return summaryRepository.save(summary);
    }

    public Optional<AwsBillingSummary> importLatestIfNewer(String accessKey, String secretKey, String accountId) {
        return Optional.of(importLatest(accessKey, secretKey, accountId));
    }

    private String first(Map<String, String> values, String... keys) {
        for (String key : keys) if (!values.getOrDefault(key, "").isBlank()) return values.get(key);
        return "";
    }
    private String valueOr(Map<String, String> values, String first, String second, String fallback) {
        String value = first(values, first, second); return value.isBlank() ? fallback : value;
    }
    private BigDecimal decimal(String value) { try { return value == null || value.isBlank() ? null : new BigDecimal(value); } catch (NumberFormatException e) { return null; } }
    private BigDecimal decimalOrZero(String value) { BigDecimal result = decimal(value); return result == null ? BigDecimal.ZERO : result; }
    private LocalDate date(String value) { try { return value == null || value.isBlank() ? null : OffsetDateTime.parse(value).toLocalDate(); } catch (RuntimeException e) { try { return LocalDate.parse(value.substring(0, 10)); } catch (RuntimeException ignored) { return null; } } }
    private String hash(String value) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); } catch (Exception e) { throw new IllegalStateException(e); } }
}
