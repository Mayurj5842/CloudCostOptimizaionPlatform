package com.cloudcostai.aws.service;

import com.cloudcostai.aws.dto.request.CostSimulationRequest;
import com.cloudcostai.aws.dto.response.CostSimulationResponse;
import com.cloudcostai.aws.model.AwsBilling;
import com.cloudcostai.aws.model.AwsRecommendation;
import com.cloudcostai.aws.repository.AwsRecommendationRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class CostSimulatorService {
    private final AwsConnectionSessionService sessionService;
    private final AwsBillingSourceResolver billingSourceResolver;
    private final AwsRecommendationRepository recommendationRepository;

    public CostSimulatorService(AwsConnectionSessionService sessionService,
                                AwsBillingSourceResolver billingSourceResolver,
                                AwsRecommendationRepository recommendationRepository) {
        this.sessionService = sessionService;
        this.billingSourceResolver = billingSourceResolver;
        this.recommendationRepository = recommendationRepository;
    }

    public CostSimulationResponse simulate(String connectionToken, CostSimulationRequest request) {
        var session = sessionService.getSession(connectionToken);
        YearMonth selectedMonth = parseMonth(request == null ? null : request.month());
        List<UUID> selectedIds = request.recommendationIds();
        validateSelection(selectedIds);

        var billing = billingSourceResolver.resolve(session.accountId(), selectedMonth);
        if (billing.rows().isEmpty()) {
            return new CostSimulationResponse(session.accountId(), selectedMonth.toString(), null,
                    billing.dataSource(), "NO_BILLING_DATA", "No billing data is available for this month.",
                    null, null, null, null, List.of());
        }

        Map<UUID, AwsRecommendation> availableRecommendations = new HashMap<>();
        recommendationRepository.findByAwsAccountIdAndBillingMonthOrderByCreatedAtDesc(
                        session.accountId(), selectedMonth.toString())
                .forEach(item -> availableRecommendations.put(item.getId(), item));

        List<AwsRecommendation> selected = selectedIds.stream().map(id -> {
            AwsRecommendation recommendation = availableRecommendations.get(id);
            if (recommendation == null) {
                throw new IllegalArgumentException("A selected optimization is not available for this AWS account and billing month.");
            }
            return recommendation;
        }).toList();
        validateNonOverlapping(selected);

        List<CostSimulationResponse.Scenario> scenarios = new ArrayList<>();
        boolean missingSavings = false;
        BigDecimal totalSaving = BigDecimal.ZERO;
        for (AwsRecommendation recommendation : selected) {
            BigDecimal saving = recommendation.getPotentialSavings();
            boolean alreadyApplied = "applied".equals(recommendation.getStatus().toLowerCase(Locale.ROOT));
                boolean actionable = "new".equals(recommendation.getStatus().toLowerCase(Locale.ROOT))
                    || "reviewed".equals(recommendation.getStatus().toLowerCase(Locale.ROOT));
                if (!actionable || saving == null || saving.signum() < 0) {
                missingSavings = true;
                scenarios.add(new CostSimulationResponse.Scenario(recommendation.getId().toString(),
                    recommendation.getTitle(), recommendation.getService(), recommendation.getResourceId(),
                    recommendation.getRegion(), alreadyApplied ? "ALREADY_APPLIED" : !actionable ? "NOT_ACTIONABLE" : "INSUFFICIENT_PRICING_DATA",
                    null, alreadyApplied ? "This recommendation is already marked as applied." : !actionable
                        ? "This recommendation is not actionable in its current status."
                        : "No valid stored monthly savings estimate is available."));
                continue;
            }
            totalSaving = totalSaving.add(saving);
            scenarios.add(new CostSimulationResponse.Scenario(recommendation.getId().toString(),
                    recommendation.getTitle(), recommendation.getService(), recommendation.getResourceId(),
                    recommendation.getRegion(), "AVAILABLE", money(saving), null));
        }

        BigDecimal currentCost = billing.rows().stream().map(AwsBilling::getCostAmount)
                .filter(value -> value != null).reduce(BigDecimal.ZERO, BigDecimal::add);
        String currency = billing.rows().stream().map(AwsBilling::getCurrency)
                .filter(value -> value != null && !value.isBlank()).findFirst().orElse("USD");
        if (missingSavings) {
            return new CostSimulationResponse(session.accountId(), selectedMonth.toString(), currency,
                    billing.dataSource(), "INSUFFICIENT_PRICING_DATA",
                    "One or more selected optimizations do not have a usable stored savings estimate.",
                    money(currentCost), null, null, null, List.copyOf(scenarios));
        }
        BigDecimal estimatedCost = currentCost.subtract(totalSaving).max(BigDecimal.ZERO);
        return new CostSimulationResponse(session.accountId(), selectedMonth.toString(), currency,
                billing.dataSource(), "SUCCESS", null, money(currentCost), money(estimatedCost),
                money(totalSaving), money(totalSaving.multiply(BigDecimal.valueOf(12))), List.copyOf(scenarios));
    }

    private void validateSelection(List<UUID> recommendationIds) {
        if (recommendationIds == null || recommendationIds.isEmpty()) {
            throw new IllegalArgumentException("Select at least one optimization scenario to simulate.");
        }
        if (recommendationIds.stream().anyMatch(id -> id == null)
                || new HashSet<>(recommendationIds).size() != recommendationIds.size()) {
            throw new IllegalArgumentException("Selected optimization scenarios must be unique and valid.");
        }
    }

    private void validateNonOverlapping(List<AwsRecommendation> selected) {
        Set<String> resourceIds = new HashSet<>();
        Set<String> serviceOnly = new HashSet<>();
        Set<String> resourceServices = new HashSet<>();
        for (AwsRecommendation recommendation : selected) {
            String service = recommendation.getService().toLowerCase(Locale.ROOT);
            String resourceId = recommendation.getResourceId();
            if (resourceId == null || resourceId.isBlank()) {
                if (!serviceOnly.add(service) || resourceServices.contains(service)) {
                    throw new IllegalArgumentException("Select either a service-level or resource-level scenario for each service, not both.");
                }
            } else {
                if (!resourceIds.add(resourceId) || serviceOnly.contains(service)) {
                    throw new IllegalArgumentException("A resource or service cannot be simulated more than once.");
                }
                resourceServices.add(service);
            }
        }
    }

    private YearMonth parseMonth(String month) {
        if (month == null || month.isBlank()) {
            throw new IllegalArgumentException("Select a billing month in YYYY-MM format.");
        }
        try {
            YearMonth selected = YearMonth.parse(month);
            if (!selected.toString().equals(month)) throw new IllegalArgumentException();
            return selected;
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Invalid billing month. Please use YYYY-MM.");
        }
    }

    private BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}