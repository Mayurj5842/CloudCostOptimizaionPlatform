import type {
  AwsCostAnalyticsResponse,
  AwsRecommendationResponse,
  AwsResourceResponse,
  Budget,
} from "@/services/aws-connection";

export type HealthComponentKey = "budget" | "stability" | "resources" | "optimization";

export type HealthScoreComponent = {
  key: HealthComponentKey;
  title: string;
  score: number | null;
  maxScore: 25;
  evidence: string;
  suggestion: string | null;
};

export type HealthScoreReport = {
  score: number | null;
  status: "EXCELLENT" | "GOOD" | "FAIR" | "NEEDS ATTENTION" | "DATA UNAVAILABLE";
  partial: boolean;
  availableComponents: number;
  components: HealthScoreComponent[];
  unavailable: string[];
};

type HealthScoreInputs = {
  analytics: AwsCostAnalyticsResponse | null;
  budgets: Budget[] | null;
  resources: AwsResourceResponse | null;
  recommendations: AwsRecommendationResponse | null;
  errors?: Partial<Record<HealthComponentKey, string>>;
};

export function calculateHealthScore({ analytics, budgets, resources, recommendations, errors = {} }: HealthScoreInputs): HealthScoreReport {
  const components = [
    scoreBudgetHealth(budgets, errors.budget),
    scoreCostStability(analytics, errors.stability),
    scoreResourceEfficiency(resources, errors.resources),
    scoreOptimizationHealth(recommendations, errors.optimization),
  ];
  const unavailable = components.filter((component) => component.score == null).map((component) => component.title);
  const available = components.filter((component) => component.score != null);

  if (available.length === 0) {
    return { score: null, status: "DATA UNAVAILABLE", partial: false, availableComponents: 0, components, unavailable };
  }

  const availablePoints = available.reduce((total, component) => total + (component.score ?? 0), 0);
  const availableMaximum = available.reduce((total, component) => total + component.maxScore, 0);
  const score = Math.round(availablePoints / availableMaximum * 100);
  const status = score >= 90 ? "EXCELLENT" : score >= 80 ? "GOOD" : score >= 65 ? "FAIR" : "NEEDS ATTENTION";
  return { score, status, partial: unavailable.length > 0, availableComponents: available.length, components, unavailable };
}

function scoreBudgetHealth(budgets: Budget[] | null, error?: string): HealthScoreComponent {
  const key = "budget" as const;
  const title = "Budget Health";
  if (budgets == null) return unavailable(key, title, error ?? "Budget data is unavailable.");

  const enabled = budgets.filter((budget) => budget.enabled);
  if (enabled.length === 0) return unavailable(key, title, "No enabled budget is configured for this period.");

  const measured = enabled.filter((budget) => Number.isFinite(budget.amount) && budget.amount > 0
    && budget.actualSpend != null && Number.isFinite(budget.actualSpend) && budget.actualSpend >= 0);
  if (measured.length === 0) return unavailable(key, title, "Actual spend is unavailable for the configured budgets.");

  const worst = measured.reduce((current, budget) => {
    const utilization = budget.actualSpend! / budget.amount * 100;
    return utilization > current.utilization ? { budget, utilization } : current;
  }, { budget: measured[0], utilization: measured[0].actualSpend! / measured[0].amount * 100 });
  const score = worst.utilization <= 50 ? 25
    : worst.utilization <= 75 ? 20
      : worst.utilization <= 90 ? 15
        : worst.utilization <= 100 ? 10 : 0;
  const missingCount = enabled.length - measured.length;
  const evidence = `${worst.budget.name} is ${formatPercent(worst.utilization)} used, the highest measured utilization across ${measured.length} enabled budget${measured.length === 1 ? "" : "s"}.${missingCount ? ` Actual spend is unavailable for ${missingCount} additional budget${missingCount === 1 ? "" : "s"}.` : ""}`;
  return {
    key,
    title,
    score,
    maxScore: 25,
    evidence,
    suggestion: worst.utilization >= 75 ? "Review the budget's actual service and period spending before increasing its limit." : null,
  };
}

