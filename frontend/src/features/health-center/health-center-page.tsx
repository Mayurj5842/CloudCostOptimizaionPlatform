"use client";

import Link from "next/link";
import { useQuery } from "@tanstack/react-query";
import { AlertTriangle, CheckCircle2, HeartPulse, LoaderCircle, RefreshCw, ShieldCheck, Sparkles } from "lucide-react";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { MonthSelector } from "@/components/month-selector";
import {
  fetchAwsCostAnalytics,
  fetchAwsInsights,
  fetchAwsRecommendations,
  fetchAwsResources,
  fetchBudgetAlerts,
  fetchBudgets,
  type AwsCostAnalyticsResponse,
  type AwsInsightResponse,
  type AwsRecommendation,
  type AwsRecommendationResponse,
  type AwsResourceResponse,
  type Budget,
  type BudgetAlert,
} from "@/services/aws-connection";
import { useCloudAccountStore } from "@/store/cloud-account-store";
import { useMonthSelectionStore } from "@/store/month-selection-store";

import { calculateHealthScore, type HealthScoreComponent } from "./health-score";

type AttentionItem = { id: string; title: string; detail: string; severity: "INFO" | "WARNING" | "CRITICAL" };

export default function HealthCenterPage() {
  const account = useCloudAccountStore((state) => state.account);
  const connectionToken = useCloudAccountStore((state) => state.connectionToken);
  const selectedMonth = useMonthSelectionStore((state) => state.selectedMonth);
  const setSelectedMonth = useMonthSelectionStore((state) => state.setSelectedMonth);
  const enabled = Boolean(account && connectionToken);
  const analytics = useQuery({
    queryKey: ["aws-cost-analytics", account?.accountId, connectionToken, selectedMonth],
    queryFn: () => fetchAwsCostAnalytics(connectionToken as string, "CURRENT_MONTH", selectedMonth),
    enabled,
    staleTime: 5 * 60 * 1000,
    retry: false,
  });
  const resources = useQuery({
    queryKey: ["aws-resources", account?.accountId, connectionToken],
    queryFn: () => fetchAwsResources(connectionToken as string),
    enabled,
    staleTime: 5 * 60 * 1000,
    retry: false,
  });
  const budgets = useQuery({
    queryKey: ["budgets", account?.accountId, selectedMonth],
    queryFn: () => fetchBudgets(connectionToken as string, selectedMonth),
    enabled,
    retry: false,
  });
  const insights = useQuery({
    queryKey: ["aws-insights", account?.accountId, connectionToken, selectedMonth],
    queryFn: () => fetchAwsInsights(connectionToken as string, selectedMonth),
    enabled,
    staleTime: 5 * 60 * 1000,
    retry: false,
  });
  const recommendations = useQuery({
    queryKey: ["aws-recommendations", account?.accountId, connectionToken, selectedMonth],
    queryFn: () => fetchAwsRecommendations(connectionToken as string, selectedMonth),
    enabled,
    staleTime: 5 * 60 * 1000,
    retry: false,
  });
  const alerts = useQuery({
    queryKey: ["health-center-alerts", account?.accountId, connectionToken, selectedMonth],
    queryFn: () => fetchBudgetAlerts(connectionToken as string, selectedMonth),
    enabled,
    retry: false,
  });

  if (!account || !connectionToken) return <ConnectionEmptyState />;

  const score = calculateHealthScore({
    analytics: analytics.data ?? null,
    budgets: budgets.data ?? null,
    resources: resources.data ?? null,
    recommendations: recommendations.data ?? null,
    errors: {
      stability: queryError(analytics.error, "Cost Analytics request failed."),
      budget: queryError(budgets.error, "Budget request failed."),
      resources: queryError(resources.error, "AWS Resources request failed."),
      optimization: queryError(recommendations.error, "Recommendations request failed."),
    },
  });
  const coreLoading = analytics.isLoading || budgets.isLoading || resources.isLoading || recommendations.isLoading;
  const queryErrors = [analytics, budgets, resources, insights, recommendations, alerts].filter((query) => query.isError).length;
  const currency = analytics.data?.currency ?? insights.data?.currency ?? "USD";
  const comparison = periodComparison(analytics.data ?? null, currency);
  const attention = collectAttention(analytics.data ?? null, budgets.data ?? null, resources.data ?? null, insights.data ?? null, recommendations.data ?? null, alerts.data ?? null);
  // const savings = potentialSavings(recommendations.data ?? null, queryError(recommendations.error, "Recommendations request failed."));
  const refresh = () => {
    void Promise.all([analytics.refetch(), resources.refetch(), budgets.refetch(), insights.refetch(), alerts.refetch()]);
  };

  return <main className="mx-auto max-w-7xl space-y-8">
    <header className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
      <div>
        <p className="text-sm font-medium text-primary">Optimization</p>
        <h1 className="mt-1 text-2xl font-semibold tracking-tight sm:text-3xl">Cloud Cost Health Center</h1>
        <p className="mt-2 text-sm text-muted-foreground">A transparent, rule-based view of your connected AWS account.</p>
      </div>
      <div className="flex flex-wrap items-center gap-3">
        <MonthSelector value={selectedMonth} onChange={setSelectedMonth} />
        <Button aria-label="Refresh health data" disabled={coreLoading} onClick={refresh} variant="outline"><RefreshCw className={coreLoading ? "size-4 animate-spin" : "size-4"} />Refresh</Button>
      </div>
    </header>

    <div className="flex flex-wrap items-center gap-2 text-xs text-muted-foreground">
      <span>Account {account.accountAlias}</span>
      <span aria-hidden="true">·</span>
      <span>Billing period {selectedMonth}</span>
      <span aria-hidden="true">·</span>
      <span>Billing source: {analytics.data?.dataSource ?? "Unavailable"}</span>
      <Badge variant="outline">Rule-based score</Badge>
    </div>

    {coreLoading && <LoadingState />}
    {queryErrors > 0 && <Card><CardContent className="flex items-start gap-3 p-4"><AlertTriangle className="mt-0.5 size-4 shrink-0 text-warning" /><div className="text-sm"><p className="font-medium">Some health data could not be loaded.</p><p className="mt-1 text-muted-foreground">Affected score components will show Data unavailable; retry to refresh the existing sources.</p><Button className="mt-3" onClick={refresh} size="sm" variant="outline">Retry available sources</Button></div></CardContent></Card>}

    {!coreLoading && <>
      <section aria-labelledby="overall-health-heading">
        <Card>
          <CardContent className="grid gap-6 p-6 md:grid-cols-[220px_1fr] md:items-center">
            <div className="flex flex-col items-center justify-center rounded-lg bg-surface-muted p-6 text-center">
              <HeartPulse className="size-7 text-primary" />
              <h2 className="mt-3 text-sm font-medium text-muted-foreground" id="overall-health-heading">Overall health score</h2>
              <p className="mt-2 text-5xl font-semibold tabular-nums">{score.score == null ? "--" : score.score}<span className="text-xl text-muted-foreground">{score.score == null ? "" : " / 100"}</span></p>
              <Badge className="mt-3" variant={score.partial ? "warning" : healthStatusVariant(score.status)}>{score.partial ? `PROVISIONAL · ${score.status}` : score.status}</Badge>
            </div>
            <div className="space-y-4">
              <div>
                <p className="text-lg font-semibold">{score.score == null ? "Health score unavailable" : `${score.unavailable.length === 0 ? "Complete" : "Partial"} health assessment`}</p>
                <p className="mt-1 text-sm leading-6 text-muted-foreground">{score.score == null ? `No score components are currently assessable. Unavailable: ${score.unavailable.join(", ")}.` : score.partial ? `This provisional score uses ${score.availableComponents} of 4 available dimensions and is normalized across those dimensions only. Unavailable components are not treated as zero: ${score.unavailable.join(", ")}.` : "Each dimension contributes 25 points. The score uses only the existing billing, budget, resource, and recommendation data shown below."}</p>
              </div>
              {score.score != null && <div className="h-3 overflow-hidden rounded-full bg-muted" role="progressbar" aria-label="Cloud cost health score" aria-valuemin={0} aria-valuemax={100} aria-valuenow={score.score}><div className={`h-full rounded-full ${healthBarColor(score.score)}`} style={{ width: `${score.score}%` }} /></div>}
              <div className="grid gap-3 sm:grid-cols-2">
                <p className="text-sm"><span className="font-medium">Score period:</span> <span className="text-muted-foreground">{selectedMonth}</span></p>
                <p className="text-sm"><span className="font-medium">Assessment basis:</span> <span className="text-muted-foreground">Actual or persisted source data only</span></p>
              </div>
            </div>
          </CardContent>
        </Card>
      </section>

      <section aria-labelledby="score-breakdown-heading" className="space-y-3">
        <div><h2 className="text-lg font-semibold" id="score-breakdown-heading">Score Breakdown</h2><p className="mt-1 text-sm text-muted-foreground">Each score is out of 25; unavailable inputs are not treated as zero.</p></div>
        <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">{score.components.map((component) => <ScoreCard component={component} key={component.key} />)}</div>
      </section>

      <section aria-labelledby="period-comparison-heading" className="space-y-3">
        <div><h2 className="text-lg font-semibold" id="period-comparison-heading">Health History / Previous Period Comparison</h2><p className="mt-1 text-sm text-muted-foreground">Actual billing comparison from the existing Cost Analytics response.</p></div>
        <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
          <MetricCard label="Current-period actual spend" value={comparison.current} detail={currency} />
          <MetricCard label="Previous-period actual spend" value={comparison.previous} detail={currency} />
          <MetricCard label="Actual spend change" value={comparison.change} detail={comparison.direction} />
          <MetricCard label="Overall score change" value="Data unavailable" detail="Historical resource snapshots are not provided by the existing resource API." />
        </div>
      </section>

      <div className="grid gap-6 xl:grid-cols-2">
        <section aria-labelledby="attention-heading" className="space-y-3">
          <div><h2 className="text-lg font-semibold" id="attention-heading">Attention Required</h2><p className="mt-1 text-sm text-muted-foreground">Existing budget alerts, persisted insights, and resource findings.</p></div>
          <AttentionPanel items={attention} loading={alerts.isLoading || insights.isLoading || resources.isLoading || recommendations.isLoading} error={alerts.isError && insights.isError && resources.isError && recommendations.isError} />
        </section>

        <section aria-labelledby="why-score-heading" className="space-y-3">
          <div><h2 className="text-lg font-semibold" id="why-score-heading">Why is my score this value?</h2><p className="mt-1 text-sm text-muted-foreground">The score is calculated from explicit rules, not a trained ML model.</p></div>
          <Card><CardContent className="space-y-4 p-5">
            {score.components.map((component) => <div className="border-b border-border pb-3 last:border-0 last:pb-0" key={component.key}><div className="flex items-center justify-between gap-3"><p className="text-sm font-medium">{component.title}</p><span className="text-sm font-semibold tabular-nums">{component.score == null ? "Data unavailable" : `${component.score}/25`}</span></div><p className="mt-1 text-sm text-muted-foreground">{component.evidence}</p></div>)}
            <RuleSummary />
          </CardContent></Card>
        </section>
      </div>

      <div className="grid gap-6 xl:grid-cols-1">
        <section aria-labelledby="suggestions-heading" className="space-y-3">
          <div><h2 className="text-lg font-semibold" id="suggestions-heading">Improvement Suggestions</h2><p className="mt-1 text-sm text-muted-foreground">Rule-based guidance and actions from stored recommendations.</p></div>
          <SuggestionsPanel components={score.components} recommendations={recommendations.data ?? null} />
        </section>
      </div>
    </>}
  </main>;
}

