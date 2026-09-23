package com.cloudcostai.aws.service.impl;

import com.cloudcostai.aws.config.AwsClientFactory;
import com.cloudcostai.aws.dto.response.AwsLiveCostResponse;
import com.cloudcostai.aws.exception.AwsConnectionException;
import com.cloudcostai.aws.service.AwsConnectionSessionService;
import com.cloudcostai.aws.service.AwsLiveCostService;
import com.cloudcostai.aws.repository.AwsBillingRepository;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.costexplorer.CostExplorerClient;
import software.amazon.awssdk.services.costexplorer.model.DateInterval;
import software.amazon.awssdk.services.costexplorer.model.GetCostAndUsageRequest;
import software.amazon.awssdk.services.costexplorer.model.GroupDefinition;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class AwsLiveCostServiceImpl implements AwsLiveCostService {
    private final AwsClientFactory clientFactory;
    private final AwsConnectionSessionService sessionService;
    private final AwsBillingRepository billingRepository;

    public AwsLiveCostServiceImpl(AwsClientFactory clientFactory, AwsConnectionSessionService sessionService, AwsBillingRepository billingRepository) {
        this.clientFactory = clientFactory;
        this.sessionService = sessionService;
        this.billingRepository = billingRepository;
    }

    @Override
    public AwsLiveCostResponse fetch(String connectionToken, String month) {
        var session = sessionService.getSession(connectionToken);
        DateRange range = dateRange(month);

        try (CostExplorerClient client = clientFactory.createCostExplorerClient(session.accessKey(), session.secretKey())) {
            var response = client.getCostAndUsage(GetCostAndUsageRequest.builder()
                    .timePeriod(DateInterval.builder().start(range.start().toString()).end(range.end().toString()).build())
                        .granularity("DAILY")
                    .metrics("UnblendedCost")
                            .groupBy(GroupDefinition.builder().type("DIMENSION").key("REGION").build(),
                                GroupDefinition.builder().type("DIMENSION").key("SERVICE").build())
                    .build());
            if (response.resultsByTime() == null || response.resultsByTime().isEmpty()
                    || response.resultsByTime().get(0).total() == null
                    || response.resultsByTime().get(0).total().get("UnblendedCost") == null) {
                return new AwsLiveCostResponse(session.accountId(), range.start().toString(), range.end().minusDays(1).toString(), "USD", null, List.of(), List.of(),
                        "COST_EXPLORER", LocalDateTime.now().toString(), "NO_DATA", "AWS Cost Explorer returned no cost data for this period.");
            }
                String currency = "USD";
                String fetchedAt = LocalDateTime.now().toString();
                java.util.List<StoredCost> storedCosts = new java.util.ArrayList<>();
                response.resultsByTime().forEach(result -> result.groups().forEach(group -> {
                    String region = group.keys().isEmpty() ? "NoRegion" : group.keys().get(0);
                    String service = group.keys().size() < 2 ? "Other AWS services" : group.keys().get(1);
                    var value = group.metrics().get("UnblendedCost");
                    if (value != null) storedCosts.add(new StoredCost(LocalDate.parse(result.timePeriod().start()), region, service, new BigDecimal(value.amount()), value.unit() == null ? "USD" : value.unit()));
                }));
                if (!storedCosts.isEmpty()) currency = storedCosts.get(0).currency();
                persistCosts(session.accountId(), range, storedCosts, fetchedAt);
                    String responseCurrency = currency;
                List<AwsLiveCostResponse.RegionCost> regions = storedCosts.stream()
                    .collect(Collectors.groupingBy(StoredCost::region, Collectors.reducing(BigDecimal.ZERO, StoredCost::cost, BigDecimal::add)))
                        .entrySet().stream().map(entry -> new AwsLiveCostResponse.RegionCost(entry.getKey(), entry.getValue(), responseCurrency, fetchedAt)).toList();
                BigDecimal cost = regions.stream().map(AwsLiveCostResponse.RegionCost::cost).reduce(BigDecimal.ZERO, BigDecimal::add);
                List<AwsLiveCostResponse.ServiceCost> services = storedCosts.stream()
                    .collect(Collectors.groupingBy(StoredCost::service, Collectors.reducing(BigDecimal.ZERO, StoredCost::cost, BigDecimal::add)))
                    .entrySet().stream().map(entry -> new AwsLiveCostResponse.ServiceCost(entry.getKey(), entry.getValue(), responseCurrency, fetchedAt)).toList();
                AwsLiveCostResponse result = new AwsLiveCostResponse(session.accountId(), range.start().toString(), range.end().minusDays(1).toString(),
                    currency, cost, regions, services, "COST_EXPLORER", fetchedAt, "SUCCESS", null);
            return result;
        } catch (DataAccessException exception) {
            throw new AwsConnectionException("AWS Cost Explorer returned data, but PostgreSQL storage failed: " + exception.getMessage(), exception);
        } catch (RuntimeException exception) {
            String detail = exception.getMessage() == null || exception.getMessage().isBlank()
                    ? "Verify Cost Explorer permission and try again."
                    : exception.getMessage();
            throw new AwsConnectionException("AWS Cost Explorer request failed: " + detail, exception);
        }
    }

    private DateRange dateRange(String month) {
        try {
            YearMonth selected = YearMonth.parse(month);
            return new DateRange(selected, selected.atDay(1), selected.plusMonths(1).atDay(1));
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Invalid billing month. Please use YYYY-MM.");
        }
    }

    private record DateRange(YearMonth month, LocalDate start, LocalDate end) {}
    private void persistCosts(String accountId, DateRange range, java.util.List<StoredCost> costs, String fetchedAt) {
        for (StoredCost stored : costs) {
            String recordKey = "COST_EXPLORER|" + accountId + "|" + stored.date() + "|" + stored.region() + "|" + stored.service();
            var existing = billingRepository.findByRecordKey(recordKey);
            if (existing.isPresent()) {
                existing.get().update(stored.cost(), stored.cost(), stored.currency(), false);
                billingRepository.save(existing.get());
            } else {
                billingRepository.save(new com.cloudcostai.aws.model.AwsBilling(accountId, stored.date(), stored.date(),
                        stored.service(), stored.region(), stored.cost(), stored.cost(), stored.currency(), false,
                        stored.service(), "UnblendedCost", null, "COST_EXPLORER", stored.date(), stored.date(),
                        recordKey, "COST_EXPLORER"));
            }
        }
    }

    private record StoredCost(LocalDate date, String region, String service, BigDecimal cost, String currency) {}
}