function scoreCostStability(analytics: AwsCostAnalyticsResponse | null, error?: string): HealthScoreComponent {
  const key = "stability" as const;
  const title = "Cost Stability";
  if (!analytics || analytics.dataStatus !== "AVAILABLE") {
    const reason = analytics?.dataStatus === "NO_CUR_DATA"
      ? "Cost Analytics reports NO_CUR_DATA for the selected billing period."
      : analytics?.dataStatus === "UNAVAILABLE"
        ? "Cost Analytics reports that billing data is unavailable for the selected period."
        : error ?? "Cost Analytics did not return persisted billing data for the selected period.";
    return unavailable(key, title, reason);
  }

  const current = analytics.summary.totalCost;
  const previous = analytics.summary.previousPeriodCost;
  if (current == null || previous == null || !Number.isFinite(current) || !Number.isFinite(previous) || current < 0 || previous < 0) {
    return unavailable(key, title, "Current and previous-period actual spend are both required.");
  }
  if (previous === 0 && current > 0) {
    return unavailable(key, title, "The previous period has zero spend, so a percentage comparison is not meaningful.");
  }

  const change = previous === 0 ? 0 : Math.abs((current - previous) / previous * 100);
  const score = change <= 5 ? 25 : change <= 15 ? 20 : change <= 30 ? 15 : change <= 50 ? 10 : 5;
  return {
    key,
    title,
    score,
    maxScore: 25,
    evidence: `Actual spend changed ${formatPercent(change)} in absolute terms versus the previous period. This is a comparison, not an anomaly claim.`,
    suggestion: change > 15 ? "Review the existing service-cost breakdown for the largest period-over-period changes." : null,
  };
}

function scoreResourceEfficiency(resources: AwsResourceResponse | null, error?: string): HealthScoreComponent {
  const key = "resources" as const;
  const title = "Resource Efficiency";
  if (!resources) return unavailable(key, title, error ?? "The current AWS resource snapshot is unavailable.");

  const summary = resources.summary;
  const eligible = summary.ec2Instances + summary.rdsDatabases + summary.lambdaFunctions + summary.vpcs;
  const healthy = summary.healthyResources;
  const total = summary.totalResources;
  const unattached = summary.unusedVolumes;
  const counts = [eligible, healthy, total, unattached];
  if (counts.some((value) => !Number.isFinite(value) || value < 0)
    || eligible <= 0 || total <= 0 || healthy > eligible || unattached > total) {
    return unavailable(key, title, "The resource snapshot does not contain a valid countable health and storage summary.");
  }

  const healthPoints = Math.round(15 * healthy / eligible);
  const storagePoints = Math.round(10 * (1 - unattached / total));
  const score = healthPoints + storagePoints;
  return {
    key,
    title,
    score,
    maxScore: 25,
    evidence: `Current AWS resource snapshot: ${healthy} of ${eligible} status-reporting resources are healthy; ${unattached} of ${total} resources are reported as unattached EBS volumes. This snapshot is not historical.`,
    suggestion: unattached > 0 ? "Review unattached EBS volumes and confirm ownership before deciding whether any action is appropriate." : null,
  };
}

function scoreOptimizationHealth(recommendations: AwsRecommendationResponse | null, error?: string): HealthScoreComponent {
  const key = "optimization" as const;
  const title = "Optimization Health";
  if (!recommendations) {
    return unavailable(key, title, error ?? "The Recommendations API did not return data.");
  }
  if (recommendations.status === "NO_DATA") {
    return unavailable(key, title, recommendations.message ?? "The Recommendations API reports no billing data for this period.");
  }
  if (recommendations.status !== "SUCCESS" && recommendations.status !== "NO_RECOMMENDATIONS") {
    return unavailable(key, title, recommendations.message ?? `Recommendations are unavailable (${recommendations.status}).`);
  }

  const open = recommendations.recommendations.filter((item) => {
    const status = item.status.toLowerCase();
    return status === "new" || status === "reviewed";
  });
  const countPriority = (priority: string) => open.filter((item) => item.priority.toLowerCase() === priority).length;
  const high = countPriority("high");
  const medium = countPriority("medium");
  const low = countPriority("low");
  const score = Math.max(0, 25 - high * 8 - medium * 4 - low * 2);
  const suggestion = open.length > 0 ? "Review the stored recommendations and verify each proposed action against current workloads." : null;
  const evidence = open.length === 0
    ? "The Recommendations API has billing data and reports no open stored recommendations for this period."
    : `${open.length} open stored recommendation${open.length === 1 ? "" : "s"}: ${high} High, ${medium} Medium, ${low} Low. Applied and dismissed items are not counted as open.`;
  return {
    key,
    title,
    score,
    maxScore: 25,
    evidence,
    suggestion,
  };
}

function unavailable(key: HealthComponentKey, title: string, evidence: string): HealthScoreComponent {
  return { key, title, score: null, maxScore: 25, evidence, suggestion: null };
}

function formatPercent(value: number) {
  return `${value.toFixed(1)}%`;
}