function ScoreCard({ component }: { component: HealthScoreComponent }) {
  const available = component.score != null;
  return <Card><CardContent className="p-5"><div className="flex items-start justify-between gap-3"><p className="text-sm font-medium">{component.title}</p><Badge variant={available ? componentBadgeVariant(component.score!) : "outline"}>{available ? `${component.score}/25` : "Unavailable"}</Badge></div><p className="mt-3 text-sm leading-6 text-muted-foreground">{component.evidence}</p>{component.suggestion && <p className="mt-3 flex items-start gap-2 text-sm"><Sparkles className="mt-0.5 size-4 shrink-0 text-primary" />{component.suggestion}</p>}</CardContent></Card>;
}

function MetricCard({ label, value, detail }: { label: string; value: string; detail: string }) {
  return <Card><CardContent className="p-5"><p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">{label}</p><p className="mt-2 text-xl font-semibold tabular-nums">{value}</p><p className="mt-1 text-xs text-muted-foreground">{detail}</p></CardContent></Card>;
}

function AttentionPanel({ items, loading, error }: { items: AttentionItem[]; loading: boolean; error: boolean }) {
  if (loading) return <Card><CardContent className="flex min-h-40 items-center justify-center text-sm text-muted-foreground"><LoaderCircle className="mr-2 size-4 animate-spin" />Loading existing findings...</CardContent></Card>;
  if (error) return <Card><CardContent className="flex min-h-40 items-center justify-center text-sm text-muted-foreground">Attention data is unavailable.</CardContent></Card>;
  if (items.length === 0) return <Card><CardContent className="flex min-h-40 flex-col items-center justify-center text-center"><CheckCircle2 className="size-7 text-success" /><p className="mt-3 text-sm font-medium">No current attention items in the available data.</p><p className="mt-1 text-xs text-muted-foreground">This does not imply that unavailable sources are healthy.</p></CardContent></Card>;
  return <Card><CardContent className="divide-y divide-border p-5">{items.slice(0, 8).map((item) => <div className="flex items-start gap-3 py-3 first:pt-0 last:pb-0" key={item.id}><AlertTriangle className={`mt-0.5 size-4 shrink-0 ${attentionColor(item.severity)}`} /><div className="min-w-0 flex-1"><div className="flex flex-wrap items-center gap-2"><p className="text-sm font-medium">{item.title}</p><Badge variant={componentBadgeVariantBySeverity(item.severity)}>{item.severity}</Badge></div><p className="mt-1 text-sm text-muted-foreground">{item.detail}</p></div></div>)}</CardContent></Card>;
}

