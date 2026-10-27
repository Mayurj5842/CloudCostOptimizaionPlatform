package com.cloudcostai.aws.service;

import com.cloudcostai.aws.dto.request.CostSimulationRequest;
import com.cloudcostai.aws.dto.response.CostSimulationResponse;
import com.cloudcostai.aws.model.AwsBilling;
import com.cloudcostai.aws.model.AwsRecommendation;
import com.cloudcostai.aws.model.ResourceIds;
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
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Projects a possible future cost from stored billing plus either saved/heuristic savings estimates for selected
 * recommendations or an explicit what-if reduction. Estimates are labelled by basis and never silently fabricated:
 * a scenario without any usable pricing basis is reported as {@code INSUFFICIENT_PRICING_DATA}.
 */
@Service
public class CostSimulatorService {
    /** Heuristic share of a service's spend assumed recoverable when only service-level billing evidence exists. */
    private static final BigDecimal SERVICE_OPTIMIZATION_RATE = new BigDecimal("0.10");
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

    private static final String MODE_RECOMMENDATIONS = "RECOMMENDATIONS";
    private static final String MODE_WHAT_IF = "WHAT_IF";

    private static final String BASIS_STORED = "STORED_ESTIMATE";
    private static final String BASIS_RESOURCE = "DERIVED_RESOURCE_COST";
    private static final String BASIS_SERVICE = "DERIVED_SERVICE_RATE";
    private static final String BASIS_WHAT_IF = "WHAT_IF";

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
        List<UUID> selectedIds = request == null ? null : request.recommendationIds();
        BigDecimal whatIfPercent = request == null ? null : request.whatIfReductionPercent();
        boolean whatIfMode = whatIfPercent != null;
        if (whatIfMode) {
            validateWhatIf(whatIfPercent, selectedIds);
        } else {
            validateSelection(selectedIds);
        }
        String mode = whatIfMode ? MODE_WHAT_IF : MODE_RECOMMENDATIONS;

        var billing = billingSourceResolver.resolve(session.accountId(), selectedMonth);
        if (billing.rows().isEmpty()) {
            return new CostSimulationResponse(session.accountId(), selectedMonth.toString(), null,
                    billing.dataSource(), mode, "NO_BILLING_DATA", "No billing data is available for this month.",
                    null, null, null, null, whatIfMode ? money(whatIfPercent) : null, List.of());
        }
        BigDecimal currentCost = money(sumCost(billing.rows()));
        String currency = currency(billing.rows());

