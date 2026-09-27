package com.cloudcostai.aws.service;

import com.cloudcostai.aws.model.AwsBilling;
import com.cloudcostai.aws.repository.AwsBillingRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class AwsBillingSourceResolver {
    private static final String CUR = "CUR_2_0";
    private static final String COST_EXPLORER = "COST_EXPLORER";
    private final AwsBillingRepository billingRepository;

    public AwsBillingSourceResolver(AwsBillingRepository billingRepository) {
        this.billingRepository = billingRepository;
    }

    public ResolvedBilling resolve(String accountId, YearMonth month) {
        LocalDate start = month.atDay(1);
        LocalDate end = month.plusMonths(1).atDay(1);
        return new ResolvedBilling(CUR, rows(accountId, CUR, start, end), start, end);
    }

    public ResolvedBilling resolveForDisplay(String accountId, LocalDate start, LocalDate end) {
        List<AwsBilling> curRows = rows(accountId, CUR, start, end);
        List<AwsBilling> liveRows = rows(accountId, COST_EXPLORER, start, end);
        Set<YearMonth> liveMonths = liveRows.stream()
                .map(this::effectiveDate)
                .filter(date -> date != null)
                .map(YearMonth::from)
                .collect(Collectors.toSet());
        List<AwsBilling> retainedCurRows = curRows.stream()
                .filter(row -> effectiveDate(row) == null || !liveMonths.contains(YearMonth.from(effectiveDate(row))))
                .toList();
        List<AwsBilling> combinedRows = Stream.concat(retainedCurRows.stream(), liveRows.stream())
                .sorted(Comparator.comparing(this::effectiveDate))
                .toList();
        String source = liveRows.isEmpty() ? CUR : retainedCurRows.isEmpty() ? COST_EXPLORER : "MIXED";
        return new ResolvedBilling(source, combinedRows, start, end);
    }

    public List<AwsBilling> rows(String accountId, String source, LocalDate start, LocalDate end) {
        return billingRepository.findByAwsAccountIdAndDataSourceOrderByBillingPeriodStartAsc(accountId, source).stream()
                .filter(row -> effectiveDate(row) != null && !effectiveDate(row).isBefore(start) && effectiveDate(row).isBefore(end))
                .toList();
    }

    private LocalDate effectiveDate(AwsBilling row) {
        return row.getUsageStartDate() == null ? row.getBillingPeriodStart() : row.getUsageStartDate();
    }

    public record ResolvedBilling(String dataSource, List<AwsBilling> rows, LocalDate start, LocalDate end) {}
}