function SuggestionsPanel({ components, recommendations }: { components: HealthScoreComponent[]; recommendations: AwsRecommendationResponse | null }) {
  const componentSuggestions = components.filter((component) => component.suggestion).map((component) => ({
    id: component.key,
    title: component.title,
    detail: component.suggestion!,
  }));
  const storedRecommendations = (recommendations?.recommendations ?? []).filter(isOpenRecommendation).slice(0, 5).map((recommendation) => ({
    id: recommendation.id,
    title: recommendation.problem,
    detail: recommendation.recommendedAction || recommendation.recommendation,
  }));
  const suggestions = [...componentSuggestions, ...storedRecommendations];
  if (suggestions.length === 0) return <Card><CardContent className="flex min-h-40 items-center justify-center p-5 text-center text-sm text-muted-foreground">No improvement suggestions are available from the current data.</CardContent></Card>;
  return <Card><CardContent className="divide-y divide-border p-5">{suggestions.map((suggestion) => <div className="py-3 first:pt-0 last:pb-0" key={suggestion.id}><p className="text-sm font-medium">{suggestion.title}</p><p className="mt-1 text-sm leading-6 text-muted-foreground">{suggestion.detail}</p></div>)}</CardContent></Card>;
}

// function PotentialSavings({ value, currency, detail }: { value: number | null; currency: string | null; detail: string }) {
//   return <Card><CardContent className="flex min-h-40 flex-col justify-center p-5"><p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">Stored monthly estimate</p><p className="mt-2 text-3xl font-semibold tabular-nums">{value == null || !currency ? "Data unavailable" : formatMoney(value, currency)}</p><p className="mt-2 text-sm text-muted-foreground">{detail}</p></CardContent></Card>;
// }

