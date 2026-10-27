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
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;
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
        var accountRows = export.rows().stream().filter(row -> accountId.equals(first(row.values(),
            "line_item_usage_account_id", "bill_payer_account_id", "line_item_account_id"))).toList();
        boolean resourceIdColumnPresent = export.rows().stream()
            .anyMatch(row -> row.values().containsKey("line_item_resource_id"));
        long accountRowsWithResourceId = accountRows.stream()
            .filter(row -> !row.values().getOrDefault("line_item_resource_id", "").isBlank())
            .count();
        logger.info("CUR account resource-id diagnostics: columnPresent={}, accountRows={}, accountRowsWithResourceId={}",
            resourceIdColumnPresent, accountRows.size(), accountRowsWithResourceId);
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
            String rowCurrency = first(value, "line_item_currency_code", "pricing_currency");
            if (!rowCurrency.isBlank()) currency = rowCurrency;
            String serviceName = valueOr(value, "product_product_name", "line_item_product_code", "Unknown service");
            String region = region(value);
            String productName = value.get("product_product_name");
            String usageType = value.get("line_item_usage_type");
            String resourceId = normalizedResourceId(value.get("line_item_resource_id"));
            String lineItemType = value.get("line_item_line_item_type");
            BigDecimal usageAmount = decimalOrZero(value.get("line_item_usage_amount"));
            String lineItemId = first(value, "identity_line_item_id", "line_item_id");
            String key = lineItemId.isBlank()
                ? hash(accountId + "|" + value)
                : hash(accountId + "|CUR_2_0|" + lineItemId);
            Optional<AwsBilling> existing = billingRepository.findByRecordKey(key);
            if (existing.isEmpty() && !lineItemId.isBlank()) {
                List<AwsBilling> possibleRevisions = billingRepository.findPotentialCurRevision(accountId, start, end,
                    serviceName, usageType, lineItemType, usageAmount, cost, rowCurrency.isBlank() ? currency : rowCurrency);
                if (possibleRevisions.size() == 1) {
                    existing = Optional.of(possibleRevisions.get(0));
                }
            }
            if (existing.isPresent()) {
                existing.get().refreshCurImport(serviceName, region, usageAmount, cost, currency, productName,
                    usageType, resourceId, lineItemType, start, end, key);
                billingRepository.save(existing.get());
                skipped++;
                continue;
            }
            billingRepository.save(new AwsBilling(accountId, start, end, serviceName, region, usageAmount, cost,
                currency, false, productName, usageType, resourceId, lineItemType, start, end, key));
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
    private String region(Map<String, String> values) {
        String regionCode = first(values, "product_region_code", "product_region");
        if (!regionCode.isBlank()) return regionCode.trim();
        String availabilityZone = values.getOrDefault("line_item_availability_zone", "").trim();
        if (availabilityZone.matches("^.+-\\d+[a-z]$")) return availabilityZone.substring(0, availabilityZone.length() - 1);
        return "Global / No Region";
    }
    private String normalizedResourceId(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
    private BigDecimal decimal(String value) { try { return value == null || value.isBlank() ? null : new BigDecimal(value); } catch (NumberFormatException e) { return null; } }
    private BigDecimal decimalOrZero(String value) { BigDecimal result = decimal(value); return result == null ? BigDecimal.ZERO : result; }
    private LocalDate date(String value) { try { return value == null || value.isBlank() ? null : OffsetDateTime.parse(value).toLocalDate(); } catch (RuntimeException e) { try { return LocalDate.parse(value.substring(0, 10)); } catch (RuntimeException ignored) { return null; } } }
    private String hash(String value) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); } catch (Exception e) { throw new IllegalStateException(e); } }
}