        if (whatIfMode) {
            return whatIf(session.accountId(), selectedMonth, currency, billing.dataSource(), currentCost, whatIfPercent);
        }
        return recommendations(session.accountId(), selectedMonth, currency, billing.dataSource(), currentCost,
                billing.rows(), selectedIds);
    }

    private CostSimulationResponse whatIf(String accountId, YearMonth month, String currency, String dataSource,
                                          BigDecimal currentCost, BigDecimal percent) {
        BigDecimal saving = money(currentCost.multiply(percent).divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP));
        BigDecimal estimatedCost = currentCost.subtract(saving).max(BigDecimal.ZERO);
        String label = percent.stripTrailingZeros().toPlainString();
        CostSimulationResponse.Scenario scenario = new CostSimulationResponse.Scenario(null,
                "What-if: " + label + "% reduction", null, null, null, "AVAILABLE", BASIS_WHAT_IF, saving, null);
        return new CostSimulationResponse(accountId, month.toString(), currency, dataSource, MODE_WHAT_IF, "SUCCESS", null,
                currentCost, money(estimatedCost), saving, money(saving.multiply(BigDecimal.valueOf(12))),
                money(percent), List.of(scenario));
    }

    private CostSimulationResponse recommendations(String accountId, YearMonth month, String currency, String dataSource,
                                                   BigDecimal currentCost, List<AwsBilling> billingRows, List<UUID> selectedIds) {
        Map<UUID, AwsRecommendation> availableRecommendations = new HashMap<>();
        recommendationRepository.findByAwsAccountIdAndBillingMonthOrderByCreatedAtDesc(accountId, month.toString())
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
            String status = recommendation.getStatus() == null ? "" : recommendation.getStatus().toLowerCase(Locale.ROOT);
            boolean alreadyApplied = "applied".equals(status);
            boolean actionable = "new".equals(status) || "reviewed".equals(status);
            Saving saving = actionable ? deriveSaving(recommendation, billingRows) : null;
            if (!actionable || saving == null) {
                missingSavings = true;
                scenarios.add(new CostSimulationResponse.Scenario(recommendation.getId().toString(),
                        recommendation.getTitle(), recommendation.getService(), recommendation.getResourceId(),
                        recommendation.getRegion(), alreadyApplied ? "ALREADY_APPLIED" : !actionable ? "NOT_ACTIONABLE" : "INSUFFICIENT_PRICING_DATA",
                        null, null, alreadyApplied ? "This recommendation is already marked as applied."
                        : !actionable ? "This recommendation is not actionable in its current status."
                        : "No billing-backed savings estimate could be derived for this optimization."));
                continue;
            }
            totalSaving = totalSaving.add(saving.amount());
            scenarios.add(new CostSimulationResponse.Scenario(recommendation.getId().toString(),
                    recommendation.getTitle(), recommendation.getService(), recommendation.getResourceId(),
                    recommendation.getRegion(), "AVAILABLE", saving.basis(), money(saving.amount()), null));
        }

        if (missingSavings) {
            return new CostSimulationResponse(accountId, month.toString(), currency, dataSource, MODE_RECOMMENDATIONS,
                    "INSUFFICIENT_PRICING_DATA",
                    "One or more selected optimizations do not have a usable stored or billing-backed savings estimate.",
                    currentCost, null, null, null, null, List.copyOf(scenarios));
        }
        BigDecimal estimatedCost = currentCost.subtract(totalSaving).max(BigDecimal.ZERO);
        return new CostSimulationResponse(accountId, month.toString(), currency, dataSource, MODE_RECOMMENDATIONS, "SUCCESS",
                null, currentCost, money(estimatedCost), money(totalSaving),
                money(totalSaving.multiply(BigDecimal.valueOf(12))), null, List.copyOf(scenarios));
    }

    /**
     * Resolves a monthly saving estimate for a finding. A stored estimate always wins; otherwise the saving is derived
     * from the resource's own billed cost, or, failing that, a conservative share of the matching service's billed cost.
     * Returns {@code null} when no billing-backed estimate exists so the caller can report insufficient data instead of
     * guessing.
     */
    private Saving deriveSaving(AwsRecommendation recommendation, List<AwsBilling> billingRows) {
        BigDecimal stored = recommendation.getPotentialSavings();
        if (stored != null && stored.signum() >= 0) {
            return new Saving(stored, BASIS_STORED);
        }
        if (ResourceIds.isPresent(recommendation.getResourceId())) {
            BigDecimal matched = billingRows.stream()
                    .filter(row -> sameResourceId(row.getResourceId(), recommendation.getResourceId()))
                    .filter(row -> serviceMatches(row.getServiceName(), recommendation.getService()))
                    .map(AwsBilling::getCostAmount).filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            if (matched.signum() > 0) {
                return new Saving(matched, BASIS_RESOURCE);
            }
        }
        BigDecimal serviceCost = billingRows.stream()
                .filter(row -> serviceMatches(row.getServiceName(), recommendation.getService()))
                .map(AwsBilling::getCostAmount).filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (serviceCost.signum() > 0) {
            return new Saving(serviceCost.multiply(SERVICE_OPTIMIZATION_RATE), BASIS_SERVICE);
        }
        return null;
    }

    private boolean sameResourceId(String first, String second) {
        return first != null && second != null && first.trim().equalsIgnoreCase(second.trim());
    }

    private boolean serviceMatches(String billingService, String recommendationService) {
        if (billingService == null || recommendationService == null) {
            return false;
        }
        String left = billingService.trim().toLowerCase(Locale.ROOT);
        String right = recommendationService.trim().toLowerCase(Locale.ROOT);
        return !left.isEmpty() && !right.isEmpty() && (left.equals(right) || left.contains(right) || right.contains(left));
    }

    private void validateSelection(List<UUID> recommendationIds) {
        if (recommendationIds == null || recommendationIds.isEmpty()) {
            throw new IllegalArgumentException("Select at least one optimization scenario to simulate.");
        }
        if (recommendationIds.stream().anyMatch(Objects::isNull)
                || new HashSet<>(recommendationIds).size() != recommendationIds.size()) {
            throw new IllegalArgumentException("Selected optimization scenarios must be unique and valid.");
        }
    }

    private void validateWhatIf(BigDecimal percent, List<UUID> recommendationIds) {
        if (recommendationIds != null && !recommendationIds.isEmpty()) {
            throw new IllegalArgumentException("Choose either optimization scenarios or a what-if reduction, not both.");
        }
        if (percent.signum() <= 0 || percent.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalArgumentException("Enter a what-if reduction percentage between 0 and 100.");
        }
    }

    private void validateNonOverlapping(List<AwsRecommendation> selected) {
        Set<String> resourceIds = new HashSet<>();
        Set<String> serviceOnly = new HashSet<>();
        Set<String> resourceServices = new HashSet<>();
        for (AwsRecommendation recommendation : selected) {
            String service = recommendation.getService().toLowerCase(Locale.ROOT);
            String resourceId = recommendation.getResourceId();
            if (!ResourceIds.isPresent(resourceId)) {
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

    private BigDecimal sumCost(List<AwsBilling> rows) {
        return rows.stream().map(AwsBilling::getCostAmount).filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private String currency(List<AwsBilling> rows) {
        return rows.stream().map(AwsBilling::getCurrency)
                .filter(value -> value != null && !value.isBlank()).findFirst().orElse("USD");
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

    private record Saving(BigDecimal amount, String basis) {}
}