function RuleSummary() {
  return <div className="rounded-lg bg-surface-muted p-4"><p className="text-sm font-medium">Scoring rules</p><ul className="mt-2 space-y-1.5 text-xs leading-5 text-muted-foreground"><li>Budget: highest enabled-budget utilization (≤50%, ≤75%, ≤90%, ≤100%, above 100%).</li><li>Stability: absolute period-over-period actual-spend change (≤5%, ≤15%, ≤30%, ≤50%, above 50%).</li><li>Resources: 15 points scaled by reported healthy/status-tracked resources, plus 10 points scaled by unattached-volume share.</li><li>Optimization: 25 points less 8 per open High, 4 per Medium, and 2 per Low stored recommendation.</li></ul></div>;
}

function LoadingState() {
  return <Card><CardContent className="flex min-h-[240px] items-center justify-center text-sm text-muted-foreground"><LoaderCircle className="mr-2 size-4 animate-spin" />Loading existing AWS health data...</CardContent></Card>;
}

function ConnectionEmptyState() {
  return <Card><CardContent className="flex min-h-[320px] flex-col items-center justify-center px-6 py-10 text-center"><ShieldCheck className="size-9 text-primary" /><h1 className="mt-4 text-xl font-semibold">Connect an AWS account first</h1><p className="mt-2 text-sm text-muted-foreground">The Health Center uses the account already connected to CloudCostAI.</p><Button asChild className="mt-5"><Link href="/integrations">Go to Cloud Accounts</Link></Button></CardContent></Card>;
}

