package com.cloudcostai.aws.service.impl;

import com.cloudcostai.aws.dto.response.AwsRecommendationResponse;
import com.cloudcostai.aws.dto.response.AwsRecommendationResponse.Recommendation;
import com.cloudcostai.aws.dto.response.AwsResourceResponse;
import com.cloudcostai.aws.model.AwsBilling;
import com.cloudcostai.aws.model.AwsRecommendation;
import com.cloudcostai.aws.model.ResourceIds;
import com.cloudcostai.aws.repository.AwsBillingRepository;
import com.cloudcostai.aws.repository.AwsRecommendationRepository;
import com.cloudcostai.aws.service.AwsConnectionSessionService;
import com.cloudcostai.aws.service.AwsRecommendationService;
import com.cloudcostai.aws.service.AwsBillingSourceResolver;
import com.cloudcostai.aws.service.AwsResourceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import software.amazon.awssdk.awscore.exception.AwsServiceException;

@Service
public class AwsRecommendationServiceImpl implements AwsRecommendationService {
    private static final Logger logger = LoggerFactory.getLogger(AwsRecommendationServiceImpl.class);
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    private static final BigDecimal COST_CONCENTRATION_THRESHOLD = BigDecimal.valueOf(0.25);
    private final AwsConnectionSessionService sessionService;
    private final AwsBillingRepository billingRepository;
    private final AwsRecommendationRepository recommendationRepository;
    private final AwsBillingSourceResolver billingSourceResolver;
    private final AwsResourceService resourceService;

    @Autowired
    public AwsRecommendationServiceImpl(AwsConnectionSessionService sessionService,
                                        AwsBillingRepository billingRepository,
                                        AwsRecommendationRepository recommendationRepository,
                                        AwsBillingSourceResolver billingSourceResolver,
                                        AwsResourceService resourceService) {
        this.sessionService = sessionService;
        this.billingRepository = billingRepository;
        this.recommendationRepository = recommendationRepository;
        this.billingSourceResolver = billingSourceResolver;
        this.resourceService = resourceService;
    }

    public AwsRecommendationServiceImpl(AwsConnectionSessionService sessionService,
                                        AwsBillingRepository billingRepository,
                                        AwsRecommendationRepository recommendationRepository,
                                        AwsBillingSourceResolver billingSourceResolver) {
        this(sessionService, billingRepository, recommendationRepository, billingSourceResolver, null);
    }

    @Override
    @Transactional(readOnly = true)
    public AwsRecommendationResponse getRecommendations(String connectionToken) {
        return getRecommendations(connectionToken, YearMonth.now().toString());
    }

    @Override
    @Transactional(readOnly = true)
    public AwsRecommendationResponse getRecommendations(String connectionToken, String month) {
        var session = sessionService.getSession(connectionToken);
        YearMonth selected = parseMonth(month, YearMonth.now());
        BillingEvidence billingEvidence = recommendationBillingRows(session.accountId(), selected);
        List<AwsBilling> rows = billingEvidence.rows();
        List<AwsBilling> currentRows = rows.stream()
            .filter(row -> inRange(row, selected.atDay(1), selected.plusMonths(1).atDay(1))).toList();
        String currency = currentRows.stream().map(AwsBilling::getCurrency)
            .filter(value -> value != null && !value.isBlank()).findFirst().orElse("USD");
        if (currentRows.isEmpty()) return response(session.accountId(), currency, List.of(), selected, "NO_DATA",
            "No stored Cost Explorer or CUR 2.0 billing data is available for this period.", null, false, "NOT_CHECKED", List.of());
        List<AwsBilling> previousRows = rows.stream()
            .filter(row -> inRange(row, selected.minusMonths(1).atDay(1), selected.atDay(1))).toList();
        List<AwsRecommendation> stored = recommendationRepository.findByAwsAccountIdAndBillingMonthOrderByCreatedAtDesc(session.accountId(), selected.toString());
        SupportResult validation = supportedRecommendations(session.accountId(), stored, currentRows, previousRows,
            connectionToken, selected, billingEvidence.dataSource());
        List<AwsRecommendation> supported = validation.recommendations();
        String status = supported.isEmpty() ? emptyStatus(validation.resourceEvidence(), currentRows, previousRows,
            billingEvidence.dataSource()) : "SUCCESS";
        String message = validation.resourceEvidence().permissionDenied()
            ? diagnostic(validation.resourceEvidence(), RESOURCE_PERMISSION_MESSAGE)
            : validation.resourceEvidence().checked() && !validation.resourceEvidence().available()
                ? RESOURCE_UNAVAILABLE_MESSAGE
                : "COST_EXPLORER".equals(billingEvidence.dataSource()) && supported.isEmpty()
                    ? "Stored Cost Explorer billing evidence is insufficient to validate a recommendation for this period."
                : "NO_RESOURCES".equals(status)
                    ? "AWS resource discovery found no EC2 or EBS resources in any enabled region for this account."
                    : supported.isEmpty() ? "Recommendations have not been generated for this period yet." : null;
        return response(session.accountId(), currency, supported, selected, status, message, sum(currentRows), false,
            resourceStatus(validation.resourceEvidence()), List.of(), billingEvidence.dataSource());
    }

    @Override
    @Transactional
    public AwsRecommendationResponse generateRecommendations(String connectionToken) {
        return generateRecommendations(connectionToken, YearMonth.now().toString());
    }

