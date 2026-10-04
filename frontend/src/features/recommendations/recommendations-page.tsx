"use client";

import Link from "next/link";
import { AlertTriangle, CheckCircle2, Lightbulb, LoaderCircle, TrendingDown } from "lucide-react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { fetchAwsRecommendations, generateAwsRecommendations, simulateAwsCost, updateAwsRecommendationStatus, type AwsCostSimulationResponse, type AwsRecommendation } from "@/services/aws-connection";
import { useCloudAccountStore } from "@/store/cloud-account-store";
import { useMonthSelectionStore } from "@/store/month-selection-store";
import { MonthSelector } from "@/components/month-selector";
import { syncAwsBilling } from "@/services/aws-bill";

const statusStyle = { New: "info", Applied: "success", Dismissed: "outline", NEW: "info", REVIEWED: "success", DISMISSED: "outline" } as const;

export default function RecommendationsPage({ compact = false, month }: { compact?: boolean; month?: string }) {
  const { account, connectionToken } = useCloudAccountStore();
  const storedMonth = useMonthSelectionStore((state) => state.selectedMonth);
  const selectedMonth = month ?? storedMonth;
  const setSelectedMonth = useMonthSelectionStore((state) => state.setSelectedMonth);
  const queryClient = useQueryClient();
  const [priority, setPriority] = useState("All");
  const [service, setService] = useState("All");
  const [region, setRegion] = useState("All");
  const [category, setCategory] = useState("All");
  const [status, setStatus] = useState("All");
  const queryKey = ["aws-recommendations", account?.accountId, connectionToken, selectedMonth];
  const query = useQuery({ queryKey, queryFn: () => fetchAwsRecommendations(connectionToken as string, selectedMonth), enabled: Boolean(connectionToken), staleTime: 5 * 60 * 1000, retry: false });
  const mutation = useMutation({ mutationFn: ({ id, value }: { id: string; value: AwsRecommendation["status"] }) => updateAwsRecommendationStatus(connectionToken as string, id, value, selectedMonth), onSuccess: (data) => queryClient.setQueryData(queryKey, data) });
  const generateMutation = useMutation({ mutationFn: () => generateAwsRecommendations(connectionToken as string, selectedMonth), onSuccess: (data) => queryClient.setQueryData(queryKey, data) });
  const syncMutation = useMutation({
    mutationFn: () => syncAwsBilling(connectionToken as string),
    onSuccess: async () => queryClient.invalidateQueries({ queryKey }),
  });

  if (!account || !connectionToken) return <Card><CardContent className="flex min-h-[280px] flex-col items-center justify-center text-center"><Lightbulb className="size-9 text-primary" /><p className="mt-4 font-medium">Connect an AWS account first</p><p className="mt-2 text-sm text-muted-foreground">Recommendations analyze stored billing data and use live resource state only for rules that require it.</p></CardContent></Card>;
  if (query.isLoading) return <Card><CardContent className="flex min-h-[220px] items-center justify-center text-sm text-muted-foreground"><LoaderCircle className="mr-2 size-4 animate-spin" />Analyzing AWS cost data...</CardContent></Card>;
  if (query.isError) return <Card><CardContent className="flex min-h-[220px] flex-col items-center justify-center text-center"><AlertTriangle className="size-8 text-destructive" /><p className="mt-3 font-medium">Unable to load recommendations</p><p role="alert" className="mt-2 text-sm text-destructive">{query.error instanceof Error ? query.error.message : "Recommendation data could not be loaded."}</p><Button className="mt-4" onClick={() => query.refetch()} variant="outline">Try again</Button></CardContent></Card>;

  const all = query.data?.recommendations ?? [];
  const filtered = all.filter((item) => (priority === "All" || item.priority === priority) && (service === "All" || item.service === service) && (region === "All" || item.region === region) && (category === "All" || item.category === category) && (status === "All" || item.status === status));
  const visible = compact ? filtered.slice(0, 4) : filtered;
  return <div className={compact ? "space-y-4" : "mx-auto max-w-7xl space-y-8"}>
    {!compact && <header className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between"><div><p className="text-sm font-medium text-primary">Optimization</p><h1 className="mt-1 text-2xl font-semibold tracking-tight sm:text-3xl">AWS Optimization Recommendations</h1><p className="mt-2 text-sm text-muted-foreground">Actionable findings generated from stored PostgreSQL billing data.</p></div><div className="flex flex-wrap items-center gap-3"><MonthSelector value={selectedMonth} onChange={setSelectedMonth} /><Button variant="outline" onClick={() => syncMutation.mutate()} disabled={syncMutation.isPending}>{syncMutation.isPending ? "Syncing CUR..." : "Sync CUR data"}</Button><Button onClick={() => generateMutation.mutate()} disabled={generateMutation.isPending || syncMutation.isPending}>{generateMutation.isPending ? "Checking AWS evidence..." : "Generate recommendations"}</Button></div></header>}
    <Card><CardHeader className="flex flex-row items-center justify-between"><CardTitle className="flex items-center gap-2"><Lightbulb className="size-5 text-warning" />{compact ? "Recommendations" : "AWS cost recommendations"}</CardTitle><Badge variant="secondary">{all.length} findings</Badge></CardHeader><CardContent>
      {!compact && <><div className="mb-5 grid gap-3 sm:grid-cols-3 xl:grid-cols-6"><Summary label="Billing month" value={selectedMonth} /><Summary label="Total spend" value={query.data?.totalSpend == null ? "NO_DATA" : new Intl.NumberFormat("en-US", { style: "currency", currency: query.data.currency ?? "USD" }).format(query.data.totalSpend)} /><Summary label="Findings" value={all.length} /><Summary label="High priority" value={all.filter((item) => item.priority === "High").length} /><Summary label="Medium priority" value={all.filter((item) => item.priority === "Medium").length} /><Summary label="Low priority" value={all.filter((item) => item.priority === "Low").length} /></div><div className="mb-5 flex flex-col gap-3 rounded-lg border border-border bg-surface-muted p-3 sm:flex-row sm:flex-wrap"><Filter label="Service" value={service} options={["All", ...new Set(all.map((item) => item.service))]} onChange={setService} /><Filter label="Region" value={region} options={["All", ...new Set(all.map((item) => item.region))]} onChange={setRegion} /><Filter label="Severity" value={priority} options={["All", "High", "Medium", "Low"]} onChange={setPriority} /><Filter label="Category" value={category} options={["All", ...new Set(all.map((item) => item.category))]} onChange={setCategory} /><Filter label="Status" value={status} options={["All", "New", "Applied", "Dismissed"]} onChange={setStatus} /></div></>}
      {generateMutation.isPending && <p aria-live="polite" className="py-4 text-center text-sm text-muted-foreground"><LoaderCircle className="mr-2 inline size-4 animate-spin" />Analyzing stored billing evidence for {selectedMonth}...</p>}
      {generateMutation.isError && <p role="alert" className="mb-4 rounded-lg border border-destructive/30 bg-destructive/5 p-3 text-sm text-destructive">{generateMutation.error instanceof Error ? generateMutation.error.message : "Recommendation generation failed. Try again."}</p>}
      {syncMutation.isError && <p role="alert" className="mb-4 rounded-lg border border-destructive/30 bg-destructive/5 p-3 text-sm text-destructive">{syncMutation.error instanceof Error ? syncMutation.error.message : "CUR billing sync failed. Check the AWS connection and S3 export configuration."}</p>}
      {syncMutation.isSuccess && <p role="status" className="mb-4 rounded-lg border border-success/30 bg-success/5 p-3 text-sm text-success">CUR billing sync completed. Generate recommendations to evaluate the updated billing data.</p>}
      {mutation.isError && <p role="alert" className="mb-4 rounded-lg border border-destructive/30 bg-destructive/5 p-3 text-sm text-destructive">{mutation.error instanceof Error ? mutation.error.message : "Recommendation status could not be updated."}</p>}
      {query.data?.resourceDataStatus === "PERMISSION_DENIED" && <p role="alert" className="mb-4 rounded-lg border border-destructive/30 bg-destructive/5 p-3 text-sm text-destructive">{query.data.message ?? "AWS denied EC2/EBS resource discovery. Verify ec2:DescribeInstances and ec2:DescribeVolumes permissions."}</p>}
      {query.data?.resourceDataStatus === "UNAVAILABLE" && <p role="alert" className="mb-4 rounded-lg border border-warning/30 bg-warning/5 p-3 text-sm text-warning">{query.data.message ?? "Resource data unavailable. Reconnect AWS and generate recommendations again."}</p>}
      {all.length > 0 ? visible.length === 0
        ? <div className="py-12 text-center"><CheckCircle2 className="mx-auto size-8 text-muted-foreground" /><p className="mt-3 text-sm font-medium">No recommendations match these filters</p><p className="mt-1 text-sm text-muted-foreground">Recommendations appear when stored data supports an action.</p></div>
        : <div className="space-y-4">{visible.map((item) => <RecommendationCard currency={query.data?.currency ?? "USD"} item={item} key={item.id} onStatus={(value) => mutation.mutate({ id: item.id, value })} />)}</div>
        : query.data?.status === "NO_DATA"
          ? <div className="py-12 text-center"><CheckCircle2 className="mx-auto size-8 text-muted-foreground" /><p className="mt-3 text-sm font-medium">{query.data.message ?? "No billing data available for this period."}</p><p className="mt-1 text-sm text-muted-foreground">Selected period: {selectedMonth}</p></div>
          : query.data?.status === "PERMISSION_DENIED"
            ? <div className="py-12 text-center"><AlertTriangle className="mx-auto size-8 text-destructive" /><p className="mt-3 text-sm font-medium">AWS read permission denied</p><p className="mt-1 text-sm text-muted-foreground">{query.data.message}</p></div>
            : query.data?.status === "RESOURCE_UNAVAILABLE" || query.data?.resourceDataStatus === "UNAVAILABLE"
            ? <div className="py-12 text-center"><AlertTriangle className="mx-auto size-8 text-warning" /><p className="mt-3 text-sm font-medium">Resource data unavailable</p><p className="mt-1 text-sm text-muted-foreground">Reconnect AWS and generate recommendations again.</p></div>
            : query.data?.status === "NO_RESOURCES"
              ? <div className="py-12 text-center"><AlertTriangle className="mx-auto size-8 text-muted-foreground" /><p className="mt-3 text-sm font-medium">No EC2 or EBS resources found</p><p className="mt-1 text-sm text-muted-foreground">No instances or volumes were found in any enabled AWS region. Verify the connected account and EC2 read access.</p></div>
            : query.data?.status === "NO_RESOURCE_BILLING_MATCH" || query.data?.status === "INSUFFICIENT_BILLING_EVIDENCE"
              ? <div className="py-12 text-center"><AlertTriangle className="mx-auto size-8 text-warning" /><p className="mt-3 text-sm font-medium">{query.data.status === "NO_RESOURCE_BILLING_MATCH" ? "CUR billing did not match discovered resources" : "Stored billing evidence is insufficient for a supported recommendation"}</p>{query.data.message && <p className="mt-1 text-sm text-muted-foreground">{query.data.message}</p>}{query.data.evaluationReasons.length > 0 && <ul className="mx-auto mt-3 max-w-xl list-disc space-y-1 pl-5 text-left text-sm text-muted-foreground">{query.data.evaluationReasons.map((reason) => <li key={reason}>{reason}</li>)}</ul>}</div>
            : query.data?.analysisComplete && query.data.status === "NO_RECOMMENDATIONS"
              ? <div className="py-12 text-center"><CheckCircle2 className="mx-auto size-8 text-success" /><p className="mt-3 text-sm font-medium">No optimization opportunities found for this period.</p>{query.data.evaluationReasons.length > 0 && <ul className="mx-auto mt-3 max-w-xl list-disc space-y-1 pl-5 text-left text-sm text-muted-foreground">{query.data.evaluationReasons.map((reason) => <li key={reason}>{reason}</li>)}</ul>}</div>
              : query.data?.status === "NO_RECOMMENDATIONS"
                ? <div className="py-12 text-center"><Lightbulb className="mx-auto size-8 text-muted-foreground" /><p className="mt-3 text-sm font-medium">Recommendations have not been generated for this period yet.</p><p className="mt-1 text-sm text-muted-foreground">Select Generate recommendations to evaluate current AWS billing and resource evidence.</p></div>
                : <div className="py-12 text-center"><AlertTriangle className="mx-auto size-8 text-warning" /><p className="mt-3 text-sm font-medium">No findings were returned</p><p className="mt-1 text-sm text-muted-foreground">The recommendation response was empty or incomplete. Generate recommendations again.</p></div>}
      {compact && all.length > visible.length && <Button asChild className="mt-5 w-full" variant="outline"><Link href="/recommendations">View all recommendations</Link></Button>}
    </CardContent></Card>
    {!compact && <CostSimulator key={`${account.accountId}-${selectedMonth}`} accountId={account.accountId} connectionToken={connectionToken} month={selectedMonth} recommendations={all} currentMonthlyCost={query.data?.totalSpend ?? null} currency={query.data?.currency ?? "USD"} />}
  </div>;
}

function CostSimulator({ accountId, connectionToken, month, recommendations, currentMonthlyCost, currency }: {
  accountId: string;
  connectionToken: string;
  month: string;
  recommendations: AwsRecommendation[];
  currentMonthlyCost: number | null;
  currency: string;
}) {
  const [selectedIds, setSelectedIds] = useState<string[]>([]);
  const simulation = useMutation({
    mutationFn: () => simulateAwsCost(connectionToken, month, selectedRecommendations.map((recommendation) => recommendation.id)),
    retry: false,
  });
  const money = (value: number | null) => value == null
    ? "NO_DATA"
    : new Intl.NumberFormat("en-US", { style: "currency", currency }).format(value);
  const readyRecommendations = recommendations.filter((recommendation) => {
    const status = recommendation.status.toLowerCase();
    const saving = recommendation.estimatedMonthlySaving;
    return (status === "new" || status === "reviewed")
      && saving != null && Number.isFinite(saving) && saving > 0;
  });
  const selectedRecommendations = readyRecommendations.filter((recommendation) => selectedIds.includes(recommendation.id));
  const selectedSavings = selectedRecommendations.reduce((total, recommendation) => total + (recommendation.estimatedMonthlySaving ?? 0), 0);
  const hasSelectedSavings = selectedSavings > 0;
  const projectedCost = currentMonthlyCost == null ? null : Math.max(0, currentMonthlyCost - selectedSavings);
  const savingsPercentage = currentMonthlyCost == null || currentMonthlyCost <= 0
    ? null
    : Math.min(100, (selectedSavings / currentMonthlyCost) * 100);
  const toggle = (id: string, checked: boolean) => {
    setSelectedIds((current) => checked ? [...current, id] : current.filter((selected) => selected !== id));
    simulation.reset();
  };
  const result = simulation.data;
  const canCompare = result?.status === "SUCCESS"
    && result.currentMonthlyCost != null && result.estimatedMonthlyCost != null;
  const maxCost = canCompare ? Math.max(result.currentMonthlyCost ?? 0, result.estimatedMonthlyCost ?? 0, 0.01) : 1;

  return <section aria-labelledby="cost-simulator-title" className="space-y-5 rounded-xl border border-border bg-card p-5">
    <header className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
      <div>
        <p className="text-sm font-medium text-primary">Optimization</p>
        <h2 className="mt-1 text-xl font-semibold" id="cost-simulator-title">Cost Simulator</h2>
        <p className="mt-1 text-sm text-muted-foreground">Estimated cost based on current billing and saved optimization recommendations. No AWS resources are changed.</p>
      </div>
      <p className="shrink-0 text-xs text-muted-foreground">Account {accountId} · {month}</p>
    </header>

    <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-5">
      <SimulationMetric label="Current monthly cost" value={money(currentMonthlyCost)} />
      <SimulationMetric label="Selected scenarios" value={String(selectedRecommendations.length)} />
      {hasSelectedSavings && <SimulationMetric label="Estimated monthly savings" value={money(selectedSavings)} />}
      <SimulationMetric label="Projected monthly cost" value={money(projectedCost)} />
      {hasSelectedSavings && <SimulationMetric label="Estimated savings percentage" value={savingsPercentage == null ? "Unavailable" : `${savingsPercentage.toFixed(1)}%`} />}
    </div>

    {readyRecommendations.length === 0 && <div className="rounded-lg border border-dashed border-border bg-surface-muted p-4">
      <p className="text-sm font-medium">No simulation-ready recommendations are available for this account.</p>
      <p className="mt-1 text-sm text-muted-foreground">Simulation requires a recommendation with a verified monthly savings estimate.</p>
    </div>}

    {recommendations.length > 0 && <fieldset className="space-y-2" disabled={simulation.isPending}>
        <legend className="mb-2 text-sm font-medium">Optimization scenarios</legend>
        {recommendations.map((recommendation) => {
          const status = recommendation.status.toLowerCase();
          const alreadyApplied = status === "applied";
          const dismissed = status === "dismissed";
          const actionable = status === "new" || status === "reviewed";
          const saving = recommendation.estimatedMonthlySaving;
          const available = readyRecommendations.some((item) => item.id === recommendation.id);
          const overlapsSelection = available && !selectedIds.includes(recommendation.id)
            && selectedRecommendations.some((selected) => scenariosOverlap(selected, recommendation));
          const unavailableReason = alreadyApplied ? "Already applied" : dismissed ? "Dismissed" : !actionable ? "Not actionable" : overlapsSelection ? "Overlaps a selected scenario" : saving == null ? "" : "Pricing estimate unavailable";
          const canSelect = available && !overlapsSelection;
          return <label aria-disabled={!canSelect} className={`flex items-start gap-3 rounded-lg border border-border p-3 ${canSelect ? "cursor-pointer bg-background hover:border-primary/50" : "cursor-not-allowed bg-muted/40 opacity-65"}`} key={recommendation.id}>
            <input aria-label={`Select ${recommendation.problem}`} checked={available && selectedIds.includes(recommendation.id)} className="mt-1 accent-primary" disabled={!canSelect || simulation.isPending} onChange={(event) => toggle(recommendation.id, event.target.checked)} type="checkbox" />
            <span className="min-w-0 flex-1"><span className="flex flex-wrap items-center justify-between gap-2"><span className="font-medium">{recommendation.problem}</span><span className={`text-sm font-medium ${canSelect ? "text-success" : "text-muted-foreground"}`}>{available ? `${money(saving)} / month` : unavailableReason}</span></span><span className="mt-1 block text-sm text-muted-foreground">{recommendation.service} · {recommendation.resource}{recommendation.region ? ` · ${recommendation.region}` : ""}</span>{!available && !alreadyApplied && <span className="mt-2 block text-xs text-muted-foreground"><span className="font-medium">Why unavailable:</span> {dismissed ? "This recommendation was dismissed." : !actionable ? "This recommendation is not actionable in its current status." : saving != null && saving < 0 ? "The saved monthly savings estimate is invalid." : "No verified monthly savings estimate is stored."}</span>}{overlapsSelection && <span className="mt-2 block text-xs text-muted-foreground"><span className="font-medium">Why unavailable:</span> This scenario overlaps a selected optimization.</span>}</span>
          </label>;
        })}
      </fieldset>}

    {simulation.isError && <p className="text-sm text-destructive">{simulation.error instanceof Error ? simulation.error.message : "Unable to simulate the selected optimizations."}</p>}
    <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
      <p className="text-xs text-muted-foreground">Estimates reuse saved monthly savings; missing pricing is never guessed. Simulation does not modify AWS resources or billing data.</p>
      <Button disabled={selectedRecommendations.length === 0 || simulation.isPending} onClick={() => simulation.mutate()}>
        {simulation.isPending ? "Simulating..." : "Simulate"}
      </Button>
    </div>

    {result && <SimulationResult result={result} currency={currency} maxCost={maxCost} />}
  </section>;
}

function SimulationResult({ result, currency, maxCost }: {
  result: AwsCostSimulationResponse;
  currency: string;
  maxCost: number;
}) {
  const money = (value: number | null) => value == null
    ? "NO_DATA"
    : new Intl.NumberFormat("en-US", { style: "currency", currency }).format(value);
  if (result.status === "NO_BILLING_DATA") {
    return <div className="rounded-lg border border-warning/30 bg-warning/5 p-4 text-sm text-warning">{result.message ?? "Billing data is unavailable for this month."}</div>;
  }
  if (result.status === "INSUFFICIENT_PRICING_DATA") {
    return <div className="space-y-3 rounded-lg border border-warning/30 bg-warning/5 p-4">
      <p className="text-sm font-medium text-warning">{result.message ?? "Pricing data is insufficient for a complete estimate."}</p>
      <ScenarioBreakdown scenarios={result.scenarios} currency={currency} />
    </div>;
  }
  if (result.status !== "SUCCESS") return <div className="rounded-lg border border-destructive/30 p-4 text-sm text-destructive">Simulation could not be completed.</div>;
  const savingsPercentage = result.currentMonthlyCost == null || result.currentMonthlyCost <= 0 || result.estimatedMonthlySaving == null
    ? null
    : Math.min(100, (result.estimatedMonthlySaving / result.currentMonthlyCost) * 100);

  return <div className="space-y-4 border-t border-border pt-5" aria-label="Simulation results">
    <h3 className="text-base font-semibold">Simulation result</h3>
    <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-5">
      <SimulationMetric label="Current monthly cost" value={money(result.currentMonthlyCost)} />
      <SimulationMetric label="Estimated monthly savings" value={money(result.estimatedMonthlySaving)} />
      <SimulationMetric label="Projected monthly cost" value={money(result.estimatedMonthlyCost)} />
      <SimulationMetric label="Estimated savings percentage" value={savingsPercentage == null ? "NO_DATA" : `${savingsPercentage.toFixed(1)}%`} />
      <SimulationMetric label="Estimated annual saving" value={money(result.estimatedAnnualSaving)} />
    </div>
    <div className="rounded-lg border border-border p-4">
      <h3 className="text-sm font-medium">Before vs After</h3>
      <div className="mt-4 space-y-4">
        <CostBar label="Current" value={result.currentMonthlyCost} maxCost={maxCost} currency={currency} />
        <CostBar label="Estimated" value={result.estimatedMonthlyCost} maxCost={maxCost} currency={currency} />
      </div>
    </div>
    <ScenarioBreakdown scenarios={result.scenarios} currency={currency} />
  </div>;
}

function scenariosOverlap(first: AwsRecommendation, second: AwsRecommendation) {
  const firstService = first.service.toLowerCase();
  const secondService = second.service.toLowerCase();
  const firstResource = first.resourceId?.trim();
  const secondResource = second.resourceId?.trim();
  if (firstResource && secondResource) return firstResource === secondResource;
  if (firstService !== secondService) return false;
  return !firstResource || !secondResource;
}

function CostBar({ label, value, maxCost, currency }: { label: string; value: number | null; maxCost: number; currency: string }) {
  const percent = value == null ? 0 : Math.min(100, (value / maxCost) * 100);
  return <div><div className="mb-1 flex justify-between gap-3 text-sm"><span>{label}</span><span className="font-medium">{value == null ? "NO_DATA" : new Intl.NumberFormat("en-US", { style: "currency", currency }).format(value)}</span></div><div aria-label={`${label} cost`} className="h-3 overflow-hidden rounded-full bg-muted"><div className="h-full rounded-full bg-primary transition-[width]" style={{ width: `${percent}%` }} /></div></div>;
}

function SimulationMetric({ label, value }: { label: string; value: string }) {
  return <div className="rounded-lg border border-border p-4"><p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">{label}</p><p className="mt-2 text-xl font-semibold">{value}</p></div>;
}

function ScenarioBreakdown({ scenarios, currency }: { scenarios: AwsCostSimulationResponse["scenarios"]; currency: string }) {
  const money = (value: number | null) => value == null
    ? "NO_DATA"
    : new Intl.NumberFormat("en-US", { style: "currency", currency }).format(value);
  return <div className="space-y-2" aria-label="Scenario breakdown">
    {scenarios.map((scenario) => <div className="flex flex-wrap justify-between gap-2 text-sm" key={scenario.recommendationId}>
      <span>{scenario.title}</span>
      <span className={scenario.status === "AVAILABLE" ? "font-medium text-success" : "text-warning"}>
        {scenario.status === "AVAILABLE" ? `-${money(scenario.estimatedMonthlySaving)} / month` : scenario.message ?? "Unavailable"}
      </span>
    </div>)}
  </div>;
}

function Filter({ label, value, options, onChange }: { label: string; value: string; options: Iterable<string>; onChange: (value: string) => void }) { return <label className="flex items-center gap-2 text-sm"><span className="text-xs font-medium text-muted-foreground">{label}</span><select className="h-9 rounded-lg border border-input bg-background px-2.5 text-sm" onChange={(event) => onChange(event.target.value)} value={value}>{[...options].map((option) => <option key={option}>{option}</option>)}</select></label>; }

function Summary({ label, value }: { label: string; value: number | string }) {
  const displayLabel = label === "Total spend" ? "Total AWS Cost" : label === "Findings" ? "Total Findings" : label;
  const displayValue = label === "Billing month" && typeof value === "string" ? formatBillingMonth(value) : value;
  return <div className="rounded-lg border border-border bg-surface-muted p-3"><p className="text-xs text-muted-foreground">{displayLabel}</p><p className="mt-1 truncate text-xl font-semibold" title={String(displayValue)}>{displayValue}</p></div>;
}

function formatBillingMonth(value: string) {
  const [year, month] = value.split("-").map(Number);
  if (!year || !month) return value;
  return new Intl.DateTimeFormat("en-US", { month: "long", year: "numeric", timeZone: "UTC" })
    .format(new Date(Date.UTC(year, month - 1, 1)));
}

function RecommendationCard({ item, currency, onStatus }: { item: AwsRecommendation; currency: string; onStatus: (value: AwsRecommendation["status"]) => void }) {
  const [resourceUnavailable, setResourceUnavailable] = useState(false);
  const money = (value: number | null) => value == null
    ? "NO_DATA"
    : new Intl.NumberFormat("en-US", { style: "currency", currency, maximumFractionDigits: 6 }).format(value);
  const normalizedService = item.service.toLowerCase();
  const serviceName = normalizedService.includes("ebs") || normalizedService.includes("elastic block store")
    ? "EBS"
    : item.service;
  const dataSource = item.dataSource === "COST_EXPLORER"
    ? "Stored Cost Explorer billing data (PostgreSQL aws_billing)"
    : item.dataSource === "CUR_2_0" ? "CUR 2.0" : item.dataSource;
  const costShare = item.reason.match(/cost share=([\d.]+%)/i)?.[1] ?? "N/A";
  const actualCost = item.reason.match(/actual cost=([^;]+)/i)?.[1] ?? money(item.currentCost);
  const totalCost = item.reason.match(/total cost=([^;]+)/i)?.[1] ?? null;
  const evidenceValue = (dimension: string) =>
    item.reason.match(new RegExp(`${dimension}=([^;]+)`, "i"))?.[1]?.trim() ?? "N/A";
  const resourceId = item.resourceId?.trim() || null;
  const resourceHref = resourceId
    ? `/resources?resourceId=${encodeURIComponent(resourceId)}&service=${encodeURIComponent(item.service)}`
    : null;

  return <article className="rounded-lg border border-border bg-surface-muted p-4 transition-colors hover:border-primary/50">
    <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
      <div>
        <div className="flex flex-wrap items-center gap-2">
          <h3 className="font-medium">{item.problem}</h3>
          <Badge variant="outline">{item.priority}</Badge>
          <Badge variant={statusStyle[item.status]}>{item.status}</Badge>
        </div>
        <p className="mt-1 text-sm text-muted-foreground">{serviceName}</p>
        <div className="mt-2 flex flex-wrap gap-x-4 gap-y-1 text-sm text-muted-foreground">
          {item.resourceId && <span><span className="font-medium">Resource ID:</span> {item.resourceId}</span>}
          <span><span className="font-medium">Region:</span> {item.region}</span>
        </div>
      </div>
      <div className={`flex items-center gap-2 ${item.estimatedMonthlySaving == null ? "text-muted-foreground" : "text-success"}`}>
        {item.estimatedMonthlySaving != null && <TrendingDown className="size-4" />}
        <span className="text-sm font-medium">Potential Monthly Savings: {item.estimatedMonthlySaving == null ? "N/A / NO_DATA" : money(item.estimatedMonthlySaving)}</span>
      </div>
    </div>
    <div className="mt-4 grid gap-4 border-y border-border py-3 text-sm sm:grid-cols-2">
      <div>
        <p className="text-xs uppercase tracking-wide text-muted-foreground">Actual cost</p>
        <p className="mt-1 font-medium">{actualCost}</p>
        {totalCost && <><p className="mt-3 text-xs uppercase tracking-wide text-muted-foreground">Billing period total</p><p className="mt-1 text-muted-foreground">{totalCost}</p></>}
        <p className="mt-3 text-xs uppercase tracking-wide text-muted-foreground">Billing period</p>
        <p className="mt-1 text-muted-foreground">{item.billingMonth || "N/A"}</p>
        <p className="mt-3 text-xs uppercase tracking-wide text-muted-foreground">Cost share</p>
        <p className="mt-1 text-muted-foreground">{costShare}</p>
        {item.dataSource === "COST_EXPLORER" && <p className="mt-3 text-sm text-muted-foreground">
          Usage type: {evidenceValue("usage type")} · Product: {evidenceValue("product")}
        </p>}
        <p className="mt-3 text-xs uppercase tracking-wide text-muted-foreground">Data source</p>
        <p className="mt-1 text-muted-foreground">{dataSource}</p>
      </div>
      <div>
        <p className="text-xs uppercase tracking-wide text-muted-foreground">Why it was identified</p>
        <p className="mt-1 text-muted-foreground">{item.recommendation}</p>
        <p className="mt-3 text-xs uppercase tracking-wide text-muted-foreground">Suggested action</p>
        <p className="mt-1 text-muted-foreground">{item.recommendedAction}</p>
      </div>
    </div>
    {resourceUnavailable && <p role="status" className="mt-3 text-sm text-muted-foreground">Resource details unavailable — no Resource ID is present in the stored billing data.</p>}
    <div className="mt-4 flex flex-col gap-2 sm:flex-row sm:justify-end">
      {resourceHref
        ? <Button asChild size="sm" variant="outline"><Link href={resourceHref}>View Resource</Link></Button>
        : <Button onClick={() => setResourceUnavailable(true)} size="sm" variant="outline">View Resource</Button>}
      {item.status === "New" && <>
        <Button onClick={() => onStatus("Applied")} size="sm">Apply</Button>
        <Button onClick={() => onStatus("Dismissed")} size="sm" variant="ghost">Dismiss</Button>
      </>}
    </div>
  </article>;
}