function collectAttention(analytics: AwsCostAnalyticsResponse | null, budgets: Budget[] | null, resources: AwsResourceResponse | null, insights: AwsInsightResponse | null, recommendations: AwsRecommendationResponse | null, alerts: BudgetAlert[] | null): AttentionItem[] {
  const items: AttentionItem[] = [];
  for (const budget of budgets ?? []) {
    if (!budget.enabled || budget.usagePercentage == null || budget.usagePercentage < 75) continue;
    items.push({
      id: `budget-${budget.id}`,
      title: budget.usagePercentage >= 100 ? "Budget exceeded" : "Budget threshold reached",
      detail: `${budget.name} is ${budget.usagePercentage.toFixed(1)}% used${budget.serviceName ? ` for ${budget.serviceName}` : ""}.`,
      severity: budget.usagePercentage >= 100 ? "CRITICAL" : budget.usagePercentage >= 90 ? "WARNING" : "INFO",
    });
  }
  for (const alert of alerts ?? []) {
    if (alert.severity === "INFO") continue;
    items.push({ id: `alert-${alert.id}`, title: alert.title, detail: alert.message, severity: alert.severity });
  }
  for (const insight of insights?.insights ?? []) {
    if (insight.severity === "Low") continue;
    items.push({
      id: `insight-${insight.id}`,
      title: insight.problemDetected,
      detail: `${insight.service} · ${insight.reason}`,
      severity: insight.severity === "High" ? "CRITICAL" : "WARNING",
    });
  }
  for (const recommendation of recommendations?.recommendations ?? []) {
    if (!isOpenRecommendation(recommendation) || recommendation.priority.toLowerCase() !== "high") continue;
    items.push({
      id: `recommendation-${recommendation.id}`,
      title: recommendation.problem,
      detail: `${recommendation.service} · ${recommendation.reason}`,
      severity: "WARNING",
    });
  }
  const unattached = resources?.summary.unusedVolumes;
  if (unattached != null && unattached > 0) {
    items.push({ id: "unattached-volumes", title: "Unattached EBS volumes reported", detail: `${unattached} volume${unattached === 1 ? " is" : "s are"} unattached in the current AWS resource snapshot.`, severity: "INFO" });
  }
  if (analytics?.dataStatus === "AVAILABLE" && analytics.anomalies.length > 0) {
    items.push({ id: "analytics-anomalies", title: "Existing Cost Analytics findings", detail: `${analytics.anomalies.length} finding${analytics.anomalies.length === 1 ? "" : "s"} were returned by Cost Analytics for this billing period.`, severity: "WARNING" });
  }
  return items;
}