    @Override
    @Transactional
    public AwsRecommendationResponse generateRecommendations(String connectionToken, String month) {
        var session = sessionService.getSession(connectionToken);
        YearMonth selected = parseMonth(month, YearMonth.now());
        LocalDate start = selected.atDay(1);
        LocalDate end = selected.plusMonths(1).atDay(1);
        BillingEvidence billingEvidence = recommendationBillingRows(session.accountId(), selected);
        List<AwsBilling> rows = billingEvidence.rows();
        List<AwsBilling> currentRows = rows.stream().filter(row -> inRange(row, start, end)).toList();
        String currency = currentRows.stream().map(AwsBilling::getCurrency)
            .filter(value -> value != null && !value.isBlank()).findFirst().orElse("USD");
        if (currentRows.isEmpty()) return response(session.accountId(), currency, List.of(), selected, "NO_DATA",
            "No stored Cost Explorer or CUR 2.0 billing data is available for this period.", null, true,
            "NOT_CHECKED", List.of());
        List<AwsBilling> previousRows = rows.stream().filter(row -> inRange(row, selected.minusMonths(1).atDay(1), start)).toList();
        boolean costExplorer = "COST_EXPLORER".equals(billingEvidence.dataSource());
        List<AwsBilling> resourceRows = currentRows;
        ResourceEvidence resourceEvidence = costExplorer ? uncheckedResourceEvidence()
            : resourceEvidence(session.accountId(), connectionToken, resourceRows, true, true);
        List<RecommendationDraft> drafts = analyze(rows, resourceRows, start, end,
            selected.minusMonths(1).atDay(1), resourceEvidence, billingEvidence.dataSource());
        List<AwsRecommendation> generated = new ArrayList<>();
        for (RecommendationDraft draft : drafts) {
            AwsRecommendation entity = recommendationRepository.findByAwsAccountIdAndRecommendationKey(session.accountId(), draft.key())
                    .orElseGet(() -> new AwsRecommendation(session.accountId(), draft.key(), draft.title(), draft.category(), draft.severity(), draft.service(), draft.resourceId(), draft.region(), draft.currentCost(), draft.reason(), draft.recommendation(), draft.action(), draft.potentialSavings(), "New", selected.toString()));
            BigDecimal savings = draft.potentialSavings() == null ? entity.getPotentialSavings() : draft.potentialSavings();
            entity.update(draft.title(), draft.category(), draft.severity(), draft.service(), draft.resourceId(),
                draft.region(), draft.currentCost(), draft.reason(), draft.recommendation(), draft.action(), savings,
                entity.getStatus(), selected.toString());
            recommendationRepository.save(entity);
            generated.add(entity);
        }
        List<AwsRecommendation> stored = recommendationRepository.findByAwsAccountIdAndBillingMonthOrderByCreatedAtDesc(session.accountId(), selected.toString());
        List<AwsRecommendation> responseRows = stored.isEmpty() ? generated
            : stored.stream().filter(item -> isStillSupported(item, currentRows, previousRows, resourceRows,
                resourceEvidence, billingEvidence.dataSource())).toList();
        logger.info("Recommendation generation diagnostics: month={}, connectedRegion={}, billingSource={}, billingRows={}, selectedRows={}, resourceRows={}, resourceRowsWithResourceId={}, awsResourceAccountMatches={}, awsEc2Resources={}, awsEbsResources={}, stoppedEc2Resources={}, unattachedEbsResources={}, ec2BillingResourceMatches={}, ebsBillingResourceMatches={}, stoppedEc2Candidates={}, unattachedEbsCandidates={}, qualifiedRecommendations={}, returnedRecommendations={}, resourceDataStatus={}",
            selected, session.region(), billingEvidence.dataSource(), rows.size(), currentRows.size(), resourceRows.size(), resourceRows.stream().filter(this::hasResource).count(),
            resourceEvidence.accountMatches(), resourceEvidence.ec2Resources(), resourceEvidence.ebsResources(),
            resourceEvidence.stoppedEc2Resources(), resourceEvidence.unattachedEbsResources(),
            resourceEvidence.ec2BillingResourceMatches(), resourceEvidence.ebsBillingResourceMatches(), resourceEvidence.stoppedEc2().size(),
            resourceEvidence.unattachedEbs().size(), drafts.size(), responseRows.size(), resourceStatus(resourceEvidence));
        String status = responseRows.isEmpty() ? emptyStatus(resourceEvidence, resourceRows, previousRows,
            billingEvidence.dataSource()) : "SUCCESS";
        List<String> reasons = responseRows.isEmpty()
            ? evaluationReasons(resourceRows, responseRows, resourceEvidence, drafts.size(),
                billingEvidence.dataSource(), previousRows, selected) : List.of();
        String message = switch (status) {
            case "PERMISSION_DENIED" -> diagnostic(resourceEvidence, RESOURCE_PERMISSION_MESSAGE);
            case "NO_RESOURCES", "NO_RECOMMENDATIONS", "NO_RESOURCE_BILLING_MATCH",
                 "INSUFFICIENT_BILLING_EVIDENCE" -> String.join(" ", reasons);
            case "RESOURCE_UNAVAILABLE" -> diagnostic(resourceEvidence, RESOURCE_UNAVAILABLE_MESSAGE);
            default -> null;
        };
        return response(session.accountId(), currency, responseRows, selected, status, message, sum(currentRows),
            costExplorer || resourceEvidence.available(), resourceStatus(resourceEvidence), reasons, billingEvidence.dataSource());
    }

    @Override
    @Transactional
    public AwsRecommendationResponse updateStatus(String connectionToken, String recommendationId, String status) {
        return updateStatus(connectionToken, recommendationId, status, null);
    }

    @Override
    @Transactional
    public AwsRecommendationResponse updateStatus(String connectionToken, String recommendationId, String status, String month) {
        var session = sessionService.getSession(connectionToken);
        if (!List.of("New", "Applied", "Dismissed").contains(status)) throw new IllegalArgumentException("Status must be New, Applied, or Dismissed.");
        AwsRecommendation entity = recommendationRepository.findById(java.util.UUID.fromString(recommendationId))
                .filter(item -> session.accountId().equals(item.getAwsAccountId()))
                .orElseThrow(() -> new IllegalArgumentException("Recommendation was not found for this account."));
        entity.update(entity.getTitle(), entity.getCategory(), entity.getSeverity(), entity.getService(), entity.getResourceId(), entity.getRegion(), entity.getCurrentCost(), entity.getReason(), entity.getRecommendation(), entity.getAction(), entity.getPotentialSavings(), status);
        recommendationRepository.save(entity);
        return month == null || month.isBlank() ? getRecommendations(connectionToken) : getRecommendations(connectionToken, month);
    }