// function potentialSavings(recommendations: AwsRecommendationResponse | null, error?: string) {
//   if (!recommendations || recommendations.status === "NO_DATA") return { value: null, detail: recommendations?.message ?? error ?? "No persisted recommendation estimate is available for this period." };
//   const estimates = recommendations.recommendations.filter(isOpenRecommendation)
//     .map((item) => item.estimatedMonthlySaving)
//     .filter((value): value is number => value != null && Number.isFinite(value) && value > 0);
//   if (estimates.length === 0) return { value: null, detail: "No positive stored monthly savings estimates are available for open recommendations." };
//   return { value: estimates.reduce((sum, value) => sum + value, 0), detail: "Sum of stored monthly savings estimates for open recommendations; not a guaranteed saving." };
// }

function isOpenRecommendation(recommendation: AwsRecommendation) {
  const status = recommendation.status.toLowerCase();
  return status === "new" || status === "reviewed";
}

function periodComparison(analytics: AwsCostAnalyticsResponse | null, currency: string) {
  if (!analytics || analytics.dataStatus !== "AVAILABLE") {
    return { current: "Data unavailable", previous: "Data unavailable", change: "Data unavailable", direction: "No persisted actuals" };
  }
  const current = analytics.summary.totalCost;
  const previous = analytics.summary.previousPeriodCost;
  const difference = analytics.summary.difference;
  const percentage = analytics.summary.percentageChange;
  return {
    current: current == null ? "Data unavailable" : formatMoney(current, currency),
    previous: previous == null ? "Data unavailable" : formatMoney(previous, currency),
    change: difference == null ? "Data unavailable" : formatMoney(difference, currency),
    direction: percentage == null ? "Percentage unavailable" : `${percentage > 0 ? "+" : ""}${percentage.toFixed(1)}%`,
  };
}

function queryError(error: unknown, fallback: string) {
  return error instanceof Error ? error.message : error ? fallback : undefined;
}

function formatMoney(value: number, currency: string) {
  try {
    return new Intl.NumberFormat(undefined, { style: "currency", currency, minimumFractionDigits: 2 }).format(value);
  } catch {
    return `${value.toFixed(2)} ${currency}`;
  }
}

function healthStatusVariant(status: string): "success" | "info" | "warning" | "destructive" | "outline" {
  if (status === "EXCELLENT" || status === "GOOD") return "success";
  if (status === "FAIR") return "warning";
  if (status === "NEEDS ATTENTION") return "destructive";
  return "outline";
}

function componentBadgeVariant(score: number): "success" | "info" | "warning" | "destructive" {
  return score >= 20 ? "success" : score >= 15 ? "info" : score >= 8 ? "warning" : "destructive";
}

function componentBadgeVariantBySeverity(severity: AttentionItem["severity"]): "info" | "warning" | "destructive" {
  return severity === "CRITICAL" ? "destructive" : severity === "WARNING" ? "warning" : "info";
}

function attentionColor(severity: AttentionItem["severity"]) {
  return severity === "CRITICAL" ? "text-destructive" : severity === "WARNING" ? "text-warning" : "text-info";
}

function healthBarColor(score: number) {
  return score >= 80 ? "bg-success" : score >= 65 ? "bg-warning" : "bg-destructive";
}