    private List<RecommendationDraft> analyze(List<AwsBilling> rows, List<AwsBilling> resourceRows,
                                               LocalDate start, LocalDate end, LocalDate previousStart,
                                               ResourceEvidence resourceEvidence, String dataSource) {
        List<RecommendationDraft> result = new ArrayList<>();
        Map<String, BigDecimal> currentServices = totals(rows, start, end, this::service);
        Map<String, BigDecimal> previousServices = totals(rows, previousStart, start, this::service);
        currentServices.forEach((service, current) -> {
            BigDecimal previous = previousServices.getOrDefault(service, ZERO);
            if (current.signum() > 0 && previous.signum() > 0
                && current.compareTo(previous.multiply(BigDecimal.valueOf(1.5))) > 0) {
            String displayedService = displayService(service);
            String sourceName = "COST_EXPLORER".equals(dataSource) ? "Cost Explorer" : "CUR 2.0";
            BigDecimal increase = current.subtract(previous);
            BigDecimal estimatedSaving = increase.compareTo(ZERO) > 0 ? increase : null;
            result.add(draft("spike-service-" + service, displayedService + " Cost Increase Opportunity", "Trend",
                "High", displayedService, null, "All regions", current,
                sourceName + " stored actual spend increased by at least 50% compared with the previous month. "
                    + billingDimensions(rows, service, start, end),
                "PostgreSQL billing data changed from " + money(previous) + " to " + money(current) + ".",
                "Review recent deployments, usage types, and resource-level charges for this service. Estimated monthly savings shown are heuristic (based on the month-over-month increase) and not guaranteed.", estimatedSaving));
            }
        });
        if ("COST_EXPLORER".equals(dataSource)) {
            result.addAll(costConcentrationDrafts(resourceRows, YearMonth.from(start)));
        }
        resourceEvidence.unattachedEbs().forEach((resourceId, region) -> {
            BigDecimal actualCost = matchedResourceCost(resourceRows, resourceId, region, this::isEbs);
            boolean exactBillingMatch = actualCost.signum() > 0;
            result.add(draft("unused-ebs-" + resourceId,
                exactBillingMatch ? "Unattached volume is incurring cost" : "Unattached EBS volume detected",
                "Unused Resource Review", "Medium", "Amazon EBS", resourceId,
                region, exactBillingMatch ? actualCost : null,
                exactBillingMatch
                    ? "CUR 2.0 reports " + money(actualCost) + " in actual charges matched to this volume; live AWS resource data reports it as unattached."
                    : "Live AWS resource data reports this volume as unattached. CUR 2.0 reports positive EBS charges in this region, but those rows have no resource ID and cannot be attributed to this volume.",
                exactBillingMatch
                    ? "The billing and current resource records both identify this volume as unused and billable. The estimated monthly saving equals the matched billed cost; this is a heuristic estimate, not a guarantee."
                    : "The volume state is verified, but its individual cost and potential savings are unknown (NO_DATA).",
                "Verify ownership and retention needs, then delete the volume if it is no longer required.", exactBillingMatch ? actualCost : null));
        });
        resourceEvidence.stoppedEc2().forEach((resourceId, region) -> {
            BigDecimal actualCost = matchedResourceCost(resourceRows, resourceId, region, this::isEc2);
            boolean exactBillingMatch = actualCost.signum() > 0;
            result.add(draft("stopped-ec2-" + resourceId,
                exactBillingMatch ? "Stopped instance still incurred cost" : "Stopped EC2 instance detected",
                "Unused Resource Review", "High", "EC2", resourceId,
                region, exactBillingMatch ? actualCost : null,
                exactBillingMatch
                    ? "AWS reports this EC2 instance as stopped, while CUR 2.0 records " + money(actualCost) + " in positive matched cost."
                    : "AWS reports this EC2 instance as stopped. CUR 2.0 reports positive EC2 charges in this region, but those rows have no resource ID and cannot be attributed to this instance.",
                exactBillingMatch
                    ? "The stopped instance has a real billing charge. Estimated monthly saving equals the matched billed cost (heuristic estimate, not guaranteed)."
                    : "The instance state is verified, but its individual cost and potential savings are unknown (NO_DATA).",
                "Review the stopped instance and associated resources; terminate it only after confirming it is no longer required.", exactBillingMatch ? actualCost : null));
        });

        return result;
    }

    private List<RecommendationDraft> costConcentrationDrafts(List<AwsBilling> currentRows, YearMonth month) {
        BigDecimal totalCost = currentRows.stream().map(this::cost).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (totalCost.signum() <= 0) return List.of();
        String accountId = currentRows.stream().map(AwsBilling::getAwsAccountId)
            .filter(value -> value != null && !value.isBlank()).findFirst().orElse("");
        String currency = currentRows.stream().map(AwsBilling::getCurrency)
            .filter(value -> value != null && !value.isBlank()).findFirst().orElse("unknown currency");
        Set<String> currencies = currentRows.stream().map(AwsBilling::getCurrency)
            .filter(value -> value != null && !value.isBlank()).collect(Collectors.toSet());
        if (currencies.size() != 1 || currentRows.stream()
            .anyMatch(row -> row.getCurrency() == null || row.getCurrency().isBlank())) return List.of();

        List<RecommendationDraft> findings = new ArrayList<>();
        addConcentrationFindings(findings, currentRows, month, totalCost, "service",
            accountId, currency, AwsBilling::getServiceName, "High service cost concentration");
        addConcentrationFindings(findings, currentRows, month, totalCost, "region",
            accountId, currency, AwsBilling::getRegion, "High region cost concentration");
        addConcentrationFindings(findings, currentRows, month, totalCost, "usage-type",
            accountId, currency, AwsBilling::getUsageType, "High usage-type cost concentration");
        addConcentrationFindings(findings, currentRows, month, totalCost, "product",
            accountId, currency, AwsBilling::getProductName, "High product cost concentration");
        return findings;
    }

    private void addConcentrationFindings(List<RecommendationDraft> findings, List<AwsBilling> currentRows,
                                          YearMonth month, BigDecimal totalCost, String dimension, String accountId,
                                          String currency, Function<AwsBilling, String> dimensionValue,
                                          String titlePrefix) {
        Map<String, List<AwsBilling>> groups = currentRows.stream()
            .filter(row -> dimensionValue.apply(row) != null && !dimensionValue.apply(row).isBlank())
            .collect(Collectors.groupingBy(row -> dimensionValue.apply(row).trim()));
        groups.forEach((value, groupRows) -> {
            BigDecimal actualCost = groupRows.stream().map(this::cost).reduce(BigDecimal.ZERO, BigDecimal::add);
            if (actualCost.signum() <= 0) return;
            BigDecimal share = actualCost.divide(totalCost, 8, RoundingMode.HALF_UP);
            if (share.compareTo(COST_CONCENTRATION_THRESHOLD) < 0) return;

            BigDecimal percentage = share.multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);
            String priority = share.compareTo(BigDecimal.valueOf(0.50)) >= 0 ? "High" : "Medium";
            String title = titlePrefix + ": " + value;
            String context = "service=" + summarize(groupRows, AwsBilling::getServiceName)
                + "; region=" + summarize(groupRows, AwsBilling::getRegion)
                + "; usage type=" + summarize(groupRows, AwsBilling::getUsageType)
                + "; product=" + summarize(groupRows, AwsBilling::getProductName);
            String reason = title + " represents " + percentage.toPlainString()
                + "% of stored billing-period spend, meeting the existing 25% cost-concentration threshold. "
                + "Billing evidence: " + context + "; actual cost=" + actualCost.toPlainString()
                + " " + currency + "; total cost=" + totalCost.toPlainString() + " " + currency + "; cost share="
                + percentage.toPlainString() + "%; billing period=" + month
                + "; source=stored Cost Explorer billing data in PostgreSQL aws_billing.";
            String rationale = title + " represents " + percentage.toPlainString()
                + "% of actual spend for " + month + ", exceeding the 25% concentration threshold.";
            String service = "service".equals(dimension) ? displayService(value) : "All services";
            String region = "region".equals(dimension) ? value : "Multiple regions";
            String key = "cost-concentration-" + dimension + "-"
                + UUID.nameUUIDFromBytes((accountId + ":" + dimension + ":" + value)
                    .getBytes(StandardCharsets.UTF_8));
            // Heuristic: potential savings estimate shown as the concentrated spend amount (not guaranteed).
            BigDecimal heuristicSavings = actualCost.compareTo(ZERO) > 0 ? actualCost : null;
            findings.add(new RecommendationDraft(key, title, "High Cost Service Detection", priority, service, null,
                region, actualCost, reason, rationale,
                "Review the listed services, regions, usage types, and products against workload needs and budgets. Estimated monthly savings shown are heuristic estimates (not guaranteed).",
                heuristicSavings));
        });
    }

    private String summarize(List<AwsBilling> rows, Function<AwsBilling, String> dimension) {
        String values = rows.stream().map(dimension).filter(value -> value != null && !value.isBlank())
            .map(String::trim).distinct().sorted().limit(5).collect(Collectors.joining(", "));
        return values.isBlank() ? "Unavailable" : values;
    }

    private List<AwsBilling> resourceBillingRows(String accountId, LocalDate start, LocalDate end) {
        List<AwsBilling> rows = billingSourceResolver == null
                ? billingRepository.findByAwsAccountId(accountId).stream().filter(row -> inRange(row, start, end)).toList()
                : billingSourceResolver.rows(accountId, "CUR_2_0", start, end);
        return rows.stream().filter(row -> row.getCostAmount() != null).toList();
    }

    private BillingEvidence recommendationBillingRows(String accountId, YearMonth month) {
        LocalDate start = month.minusMonths(1).atDay(1);
        LocalDate end = month.plusMonths(1).atDay(1);
        List<AwsBilling> curRows = billingRows(accountId, "CUR_2_0", start, end);
        boolean hasCurrentCurRows = curRows.stream().anyMatch(row -> inRange(row, month.atDay(1), end));
        List<AwsBilling> costExplorerRows = billingRows(accountId, "COST_EXPLORER", start, end);
        boolean hasCurrentCostExplorerRows = costExplorerRows.stream()
            .anyMatch(row -> inRange(row, month.atDay(1), end));
        if (hasCurrentCostExplorerRows) {
            return new BillingEvidence("COST_EXPLORER", costExplorerRows);
        }
        if (hasCurrentCurRows) {
            return new BillingEvidence("CUR_2_0", curRows);
        }
        return new BillingEvidence("CUR_2_0", curRows);
    }

    private List<AwsBilling> billingRows(String accountId, String source, LocalDate start, LocalDate end) {
        List<AwsBilling> rows = "COST_EXPLORER".equals(source) || billingSourceResolver == null
            ? billingRepository.findByAwsAccountIdAndDataSourceOrderByBillingPeriodStartAsc(accountId, source)
            : billingSourceResolver.rows(accountId, source, start, end);
        return rows.stream().filter(row -> source.equals(row.getDataSource())
            && row.getCostAmount() != null && inRange(row, start, end)).toList();
    }

    private ResourceEvidence uncheckedResourceEvidence() {
        return new ResourceEvidence(false, false, false, false, Map.of(), Map.of(), 0, 0, 0, 0, 0, 0, null);
    }

    private String billingDimensions(List<AwsBilling> rows, String service, LocalDate start, LocalDate end) {
        List<AwsBilling> serviceRows = rows.stream().filter(row -> inRange(row, start, end)
            && service(row).equals(service)).toList();
        String regions = serviceRows.stream().map(AwsBilling::getRegion).filter(value -> value != null && !value.isBlank())
            .distinct().sorted().collect(Collectors.joining(", "));
        String usageTypes = serviceRows.stream().map(AwsBilling::getUsageType)
            .filter(value -> value != null && !value.isBlank()).distinct().sorted().collect(Collectors.joining(", "));
        String products = serviceRows.stream().map(AwsBilling::getProductName)
            .filter(value -> value != null && !value.isBlank()).distinct().sorted().collect(Collectors.joining(", "));
        List<String> dimensions = new ArrayList<>();
        if (!regions.isBlank()) dimensions.add("regions: " + regions);
        if (!usageTypes.isBlank()) dimensions.add("usage types: " + usageTypes);
        if (!products.isBlank()) dimensions.add("products: " + products);
        return dimensions.isEmpty() ? "No region, usage-type, or product detail was available." : String.join("; ", dimensions) + ".";
    }

    private ResourceEvidence resourceEvidence(String accountId, String connectionToken, List<AwsBilling> billingRows,
                                              boolean checkEbs, boolean checkStoppedEc2) {
        if ((!checkEbs && !checkStoppedEc2) || resourceService == null) {
            return new ResourceEvidence(false, false, false, false, Map.of(), Map.of(), 0, 0, 0, 0, 0, 0, null);
        }
        try {
            AwsResourceResponse resources = resourceService.discoverResourcesForRecommendations(connectionToken, billingRegions(billingRows));
            if (resources == null) resources = resourceService.discoverResources(connectionToken);
            if (resources == null) return new ResourceEvidence(true, false, false, false, Map.of(), Map.of(), 0, 0, 0, 0, 0, 0,
                "AWS resource discovery returned no data.");
            final AwsResourceResponse resourceData = resources;
            List<AwsResourceResponse.Ec2InstanceDto> instances = resourceData.ec2Instances() == null
                ? List.of() : resourceData.ec2Instances();
            List<AwsResourceResponse.EbsVolumeDto> volumes = resourceData.ebsVolumes() == null
                ? List.of() : resourceData.ebsVolumes();
            if (!accountId.equals(resourceData.accountId())) {
                return new ResourceEvidence(true, false, false, false, Map.of(), Map.of(), instances.size(), volumes.size(),
                    0, 0, 0, 0, "AWS resource discovery returned a different AWS account than the active connection.");
            }
            long stoppedEc2Resources = instances.stream()
                    .filter(instance -> "stopped".equalsIgnoreCase(instance.state())
                        && regionForInstance(instance, resourceData.region()) != null)
                    .count();
            long unattachedEbsResources = volumes.stream()
                    .filter(volume -> "available".equalsIgnoreCase(volume.state()) && volume.attachedInstanceId() == null
                        && regionForVolume(volume, resourceData.region()) != null)
                    .count();
            long ec2BillingResourceMatches = !checkStoppedEc2 ? 0 : instances.stream()
                    .filter(instance -> instance.instanceId() != null
                        && hasMatchingEc2Billing(billingRows, instance.instanceId(), regionForInstance(instance, resourceData.region())))
                    .count();
            long ebsBillingResourceMatches = !checkEbs ? 0 : volumes.stream()
                    .filter(volume -> volume.volumeId() != null
                        && hasMatchingEbsBilling(billingRows, volume.volumeId(), regionForVolume(volume, resourceData.region())))
                    .count();
            Map<String, String> unattachedEbs = !checkEbs ? Map.of()
                : volumes.stream()
                    .filter(volume -> volume.volumeId() != null && "available".equalsIgnoreCase(volume.state())
                        && volume.attachedInstanceId() == null
                        && hasBillingForResourceRegion(billingRows, volume.volumeId(), regionForVolume(volume, resourceData.region()),
                            this::isEbs, true))
                    .collect(Collectors.toMap(volume -> normalized(volume.volumeId()),
                        volume -> regionForVolume(volume, resourceData.region()), (first, ignored) -> first));
            Map<String, String> stoppedEc2 = !checkStoppedEc2 ? Map.of()
                : instances.stream()
                    .filter(instance -> instance.instanceId() != null && "stopped".equalsIgnoreCase(instance.state())
                        && hasBillingForResourceRegion(billingRows, instance.instanceId(), regionForInstance(instance, resourceData.region()),
                            this::isEc2, false))
                    .collect(Collectors.toMap(instance -> normalized(instance.instanceId()),
                        instance -> regionForInstance(instance, resourceData.region()), (first, ignored) -> first));
            return new ResourceEvidence(true, true, true, false, unattachedEbs, stoppedEc2, instances.size(), volumes.size(),
                stoppedEc2Resources, unattachedEbsResources, ec2BillingResourceMatches, ebsBillingResourceMatches, null);
        } catch (RuntimeException exception) {
            logger.warn("Recommendation AWS resource discovery failed; exceptionType={}", exception.getClass().getSimpleName());
            return new ResourceEvidence(true, false, false, isPermissionDenied(exception),
                Map.of(), Map.of(), 0, 0, 0, 0, 0, 0, exception.getMessage());
        }
    }

    private SupportResult supportedRecommendations(String accountId, List<AwsRecommendation> stored, List<AwsBilling> currentRows,
                                                    List<AwsBilling> previousRows, String connectionToken, YearMonth month,
                                                    String dataSource) {
        boolean hasEbsFinding = stored.stream().anyMatch(item -> item.getRecommendationKey().startsWith("unused-ebs-"));
        boolean hasStoppedEc2Finding = stored.stream().anyMatch(item -> item.getRecommendationKey().startsWith("stopped-ec2-"));
        List<AwsBilling> resourceRows = "COST_EXPLORER".equals(dataSource) ? currentRows
            : hasEbsFinding || hasStoppedEc2Finding
            ? resourceBillingRows(accountId, month.atDay(1), month.plusMonths(1).atDay(1)) : List.of();
        ResourceEvidence resourceEvidence = "COST_EXPLORER".equals(dataSource) ? uncheckedResourceEvidence()
            : resourceEvidence(accountId, connectionToken, resourceRows, hasEbsFinding, hasStoppedEc2Finding);
        List<AwsRecommendation> recommendations = stored.stream()
                .filter(item -> isStillSupported(item, currentRows, previousRows, resourceRows, resourceEvidence,
                    dataSource)).toList();
        return new SupportResult(recommendations, resourceEvidence);
    }

    private boolean isStillSupported(AwsRecommendation item, List<AwsBilling> currentRows,
                                     List<AwsBilling> previousRows, List<AwsBilling> resourceRows,
                                     ResourceEvidence resourceEvidence, String dataSource) {
        String key = item.getRecommendationKey();
        if (key.startsWith("cost-concentration-")) {
            return "COST_EXPLORER".equals(dataSource)
                && costConcentrationDrafts(currentRows, YearMonth.parse(item.getBillingMonth())).stream()
                    .anyMatch(draft -> draft.key().equals(key));
        }
        if (key.startsWith("cost-service-") || key.startsWith("cost-resource-")) return false;
        if (key.startsWith("spike-service-")) {
            BigDecimal current = currentServiceSpend(currentRows, item.getService());
            BigDecimal previous = currentServiceSpend(previousRows, item.getService());
            return current.signum() > 0 && previous.signum() > 0
                    && current.compareTo(previous.multiply(BigDecimal.valueOf(1.5))) > 0;
        }
        if (key.startsWith("unused-resource-")) return false;
        if (key.startsWith("unused-ebs-")) {
            String resourceId = realResourceId(item.getResourceId());
            String region = resourceId == null ? null : resourceEvidence.unattachedEbs().get(resourceId);
            return region != null && isEbs(item.getService())
                && hasBillingForResourceRegion(resourceRows, resourceId, region, this::isEbs, true);
        }
        if (key.startsWith("stopped-ec2-")) {
            String resourceId = realResourceId(item.getResourceId());
            String region = resourceId == null ? null : resourceEvidence.stoppedEc2().get(resourceId);
            return region != null
                && hasBillingForResourceRegion(resourceRows, resourceId, region, this::isEc2, false);
        }
        return false;
    }

    private BigDecimal currentServiceSpend(List<AwsBilling> rows, String recommendationService) {
        return rows.stream().filter(row -> displayService(service(row)).equalsIgnoreCase(recommendationService))
                .map(this::cost).reduce(ZERO, BigDecimal::add);
    }

    private boolean isEbs(AwsBilling row) {
        return isEbs(service(row));
    }

    private boolean isEbs(String service) {
        String normalized = service == null ? "" : service.toLowerCase();
        return normalized.contains("elastic block store") || normalized.contains("ebs");
    }

    private boolean isEc2(AwsBilling row) {
        String normalized = service(row).toLowerCase();
        return normalized.contains("ec2") || normalized.contains("elastic compute");
    }

    private boolean hasMatchingEbsBilling(List<AwsBilling> rows, String volumeId, String resourceRegion) {
        return rows.stream().anyMatch(row -> isEbs(row) && sameResourceId(volumeId, row.getResourceId())
                && sameRegion(resourceRegion, row.getRegion()) && cost(row).signum() > 0);
    }

    private boolean hasMatchingEc2Billing(List<AwsBilling> rows, String instanceId, String resourceRegion) {
        return rows.stream().anyMatch(row -> isEc2(row) && sameResourceId(instanceId, row.getResourceId())
                && sameRegion(resourceRegion, row.getRegion()) && cost(row).signum() > 0);
    }

    private boolean sameResourceId(String first, String second) {
        return ResourceIds.isPresent(first) && ResourceIds.isPresent(second)
            && normalized(first).equals(normalized(second));
    }

    private boolean sameRegion(String first, String second) {
        String normalizedFirst = normalizedRegion(first);
        String normalizedSecond = normalizedRegion(second);
        return normalizedFirst != null && normalizedFirst.equals(normalizedSecond);
    }

    private String normalizedRegion(String value) {
        String region = normalized(value);
        return region == null ? null : region.replaceFirst("(\\d+)[a-z]$", "$1");
    }

    private boolean hasUnidentifiedBilling(List<AwsBilling> rows, Predicate<AwsBilling> serviceMatch, String region) {
        return rows.stream().anyMatch(row -> serviceMatch.test(row) && !hasResource(row)
            && sameRegion(region, row.getRegion()) && cost(row).signum() > 0);
    }

    private BigDecimal matchedResourceCost(List<AwsBilling> rows, String resourceId, String region,
                                          Predicate<AwsBilling> serviceMatch) {
        return rows.stream().filter(row -> serviceMatch.test(row) && sameResourceId(resourceId, row.getResourceId())
                && sameRegion(region, row.getRegion()) && cost(row).signum() > 0)
            .map(this::cost).reduce(ZERO, BigDecimal::add);
    }

    private boolean hasBillingForResourceRegion(List<AwsBilling> rows, String resourceId, String region,
                                                Predicate<AwsBilling> serviceMatch, boolean ebs) {
        boolean exactMatch = ebs ? hasMatchingEbsBilling(rows, resourceId, region)
            : hasMatchingEc2Billing(rows, resourceId, region);
        return region != null && (exactMatch || hasUnidentifiedBilling(rows, serviceMatch, region));
    }

    private Set<String> billingRegions(List<AwsBilling> rows) {
        Set<String> supportedRegions = software.amazon.awssdk.regions.Region.regions().stream()
            .map(software.amazon.awssdk.regions.Region::id).collect(Collectors.toSet());
        return rows.stream().filter(row -> (isEc2(row) || isEbs(row)) && cost(row).signum() > 0)
            .map(row -> normalizedRegion(row.getRegion())).filter(supportedRegions::contains)
            .collect(Collectors.toSet());
    }

    private String regionForInstance(AwsResourceResponse.Ec2InstanceDto instance, String fallbackRegion) {
        return firstRegion(instance.region(), fallbackRegion);
    }

    private String regionForVolume(AwsResourceResponse.EbsVolumeDto volume, String fallbackRegion) {
        return firstRegion(volume.region(), volume.availabilityZone(), fallbackRegion);
    }

    private String firstRegion(String... values) {
        for (String value : values) {
            String region = normalizedRegion(value);
            if (region != null) return region;
        }
        return null;
    }

    private String normalized(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
    private String realResourceId(String value) { return ResourceIds.isPresent(value) ? normalized(value) : null; }

    private RecommendationDraft draft(String key, String title, String category, String severity, String service, String resourceId, String region, BigDecimal currentCost, String reason, String recommendation, String action, BigDecimal potentialSavings) { return new RecommendationDraft(key, title, category, severity, service, resourceId, region, moneyOrNull(currentCost), reason, recommendation, action, potentialSavings == null ? null : money(potentialSavings)); }
    private AwsRecommendationResponse response(String accountId, String currency, List<AwsRecommendation> entities) { return new AwsRecommendationResponse(accountId, currency, entities.stream().map(this::toResponse).sorted(Comparator.comparingInt(this::priorityRank).reversed()).toList()); }
    private Recommendation toResponse(AwsRecommendation item) { return toResponse(item, "CUR_2_0"); }
    private Recommendation toResponse(AwsRecommendation item, String dataSource) {
        String resourceId = item.getResourceId();
        String resource = ResourceIds.isPresent(resourceId) ? resourceId : "Service total";
        BigDecimal estimatedSaving = item.getPotentialSavings() == null ? null : money(item.getPotentialSavings());
        RecommendationEvidenceParser.Evidence evidence = RecommendationEvidenceParser.parse(
            item.getReason(), item.getTitle(), item.getPotentialSavings());
        return new Recommendation(item.getId().toString(), item.getService(), blank(item.getRegion(), "Unknown"),
            resource, item.getCurrentCost(), item.getTitle(), item.getReason(), item.getRecommendation(),
            item.getAction(), estimatedSaving, item.getSeverity(), item.getCategory(), item.getStatus(),
            item.getCreatedAt().toString(), resourcePath(item.getService()), resourceId, item.getReason(),
            item.getBillingMonth(), dataSource, evidence.costSharePercentage(), evidence.periodTotalCost(),
            evidence.concentrationThreshold(), evidence.resourceState(), evidence.savingsBasis());
    }
    private Map<String, BigDecimal> totals(List<AwsBilling> rows, LocalDate start, LocalDate end, Function<AwsBilling, String> key) { return rows.stream().filter(row -> inRange(row, start, end)).collect(Collectors.groupingBy(row -> blank(key.apply(row), "Unknown"), Collectors.reducing(ZERO, this::cost, BigDecimal::add))); }
    private boolean inRange(AwsBilling row, LocalDate start, LocalDate end) { LocalDate date = billingDate(row); return date != null && !date.isBefore(start) && date.isBefore(end); }
    private LocalDate billingDate(AwsBilling row) {
        if ("COST_EXPLORER".equals(row.getDataSource()) && row.getBillingPeriodStart() != null) {
            return row.getBillingPeriodStart();
        }
        return row.getUsageStartDate() == null ? row.getBillingPeriodStart() : row.getUsageStartDate();
    }
    private BigDecimal cost(AwsBilling row) { return row.getCostAmount() == null ? ZERO : row.getCostAmount(); }
    private boolean hasResource(AwsBilling row) { return ResourceIds.isPresent(row.getResourceId()); }
    private String service(AwsBilling row) { return blank(row.getServiceName(), "Unknown"); }
    private String displayService(String value) { String service = value.toLowerCase(); return service.contains("s3") || service.contains("simple storage") ? "S3" : service.contains("rds") || service.contains("relational database") ? "RDS" : service.contains("lambda") ? "Lambda" : service.contains("ec2") || service.contains("elastic compute") ? "EC2" : value; }
    private String resourcePath(String service) { return service.toLowerCase().contains("ec2") ? "/resources" : "/billing"; }
    private String blank(String value, String fallback) { return value == null || value.isBlank() ? fallback : value; }
    private BigDecimal money(BigDecimal value) { return value == null ? ZERO : value.setScale(2, RoundingMode.HALF_UP); }
    private int priorityRank(Recommendation recommendation) { return "High".equals(recommendation.priority()) ? 3 : "Medium".equals(recommendation.priority()) ? 2 : 1; }
    private BigDecimal moneyOrNull(BigDecimal value) { return value == null ? null : money(value); }
    private AwsRecommendationResponse response(String accountId, String currency, List<AwsRecommendation> entities, YearMonth month, String status, String message, BigDecimal totalSpend) { return new AwsRecommendationResponse(accountId, currency, entities.stream().map(this::toResponse).sorted(Comparator.comparingInt(this::priorityRank).reversed()).toList(), month.toString(), status, message, totalSpend == null ? null : money(totalSpend)); }
    private AwsRecommendationResponse response(String accountId, String currency, List<AwsRecommendation> entities, YearMonth month,
                                               String status, String message, BigDecimal totalSpend, boolean analysisComplete,
                                               String resourceDataStatus, List<String> evaluationReasons) {
        return response(accountId, currency, entities, month, status, message, totalSpend, analysisComplete,
            resourceDataStatus, evaluationReasons, "CUR_2_0");
    }
    private AwsRecommendationResponse response(String accountId, String currency, List<AwsRecommendation> entities, YearMonth month,
                                               String status, String message, BigDecimal totalSpend, boolean analysisComplete,
                                               String resourceDataStatus, List<String> evaluationReasons, String dataSource) {
        return new AwsRecommendationResponse(accountId, currency,
                entities.stream().map(item -> toResponse(item, dataSource)).sorted(Comparator.comparingInt(this::priorityRank).reversed()).toList(),
                month.toString(), status, message, totalSpend == null ? null : money(totalSpend), analysisComplete,
                resourceDataStatus, evaluationReasons);
    }
    private String emptyStatus(ResourceEvidence evidence, List<AwsBilling> currentRows, List<AwsBilling> previousRows,
                               String dataSource) {
        if ("COST_EXPLORER".equals(dataSource)) {
            return previousRows.isEmpty() ? "INSUFFICIENT_BILLING_EVIDENCE" : "NO_RECOMMENDATIONS";
        }
        return statusForEmptyResult(evidence, currentRows);
    }
    private String statusForEmptyResult(ResourceEvidence evidence, List<AwsBilling> billingRows) {
        if (evidence.permissionDenied()) return "PERMISSION_DENIED";
        if (evidence.checked() && !evidence.available()) return "RESOURCE_UNAVAILABLE";
        if (evidence.available() && evidence.ec2Resources() + evidence.ebsResources() == 0) return "NO_RESOURCES";
        boolean hasResourceBilling = billingRows.stream()
            .filter(row -> (isEc2(row) || isEbs(row)) && cost(row).signum() > 0).anyMatch(this::hasResource);
        boolean hasUnidentifiedBilling = billingRows.stream()
            .anyMatch(row -> (isEc2(row) || isEbs(row)) && cost(row).signum() > 0 && !hasResource(row));
        if (evidence.available() && hasResourceBilling
            && evidence.ec2BillingResourceMatches() + evidence.ebsBillingResourceMatches() == 0) {
            return "NO_RESOURCE_BILLING_MATCH";
        }
        if (evidence.available() && hasUnidentifiedBilling
            && evidence.stoppedEc2().isEmpty() && evidence.unattachedEbs().isEmpty()) {
            return "INSUFFICIENT_BILLING_EVIDENCE";
        }
        return "NO_RECOMMENDATIONS";
    }
    private String diagnostic(ResourceEvidence evidence, String fallback) {
        String diagnostic = evidence.diagnosticMessage();
        if (diagnostic == null || diagnostic.isBlank()
            || (evidence.permissionDenied() && !diagnostic.contains("ec2:"))) {
            return fallback;
        }
        return diagnostic;
    }
    private String resourceStatus(ResourceEvidence resourceEvidence) {
        if (!resourceEvidence.checked()) return "NOT_CHECKED";
        if (resourceEvidence.permissionDenied()) return "PERMISSION_DENIED";
        if (!resourceEvidence.available()) return "UNAVAILABLE";
        return resourceEvidence.ec2Resources() + resourceEvidence.ebsResources() == 0 ? "NO_RESOURCES" : "AVAILABLE";
    }
    private boolean isPermissionDenied(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause.getMessage() != null && (cause.getMessage().toLowerCase().contains("denied")
                || cause.getMessage().toLowerCase().contains("accessdenied")
                || cause.getMessage().toLowerCase().contains("unauthorized"))) return true;
            if (cause instanceof AwsServiceException awsException) {
                int statusCode = awsException.statusCode();
                String errorCode = awsException.awsErrorDetails() == null
                    ? "" : awsException.awsErrorDetails().errorCode();
                if (statusCode == 401 || statusCode == 403 || (errorCode != null
                    && (errorCode.toLowerCase().contains("accessdenied")
                        || errorCode.toLowerCase().contains("unauthorized")
                        || errorCode.toLowerCase().contains("forbidden")))) return true;
            }
        }
        return false;
    }
    private List<String> evaluationReasons(List<AwsBilling> resourceRows, List<AwsRecommendation> recommendations,
                                          ResourceEvidence resourceEvidence, int qualifiedRecommendations,
                                          String dataSource, List<AwsBilling> previousRows, YearMonth selectedMonth) {
        List<String> reasons = new ArrayList<>();
        if ("COST_EXPLORER".equals(dataSource)) {
            if (previousRows.isEmpty()) {
                reasons.add("Stored Cost Explorer billing data for the selected period was evaluated for service, region, usage-type, and product cost concentration. Prior-month data is unavailable for the cost-trend rule, and no supported concentration threshold qualified. No savings estimate was inferred.");
            } else {
                BigDecimal currentTotal = resourceRows.stream().map(this::cost)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
                BigDecimal previousTotal = previousRows.stream().map(this::cost)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
                String currency = resourceRows.stream().map(AwsBilling::getCurrency)
                    .filter(value -> value != null && !value.isBlank()).findFirst().orElse("unknown currency");
                reasons.add("Stored Cost Explorer periods detected: previous month " + selectedMonth.minusMonths(1)
                    + " (" + previousRows.size() + " rows, " + previousTotal.toPlainString()
                    + " " + currency + "); selected month " + selectedMonth + " (" + resourceRows.size()
                    + " rows, " + currentTotal.toPlainString() + " " + currency
                    + "). Service, region, usage-type, product, and cost evidence was evaluated against the existing 25% concentration and 50% month-over-month increase thresholds; no rule qualified. Resource-state rules were skipped because they require live AWS resource data.");
            }
            if (qualifiedRecommendations > 0 && recommendations.isEmpty()) {
                reasons.add("Qualified billing-based recommendations did not pass stored-finding revalidation.");
            }
            return List.copyOf(reasons);
        }
        long detailRowsWithResourceId = resourceRows.stream().filter(this::hasResource).count();
        if (resourceRows.isEmpty()) {
            reasons.add("No CUR 2.0 detail billing rows were found for the selected month.");
        } else if (detailRowsWithResourceId == 0) {
            reasons.add("CUR 2.0 detail billing rows contain no resource_id values; resource-level costs cannot be attributed, so only live resource-state reviews are eligible.");
        }
        boolean ec2BillingWithoutResourceId = resourceRows.stream()
                .anyMatch(row -> isEc2(row) && cost(row).signum() > 0 && !hasResource(row));
        if (ec2BillingWithoutResourceId) {
            reasons.add("EC2 billing exists but no resource-level billing ID is available for matching.");
        }
        boolean ebsBillingWithoutResourceId = resourceRows.stream()
            .anyMatch(row -> isEbs(row) && cost(row).signum() > 0 && !hasResource(row));
        if (ebsBillingWithoutResourceId) {
            reasons.add("EBS billing evidence has no resource-level volume ID for matching.");
        }
        if (resourceEvidence.permissionDenied()) {
            reasons.add(diagnostic(resourceEvidence, RESOURCE_PERMISSION_MESSAGE));
        } else if (!resourceEvidence.available()) {
            reasons.add(resourceEvidence.checked() && !resourceEvidence.accountMatches()
                ? "AWS resource discovery returned no data or did not match the connected account; resource matching was incomplete."
                : RESOURCE_UNAVAILABLE_MESSAGE);
        } else if (resourceEvidence.ec2Resources() + resourceEvidence.ebsResources() == 0) {
            reasons.add("AWS resource discovery found no EC2 or EBS resources in any enabled region for this account.");
        } else if (detailRowsWithResourceId > 0
            && resourceEvidence.ec2BillingResourceMatches() + resourceEvidence.ebsBillingResourceMatches() == 0) {
            reasons.add("No positive-cost CUR resource_id matched an EC2 or EBS resource in its billing region.");
        } else {
            if (resourceEvidence.ec2BillingResourceMatches() > 0 && resourceEvidence.stoppedEc2().isEmpty()) {
                reasons.add("CUR billing matched EC2 resources, but none are stopped.");
            }
            if (resourceEvidence.ebsBillingResourceMatches() > 0 && resourceEvidence.unattachedEbs().isEmpty()) {
                reasons.add("CUR billing matched EBS volumes, but none are available and unattached.");
            }
        }
        if (qualifiedRecommendations > 0 && recommendations.isEmpty()) {
            reasons.add("Qualified recommendations were generated but did not pass stored-finding revalidation.");
        }
        if (reasons.isEmpty()) reasons.add("CUR detail and AWS resource state were evaluated; no EC2 or EBS rule qualified.");
        return List.copyOf(reasons);
    }
    private BigDecimal sum(List<AwsBilling> rows) { return rows.stream().map(this::cost).reduce(ZERO, BigDecimal::add); }
    private YearMonth parseMonth(String month, YearMonth fallback) { if (month == null || month.isBlank()) return fallback; try { return YearMonth.parse(month); } catch (RuntimeException exception) { throw new IllegalArgumentException("Invalid month format. Please use YYYY-MM."); } }
    private static final String RESOURCE_UNAVAILABLE_MESSAGE = "Resource data unavailable. Reconnect AWS and generate recommendations again.";
    private static final String RESOURCE_PERMISSION_MESSAGE = "AWS denied EC2/EBS resource discovery. Grant ec2:DescribeRegions, ec2:DescribeInstances, and ec2:DescribeVolumes, then generate recommendations again.";
    private record RecommendationDraft(String key, String title, String category, String severity, String service, String resourceId, String region, BigDecimal currentCost, String reason, String recommendation, String action, BigDecimal potentialSavings) {}
    private record BillingEvidence(String dataSource, List<AwsBilling> rows) {}
    private record ResourceEvidence(boolean checked, boolean available, boolean accountMatches,
                                    boolean permissionDenied, Map<String, String> unattachedEbs,
                                    Map<String, String> stoppedEc2,
                                    long ec2Resources, long ebsResources, long stoppedEc2Resources,
                                    long unattachedEbsResources, long ec2BillingResourceMatches,
                                    long ebsBillingResourceMatches, String diagnosticMessage) {}
    private record SupportResult(List<AwsRecommendation> recommendations, ResourceEvidence resourceEvidence) {}
}