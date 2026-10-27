"use client";

import Link from "next/link";
import { AlertTriangle, Calculator, CheckCircle2, Info, Lightbulb, LoaderCircle, Search, Server, ShieldCheck } from "lucide-react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { fetchAwsRecommendations, generateAwsRecommendations, simulateAwsCost, updateAwsRecommendationStatus, type AwsCostSimulationResponse, type AwsRecommendation, type AwsRecommendationResponse } from "@/services/aws-connection";
import { useCloudAccountStore } from "@/store/cloud-account-store";
import { useMonthSelectionStore } from "@/store/month-selection-store";
import { MonthSelector } from "@/components/month-selector";
// import { syncAwsBilling } from "@/services/aws-bill";

// Placeholder the backend stores when billing/source data has no resource identifier.
const NO_RESOURCE_ID = "N/A";
// Savings may only ever be shown as an explicitly labelled heuristic estimate.
const HEURISTIC_SAVINGS_LABEL = "Heuristic Estimate — Not Guaranteed";
const INSUFFICIENT_DATA = "Insufficient Data";

const CATEGORY_LABELS: Record<string, string> = {
  "Trend": "Cost increase trends",
  "High Cost Service Detection": "Cost concentration",
  "Cost concentration": "Cost concentration",
  "Unused Resource Review": "Unused & idle resources",
  "Cost": "Cost review",
  "OPTIMIZATION_OPPORTUNITY": "Optimization opportunity",
};

const PRIORITY_ORDER = ["High", "Medium", "Low"] as const;
const STATUS_ORDER = ["New", "Applied", "Dismissed"] as const;

const PRIORITY_STYLES: Record<string, string> = {
  High: "border-destructive/30 bg-destructive/10 text-destructive",
  Medium: "border-warning/30 bg-warning/10 text-warning",
  Low: "border-success/30 bg-success/10 text-success",
};

const STATUS_STYLES: Record<string, string> = {
  New: "border-primary/30 bg-primary/10 text-primary",
  Applied: "border-success/30 bg-success/10 text-success",
  Dismissed: "border-border bg-muted text-muted-foreground",
};

type SimulationMode = "findings" | "whatif";

const SIMULATION_MODES: { value: SimulationMode; label: string }[] = [
  { value: "findings", label: "Optimize findings" },
  { value: "whatif", label: "What-if reduction" },
];

const ESTIMATE_BASIS_LABELS: Record<string, string> = {
  STORED_ESTIMATE: "Stored estimate",
  DERIVED_RESOURCE_COST: "Derived from billed resource cost",
  DERIVED_SERVICE_RATE: "Derived: 10% of service spend",
  WHAT_IF: "What-if assumption",
};

function estimateBasisLabel(basis: string | null | undefined) {
  if (!basis) return null;
  return ESTIMATE_BASIS_LABELS[basis] ?? basis;
}

function groupByCategory(items: AwsRecommendation[]) {
  const groups = new Map<string, AwsRecommendation[]>();
  items.forEach((item) => {
    const key = categoryLabel(item.category);
    groups.set(key, [...(groups.get(key) ?? []), item]);
  });
  return Array.from(groups.entries());
}

function realResourceId(value: string | null | undefined) {
  const trimmed = value?.trim();
  return trimmed && trimmed !== NO_RESOURCE_ID ? trimmed : null;
}

function normalizePriority(value: string) {
  const normalized = value?.toLowerCase();
  if (normalized === "high") return "High";
  if (normalized === "medium") return "Medium";
  if (normalized === "low") return "Low";
  return value;
}

function normalizeStatus(value: string) {
  const normalized = value?.toLowerCase();
  if (normalized === "new") return "New";
  if (normalized === "applied") return "Applied";
  if (normalized === "dismissed") return "Dismissed";
  return value;
}

function categoryLabel(category: string) {
  return CATEGORY_LABELS[category] ?? category;
}

function formatMoney(value: number | null | undefined, currency: string) {
  if (value == null || !Number.isFinite(value)) return null;
  const maximumFractionDigits = value !== 0 && Math.abs(value) < 1 ? 4 : 2;
  return new Intl.NumberFormat("en-US", { style: "currency", currency, minimumFractionDigits: 2, maximumFractionDigits }).format(value);
}

function heuristicSaving(item: AwsRecommendation) {
  const saving = item.estimatedMonthlySaving;
  return saving != null && Number.isFinite(saving) && saving > 0 ? saving : null;
}

function formatBillingMonth(value: string) {
  const [year, month] = value.split("-").map(Number);
  if (!year || !month) return value;
  return new Intl.DateTimeFormat("en-US", { month: "long", year: "numeric", timeZone: "UTC" })
    .format(new Date(Date.UTC(year, month - 1, 1)));
}

export default function RecommendationsPage({ compact = false, month }: { compact?: boolean; month?: string }) {
  const { account, connectionToken } = useCloudAccountStore();
  const selectedMonth = useMonthSelectionStore((state) => state.selectedMonth);
  const setSelectedMonth = useMonthSelectionStore((state) => state.setSelectedMonth);
  const effectiveMonth = month ?? selectedMonth;
  const [priority, setPriority] = useState("All");
  const [service, setService] = useState("All");
  const [region, setRegion] = useState("All");
  const [category, setCategory] = useState("All");
  const [status, setStatus] = useState("All");
  const [searchQuery, setSearchQuery] = useState("");
  const queryKey = ["aws-recommendations", account?.accountId, connectionToken, effectiveMonth];
  const query = useQuery({ queryKey, queryFn: () => fetchAwsRecommendations(connectionToken as string, effectiveMonth), enabled: Boolean(connectionToken), staleTime: 5 * 60 * 1000, retry: false });
  const mutation = useMutation({ mutationFn: ({ id, value }: { id: string; value: AwsRecommendation["status"] }) => updateAwsRecommendationStatus(connectionToken as string, id, value, effectiveMonth), onSuccess: (data) => queryClient.setQueryData(queryKey, data) });
  const generateMutation = useMutation({ mutationFn: () => generateAwsRecommendations(connectionToken as string, effectiveMonth), onSuccess: (data) => queryClient.setQueryData(queryKey, data) });
  // const syncMutation = useMutation({
  //   mutationFn: () => syncAwsBilling(connectionToken as string),
  //   onSuccess: async () => queryClient.invalidateQueries({ queryKey }),
  // });
  const queryClient = useQueryClient();

  if (!account || !connectionToken) return <Card><CardContent className="flex min-h-[280px] flex-col items-center justify-center text-center"><Lightbulb className="size-9 text-primary" /><p className="mt-4 font-medium">Connect an AWS account first</p><p className="mt-2 text-sm text-muted-foreground">Recommendations analyze stored billing data and use live resource state only for rules that require it.</p></CardContent></Card>;
  if (query.isLoading) return <Card><CardContent className="flex min-h-[220px] items-center justify-center text-sm text-muted-foreground"><LoaderCircle className="mr-2 size-4 animate-spin" />Analyzing AWS cost data...</CardContent></Card>;
  if (query.isError) return <Card><CardContent className="flex min-h-[220px] flex-col items-center justify-center text-center"><AlertTriangle className="size-8 text-destructive" /><p className="mt-3 font-medium">Unable to load recommendations</p><p role="alert" className="mt-2 text-sm text-destructive">{query.error instanceof Error ? query.error.message : "Recommendation data could not be loaded."}</p><Button className="mt-4" onClick={() => query.refetch()} variant="outline">Try again</Button></CardContent></Card>;

  const currency = query.data?.currency ?? "USD";
  const all = query.data?.recommendations ?? [];
  const filtered = all.filter((item) => {
    const matchesPriority = priority === "All" || normalizePriority(item.priority) === priority;
    const matchesService = service === "All" || item.service === service;
    const matchesRegion = region === "All" || item.region === region;
    const matchesCategory = category === "All" || item.category === category;
    const matchesStatus = status === "All" || normalizeStatus(item.status) === status;
    const term = searchQuery.trim().toLowerCase();
    const matchesSearch = term === "" ||
      item.problem.toLowerCase().includes(term) ||
      item.service.toLowerCase().includes(term) ||
      item.region?.toLowerCase().includes(term);
    return matchesPriority && matchesService && matchesRegion && matchesCategory && matchesStatus && matchesSearch;
  });
  const visible = compact ? filtered.slice(0, 4) : filtered;

  return <div className={compact ? "space-y-4" : "mx-auto max-w-7xl space-y-8"}>
    {!compact && <header className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between"><div><p className="text-sm font-medium text-primary">Optimization</p><h1 className="mt-1 text-2xl font-semibold tracking-tight sm:text-3xl">AWS Optimization Recommendations</h1><p className="mt-2 text-sm text-muted-foreground">Evidence-based findings generated from stored CUR 2.0 / Cost Explorer billing and verified against live AWS resource state.</p></div><div className="flex flex-wrap items-center justify-end gap-3 w-full sm:w-auto"><MonthSelector value={selectedMonth} onChange={setSelectedMonth} /><Button onClick={() => generateMutation.mutate()} disabled={generateMutation.isPending}>{generateMutation.isPending ? "Checking AWS evidence..." : "Generate recommendations"}</Button></div></header>}

    {!compact && <PortfolioSummary currency={currency} items={all} totalSpend={query.data?.totalSpend ?? null} />}

    <Card>
      <CardHeader className="flex flex-row items-center justify-between"><CardTitle className="flex items-center gap-2"><Lightbulb className="size-5 text-warning" />{compact ? "Recommendations" : "AWS cost recommendations"}</CardTitle><Badge variant="secondary">{all.length} {all.length === 1 ? "finding" : "findings"}</Badge></CardHeader>
      <CardContent>
        {!compact && <Toolbar
          category={category}
          onCategory={setCategory}
          onPriority={setPriority}
          onRegion={setRegion}
          onSearch={setSearchQuery}
          onService={setService}
          onStatus={setStatus}
          options={{ categories: distinct(all.map((item) => item.category)), services: distinct(all.map((item) => item.service)), regions: distinct(all.map((item) => item.region)) }}
          priority={priority}
          region={region}
          search={searchQuery}
          service={service}
          status={status}
        />}
        {generateMutation.isPending && <p aria-live="polite" className="py-4 text-center text-sm text-muted-foreground"><LoaderCircle className="mr-2 inline size-4 animate-spin" />Analyzing stored billing evidence for {effectiveMonth}...</p>}
        {generateMutation.isError && <p role="alert" className="mb-4 rounded-lg border border-destructive/30 bg-destructive/5 p-3 text-sm text-destructive">{generateMutation.error instanceof Error ? generateMutation.error.message : "Recommendation generation failed. Try again."}</p>}
        {mutation.isError && <p role="alert" className="mb-4 rounded-lg border border-destructive/30 bg-destructive/5 p-3 text-sm text-destructive">{mutation.error instanceof Error ? mutation.error.message : "Recommendation status could not be updated."}</p>}
        {query.data?.resourceDataStatus === "PERMISSION_DENIED" && <p role="alert" className="mb-4 rounded-lg border border-destructive/30 bg-destructive/5 p-3 text-sm text-destructive">{query.data.message ?? "AWS denied EC2/EBS resource discovery. Verify ec2:DescribeInstances and ec2:DescribeVolumes permissions."}</p>}
        {query.data?.resourceDataStatus === "UNAVAILABLE" && <p role="alert" className="mb-4 rounded-lg border border-warning/30 bg-warning/5 p-3 text-sm text-warning">{query.data.message ?? "Resource data unavailable. Reconnect AWS and generate recommendations again."}</p>}
        {all.length > 0
          ? visible.length === 0
            ? <div className="py-12 text-center"><CheckCircle2 className="mx-auto size-8 text-muted-foreground" /><p className="mt-3 text-sm font-medium">No recommendations match these filters</p><p className="mt-1 text-sm text-muted-foreground">Clear a filter or search term to see more findings.</p></div>
            : <div className="space-y-4">{visible.map((item) => <RecommendationCard currency={currency} item={item} key={item.id} onStatus={(value) => mutation.mutate({ id: item.id, value })} />)}</div>
          : <EmptyState data={query.data} month={effectiveMonth} />}
        {!compact && all.length > 0 && <CoverageNote items={all} />}
        {compact && all.length > visible.length && <Button asChild className="mt-5 w-full" variant="outline"><Link href="/recommendations">View all recommendations</Link></Button>}
      </CardContent>
    </Card>

    {!compact && <CostSimulator key={`${account.accountId}-${effectiveMonth}`} accountId={account.accountId} connectionToken={connectionToken} month={effectiveMonth} recommendations={all} currentMonthlyCost={query.data?.totalSpend ?? null} currency={currency} />}
  </div>;
}

function distinct(values: string[]) {
  return [...new Set(values.filter((value) => value && value.trim()))].sort();
}

function Toolbar({ search, service, region, priority, category, status, options, onSearch, onService, onRegion, onPriority, onCategory, onStatus }: {
  search: string;
  service: string;
  region: string;
  priority: string;
  category: string;
  status: string;
  options: { categories: string[]; services: string[]; regions: string[] };
  onSearch: (value: string) => void;
  onService: (value: string) => void;
  onRegion: (value: string) => void;
  onPriority: (value: string) => void;
  onCategory: (value: string) => void;
  onStatus: (value: string) => void;
}) {
  return <div className="mb-5 flex flex-col gap-3 rounded-lg border border-border bg-surface-muted p-3 lg:flex-row lg:flex-wrap lg:items-center">
    <div className="relative min-w-[200px] flex-1"><Search className="absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" /><input aria-label="Search recommendations" className="w-full rounded-md border border-input bg-background py-2 pl-9 pr-3 text-sm ring-offset-background placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2" onChange={(event) => onSearch(event.target.value)} placeholder="Search by problem, service, or region..." value={search} /></div>
    <Filter label="Category" onChange={onCategory} options={["All", ...options.categories]} value={category} />
    <Filter label="Priority" onChange={onPriority} options={["All", ...PRIORITY_ORDER]} value={priority} />
    <Filter label="Service" onChange={onService} options={["All", ...options.services]} value={service} />
    <Filter label="Region" onChange={onRegion} options={["All", ...options.regions]} value={region} />
    <Filter label="Status" onChange={onStatus} options={["All", ...STATUS_ORDER]} value={status} />
  </div>;
}

function PortfolioSummary({ items, currency, totalSpend }: { items: AwsRecommendation[]; currency: string; totalSpend: number | null }) {
  const categories = distinct(items.map((item) => item.category)).map((key) => {
    const grouped = items.filter((item) => item.category === key);
    return { key, count: grouped.length, savings: grouped.reduce((sum, item) => sum + (heuristicSaving(item) ?? 0), 0), titles: grouped.map((item) => item.problem) };
  }).sort((first, second) => second.count - first.count);
  const priorities = PRIORITY_ORDER.map((value) => ({ value, count: items.filter((item) => normalizePriority(item.priority) === value).length })).filter((entry) => entry.count > 0);
  const statuses = STATUS_ORDER.map((value) => ({ value, count: items.filter((item) => normalizeStatus(item.status) === value).length })).filter((entry) => entry.count > 0);

  return <section aria-label="Recommendation summary" className="grid gap-4 lg:grid-cols-2">
    <Card><CardHeader className="pb-3"><CardTitle className="text-base">Portfolio</CardTitle></CardHeader><CardContent className="space-y-4">
      <div className="grid grid-cols-2 gap-3">
        <SummaryTile label="Findings" value={String(items.length)} />
        <SummaryTile label="Period spend" value={formatMoney(totalSpend, currency) ?? INSUFFICIENT_DATA} />
      </div>
      <div>
        <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">Priority levels</p>
        <div className="mt-2 flex flex-wrap gap-2">{priorities.map((entry) => <span className={`rounded-full border px-2.5 py-1 text-xs font-medium ${PRIORITY_STYLES[entry.value]}`} key={entry.value}>{entry.value} · {entry.count}</span>)}</div>
      </div>
      <div>
        <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">Status tracking</p>
        <div className="mt-2 flex flex-wrap gap-2">{statuses.map((entry) => <span className={`rounded-full border px-2.5 py-1 text-xs font-medium ${STATUS_STYLES[entry.value]}`} key={entry.value}>{entry.value}: {entry.count}</span>)}</div>
      </div>
    </CardContent></Card>

    <Card><CardHeader className="pb-3"><CardTitle className="text-base">Findings by category</CardTitle></CardHeader><CardContent className="space-y-4">
      {categories.length === 0
        ? <p className="text-sm text-muted-foreground">No findings are grouped by category for this period.</p>
        : categories.map((entry, idx) => {
            const catColor = ["bg-blue-500", "bg-emerald-500", "bg-amber-500", "bg-purple-500", "bg-pink-500"][idx % 5];
            return <div className="space-y-2 rounded-lg border p-2" key={entry.key}>
              <div className="flex items-center justify-between gap-3 text-sm">
                <span className="flex items-center gap-2 min-w-0">
                  <span className={`h-2 w-2 rounded-full ${catColor}`} />
                  <span className="truncate font-medium" title={entry.key}>{categoryLabel(entry.key)}</span>
                </span>
                <span className="shrink-0 text-muted-foreground">{entry.count} {entry.count === 1 ? "finding" : "findings"}{entry.savings > 0 ? ` · ${formatMoney(entry.savings, currency)}/mo` : ""}</span>
              </div>
              <div className="h-2 w-full rounded bg-muted">
                <div className={`h-2 rounded ${catColor}`} style={{ width: `${Math.min((entry.count / Math.max(1, items.length)) * 100, 100)}%` }} />
              </div>
            </div>;
          })}
    </CardContent></Card>
  </section>;
}

function SummaryTile({ label, value }: { label: string; value: string }) {
  return <div className="rounded-lg border border-border bg-surface-muted p-3"><p className="text-[11px] font-medium uppercase tracking-wide text-muted-foreground">{label}</p><p className="mt-1 truncate text-base font-semibold" title={value}>{value}</p></div>;
}

function EmptyState({ data, month }: { data: AwsRecommendationResponse | undefined; month: string }) {
  const status = data?.status;
  if (status === "NO_DATA") return <div className="py-12 text-center"><CheckCircle2 className="mx-auto size-8 text-muted-foreground" /><p className="mt-3 text-sm font-medium">{data?.message ?? "No billing data available for this period."}</p><p className="mt-1 text-sm text-muted-foreground">Selected period: {month}</p></div>;
  if (status === "PERMISSION_DENIED") return <div className="py-12 text-center"><AlertTriangle className="mx-auto size-8 text-destructive" /><p className="mt-3 text-sm font-medium">AWS read permission denied</p><p className="mt-1 text-sm text-muted-foreground">{data?.message}</p></div>;
  if (status === "RESOURCE_UNAVAILABLE" || data?.resourceDataStatus === "UNAVAILABLE") return <div className="py-12 text-center"><AlertTriangle className="mx-auto size-8 text-warning" /><p className="mt-3 text-sm font-medium">Resource data unavailable</p><p className="mt-1 text-sm text-muted-foreground">Reconnect AWS and generate recommendations again.</p></div>;
  if (status === "NO_RESOURCES") return <div className="py-12 text-center"><AlertTriangle className="mx-auto size-8 text-muted-foreground" /><p className="mt-3 text-sm font-medium">No EC2 or EBS resources found</p><p className="mt-1 text-sm text-muted-foreground">No instances or volumes were found in any enabled AWS region. Verify the connected account and EC2 read access.</p></div>;
  if (status === "NO_RESOURCE_BILLING_MATCH" || status === "INSUFFICIENT_BILLING_EVIDENCE") return <div className="py-12 text-center"><AlertTriangle className="mx-auto size-8 text-warning" /><p className="mt-3 text-sm font-medium">{status === "NO_RESOURCE_BILLING_MATCH" ? "CUR billing did not match discovered resources" : "Stored billing evidence is insufficient for a supported recommendation"}</p>{data?.message && <p className="mt-1 text-sm text-muted-foreground">{data.message}</p>}{data && data.evaluationReasons.length > 0 && <ul className="mx-auto mt-3 max-w-xl list-disc space-y-1 pl-5 text-left text-sm text-muted-foreground">{data.evaluationReasons.map((reason) => <li key={reason}>{reason}</li>)}</ul>}</div>;
  if (data?.analysisComplete && status === "NO_RECOMMENDATIONS") return <div className="py-12 text-center"><CheckCircle2 className="mx-auto size-8 text-success" /><p className="mt-3 text-sm font-medium">No optimization opportunities found for this period.</p>{data.evaluationReasons.length > 0 && <ul className="mx-auto mt-3 max-w-xl list-disc space-y-1 pl-5 text-left text-sm text-muted-foreground">{data.evaluationReasons.map((reason) => <li key={reason}>{reason}</li>)}</ul>}</div>;
  if (status === "NO_RECOMMENDATIONS") return <div className="py-12 text-center"><Lightbulb className="mx-auto size-8 text-muted-foreground" /><p className="mt-3 text-sm font-medium">Recommendations have not been generated for this period yet.</p><p className="mt-1 text-sm text-muted-foreground">Select Generate recommendations to evaluate current AWS billing and resource evidence.</p></div>;
  return <div className="py-12 text-center"><AlertTriangle className="mx-auto size-8 text-warning" /><p className="mt-3 text-sm font-medium">No findings were returned</p><p className="mt-1 text-sm text-muted-foreground">The recommendation response was empty or incomplete. Generate recommendations again.</p></div>;
}

function CoverageNote({ items }: { items: AwsRecommendation[] }) {
  const verified = items.filter((item) => item.resourceState != null).length;
  const concentration = items.filter((item) => item.costSharePercentage != null).length;
  const estimates = items.filter((item) => heuristicSaving(item) != null).length;
  return <div className="mt-6 flex flex-col gap-2 rounded-lg border border-border bg-surface-muted p-4 text-xs text-muted-foreground sm:flex-row sm:items-start sm:gap-3">
    <Info className="mt-0.5 size-4 shrink-0 text-primary" />
    <div className="space-y-1">
      <p className="font-medium text-foreground">Evidence coverage for this period</p>
      <p>{verified} verified against live AWS resource state · {concentration} backed by cost-concentration billing evidence · {estimates} with a heuristic savings estimate.</p>
      <p>Underutilized-resource and rightsizing analyses require CloudWatch metrics and the AWS Pricing API, which are not connected, so those categories report <span className="font-medium text-foreground">{INSUFFICIENT_DATA}</span> rather than a guessed value.</p>
    </div>
  </div>;
}

function RecommendationCard({ item, currency, onStatus }: { item: AwsRecommendation; currency: string; onStatus: (value: AwsRecommendation["status"]) => void }) {
  const resourceId = realResourceId(item.resourceId);
  const resourceHref = resourceId ? `/resources?resource=${encodeURIComponent(resourceId)}` : null;
  const priority = normalizePriority(item.priority);
  const status = normalizeStatus(item.status);
  const saving = formatMoney(heuristicSaving(item), currency);
  const actualCost = formatMoney(item.currentCost, currency);
  const periodTotal = formatMoney(item.periodTotalCost, currency);
  const serviceName = item.service?.toLowerCase().includes("elastic block store") ? "Amazon EBS" : item.service;
  const dataSource = item.dataSource === "COST_EXPLORER"
    ? "Stored Cost Explorer billing (PostgreSQL aws_billing)"
    : item.dataSource === "CUR_2_0" ? "CUR 2.0 billing" : item.dataSource;

  return <article className="rounded-xl border border-border bg-card p-5 shadow-[var(--shadow-card)]">
    <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
      <div className="min-w-0">
        <div className="flex flex-wrap items-center gap-2">
          <Badge className={PRIORITY_STYLES[priority] ?? ""} variant="outline">{priority} priority</Badge>
          <Badge className={STATUS_STYLES[status] ?? ""} variant="outline">{status}</Badge>
          <Badge variant="secondary">{categoryLabel(item.category)}</Badge>
        </div>
        <h3 className="mt-3 text-base font-semibold">{item.problem}</h3>
        <p className="mt-1 text-sm text-muted-foreground">{serviceName} · {item.region}</p>
      </div>
      <div className="shrink-0 rounded-lg border border-border bg-surface-muted px-3 py-2 text-right">
        <p className="text-[11px] font-medium uppercase tracking-wide text-muted-foreground">Estimated saving</p>
        {saving ? <p className="mt-0.5 text-sm font-semibold text-success">{saving}/mo</p> : <p className="mt-0.5 text-sm font-medium text-muted-foreground">{INSUFFICIENT_DATA}</p>}
        <p className="mt-0.5 text-[10px] text-muted-foreground">{HEURISTIC_SAVINGS_LABEL}</p>
      </div>
    </div>

    <div className="mt-4 grid gap-4 border-y border-border py-4 text-sm sm:grid-cols-2 lg:grid-cols-4">
      <EvidenceStat label="Actual cost" value={actualCost ?? INSUFFICIENT_DATA} />
      <EvidenceStat label="Cost share" value={item.costSharePercentage != null ? `${item.costSharePercentage.toFixed(2)}%${item.concentrationThreshold != null ? ` of ${item.concentrationThreshold.toFixed(0)}% threshold` : ""}` : INSUFFICIENT_DATA} />
      <EvidenceStat label="Period total" value={periodTotal ?? INSUFFICIENT_DATA} />
      <EvidenceStat label="Billing period" value={item.billingMonth ? formatBillingMonth(item.billingMonth) : INSUFFICIENT_DATA} />
      <EvidenceStat label="Resource" value={resourceId ?? "Service-level finding"} />
      <EvidenceStat label="Resource state" value={item.resourceState ? item.resourceState.charAt(0) + item.resourceState.slice(1).toLowerCase() : INSUFFICIENT_DATA} />
      <EvidenceStat label="Data source" value={dataSource} />
      <EvidenceStat label="Detected" value={item.createdAt ? new Date(item.createdAt).toLocaleDateString() : INSUFFICIENT_DATA} />
    </div>

    <div className="mt-4 grid gap-4 text-sm lg:grid-cols-2">
      <div><p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">Why this was identified</p><WhyIdentifiedDiagram currency={currency} item={item} /></div>
      <div><p className="flex items-center gap-1.5 text-xs font-medium uppercase tracking-wide text-muted-foreground"><ShieldCheck className="size-3.5" />Suggested action</p><p className="mt-1 leading-6 text-muted-foreground">{item.recommendedAction}</p></div>
    </div>

    <div className="mt-4 flex flex-col gap-2 sm:flex-row sm:flex-wrap sm:items-center">
      {resourceHref
        ? <Button asChild size="sm" variant="outline"><Link href={resourceHref}>View resource</Link></Button>
        : <Button disabled size="sm" variant="outline" title="Billing data for this finding has no resource identifier">View resource</Button>}
      <div className="flex flex-col gap-2 sm:ml-auto sm:flex-row">
        {status !== "Applied" && <Button onClick={() => onStatus("Applied")} size="sm">Mark applied</Button>}
        {status !== "Dismissed" && <Button onClick={() => onStatus("Dismissed")} size="sm" variant="outline">Dismiss</Button>}
        {status !== "New" && <Button onClick={() => onStatus("New")} size="sm" variant="ghost">Reopen</Button>}
      </div>
    </div>
  </article>;
}

function EvidenceStat({ label, value }: { label: string; value: string }) {
  return <div className="min-w-0"><p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">{label}</p><p className="mt-1 break-words font-medium" title={value}>{value}</p></div>;
}

function WhyIdentifiedDiagram({ item, currency }: { item: AwsRecommendation; currency: string }) {
  const costShare = item.costSharePercentage;
  const threshold = item.concentrationThreshold;
  const actual = item.currentCost;
  const total = item.periodTotalCost;
  const actualLabel = formatMoney(actual, currency);
  const totalLabel = formatMoney(total, currency);
  const resourceState = item.resourceState;

  if (costShare != null || (actual != null && total != null && total > 0)) {
    const sharePercent = costShare != null ? Math.min(Math.max(costShare, 0), 100) : null;
    const thresholdPercent = threshold != null ? Math.min(Math.max(threshold, 0), 100) : null;
    const servicePercent = actual != null && total != null && total > 0 ? Math.min(100, (actual / total) * 100) : null;
    const exceeds = sharePercent != null && thresholdPercent != null && sharePercent >= thresholdPercent;
    return <div className="mt-2 space-y-4 rounded-lg border border-border bg-surface-muted p-4">
      {sharePercent != null && <div>
        <div className="flex items-center justify-between text-xs text-muted-foreground"><span>Service cost share</span><span className={`font-semibold ${exceeds ? "text-warning" : "text-foreground"}`}>{costShare?.toFixed(2)}%</span></div>
        <div aria-label={thresholdPercent == null ? `Service cost share ${costShare?.toFixed(2)} percent` : `Service cost share ${costShare?.toFixed(2)} percent against a ${thresholdPercent.toFixed(0)} percent concentration threshold`} className="relative mt-2 h-3 rounded-full bg-muted" role="img">
          <div className={`h-full rounded-full ${exceeds ? "bg-warning" : "bg-primary"}`} style={{ width: `${sharePercent}%` }} />
          {thresholdPercent != null && <span className="absolute -top-1 h-5 border-l-2 border-dashed border-foreground/60" style={{ left: `${thresholdPercent}%` }} />}
        </div>
        <div className="mt-1 flex items-center justify-between text-[10px] text-muted-foreground"><span>0%</span>{thresholdPercent != null && <span>threshold {thresholdPercent.toFixed(0)}%</span>}<span>100%</span></div>
      </div>}
      {servicePercent != null && <div>
        <div className="flex items-center justify-between text-xs text-muted-foreground"><span>This service vs period total</span><span className="font-semibold text-foreground">{actualLabel} / {totalLabel}</span></div>
        <div aria-label={`Service spend ${actualLabel} of ${totalLabel} period total`} className="mt-2 flex h-3 overflow-hidden rounded-full bg-muted" role="img">
          <div className="h-full bg-primary" style={{ width: `${servicePercent}%` }} />
        </div>
      </div>}
    </div>;
  }

  if (resourceState) {
    return <div className="mt-2 flex items-center gap-4 rounded-lg border border-warning/30 bg-warning/5 p-4">
      <div className="flex size-12 shrink-0 items-center justify-center rounded-full border-2 border-warning/40 bg-warning/10"><Server className="size-6 text-warning" /></div>
      <div>
        <p className="text-xs text-muted-foreground">Resource state</p>
        <p className="text-sm font-semibold text-warning">{resourceState.charAt(0) + resourceState.slice(1).toLowerCase()}</p>
        {actualLabel && <p className="text-xs text-muted-foreground">Current spend {actualLabel}</p>}
      </div>
    </div>;
  }

  return <div className="mt-2 flex items-center gap-4 rounded-lg border border-warning/30 bg-warning/5 p-4">
    <svg aria-label="Rising cost trend detected" className="size-12 shrink-0 text-warning" fill="none" role="img" viewBox="0 0 48 48">
      <path d="M9 37 L21 24 L30 31 L40 13" stroke="currentColor" strokeLinecap="round" strokeLinejoin="round" strokeWidth="3" />
      <path d="M32 13 L40 13 L40 21" stroke="currentColor" strokeLinecap="round" strokeLinejoin="round" strokeWidth="3" />
    </svg>
    <div>
      <p className="text-xs text-muted-foreground">Detected change</p>
      <p className="text-sm font-semibold text-warning">Rising spend trend</p>
      {actualLabel && <p className="text-xs text-muted-foreground">Current spend {actualLabel}</p>}
    </div>
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
  const [mode, setMode] = useState<SimulationMode>("findings");
  const [selectedIds, setSelectedIds] = useState<string[]>([]);
  const [whatIfPercent, setWhatIfPercent] = useState(10);
  const simulation = useMutation({
    mutationFn: () => simulateAwsCost(connectionToken, month, mode === "findings" ? selectedIds : [], mode === "whatif" ? whatIfPercent : null),
    retry: false,
  });
  const selectedRecommendations = recommendations.filter((recommendation) => selectedIds.includes(recommendation.id));
  const selectedKnownSavings = selectedRecommendations.reduce((total, recommendation) => total + (heuristicSaving(recommendation) ?? 0), 0);
  const hasKnownSavings = selectedKnownSavings > 0;
  const hasUnpricedSelection = selectedRecommendations.some((recommendation) => heuristicSaving(recommendation) == null);
  const projectedFromKnown = currentMonthlyCost == null ? null : Math.max(0, currentMonthlyCost - selectedKnownSavings);
  const whatIfSaving = currentMonthlyCost == null ? null : (currentMonthlyCost * whatIfPercent) / 100;
  const whatIfProjected = whatIfSaving == null ? null : Math.max(0, (currentMonthlyCost ?? 0) - whatIfSaving);
  const canSimulate = mode === "whatif" ? currentMonthlyCost != null && whatIfPercent > 0 : selectedRecommendations.length > 0;
  const toggle = (id: string, checked: boolean) => {
    setSelectedIds((current) => checked ? [...current, id] : current.filter((selected) => selected !== id));
    simulation.reset();
  };
  const switchMode = (value: SimulationMode) => {
    setMode(value);
    simulation.reset();
  };
  const previewMaxCost = Math.max(currentMonthlyCost ?? 0, whatIfProjected ?? 0, projectedFromKnown ?? 0, 0.01);

  return <section aria-labelledby="cost-simulator-title" className="space-y-5 rounded-xl border border-border bg-card p-5 shadow-[var(--shadow-card)]">
    <header className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
      <div>
        <div className="flex items-center gap-2 text-sm font-medium text-primary"><Calculator className="size-4" />Optimization</div>
        <h2 className="mt-1 text-xl font-semibold" id="cost-simulator-title">Cost Simulator</h2>
        <p className="mt-1 max-w-2xl text-sm text-muted-foreground">Project a future bill from stored billing data. Optimize selected findings or test a what-if reduction. No AWS resources are changed and missing pricing is never guessed.</p>
      </div>
      <p className="shrink-0 text-xs text-muted-foreground">Account {accountId} · {month}</p>
    </header>

    <div aria-label="Simulation mode" className="inline-flex rounded-lg border border-border bg-surface-muted p-1" role="tablist">
      {SIMULATION_MODES.map((option) => <button aria-selected={mode === option.value} className={`rounded-md px-3 py-1.5 text-sm font-medium transition-colors ${mode === option.value ? "bg-background text-foreground shadow-sm" : "text-muted-foreground hover:text-foreground"}`} key={option.value} onClick={() => switchMode(option.value)} role="tab" type="button">{option.label}</button>)}
    </div>

    {mode === "whatif" ? <div className="space-y-4 rounded-lg border border-border p-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <label className="text-sm font-medium" htmlFor="whatif-slider">Target reduction</label>
        <span className="text-sm font-semibold text-primary">{whatIfPercent}%</span>
      </div>
      <input aria-label="What-if reduction percentage" className="w-full accent-primary" id="whatif-slider" max={100} min={1} onChange={(event) => { setWhatIfPercent(Number(event.target.value)); simulation.reset(); }} type="range" value={whatIfPercent} />
      <div className="grid gap-3 sm:grid-cols-3">
        <SimulationMetric label="Current monthly cost" value={formatMoney(currentMonthlyCost, currency) ?? INSUFFICIENT_DATA} />
        <SimulationMetric label="Assumed reduction" value={formatMoney(whatIfSaving, currency) ?? INSUFFICIENT_DATA} />
        <SimulationMetric label="Projected monthly cost" value={formatMoney(whatIfProjected, currency) ?? INSUFFICIENT_DATA} />
      </div>
      <p className="text-xs text-muted-foreground">This reduction is a planning assumption you choose, not a detected waste estimate.</p>
    </div> : <div className="space-y-3">
      {recommendations.length === 0
        ? <div className="rounded-lg border border-dashed border-border bg-surface-muted p-4 text-sm text-muted-foreground">No recommendations are available for this account and month.</div>
        : groupByCategory(recommendations).map(([label, items]) => <div className="space-y-2" key={label}>
            <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">{label}</p>
            {items.map((recommendation) => {
              const status = normalizeStatus(recommendation.status);
              const saving = heuristicSaving(recommendation);
              const actionable = status === "New";
              const overlapsSelection = actionable && !selectedIds.includes(recommendation.id)
                && selectedRecommendations.some((selected) => scenariosOverlap(selected, recommendation));
              const canSelect = actionable && !overlapsSelection;
              const unavailableReason = status === "Applied" ? "Already applied" : status === "Dismissed" ? "Dismissed" : overlapsSelection ? "Overlaps a selected scenario" : "Not actionable";
              return <label aria-disabled={!canSelect} className={`flex items-start gap-3 rounded-lg border border-border p-3 ${canSelect ? "cursor-pointer bg-background hover:border-primary/50" : "cursor-not-allowed bg-muted/40 opacity-65"}`} key={recommendation.id}>
                <input aria-label={`Select ${recommendation.problem}`} checked={selectedIds.includes(recommendation.id)} className="mt-1 accent-primary" disabled={!canSelect || simulation.isPending} onChange={(event) => toggle(recommendation.id, event.target.checked)} type="checkbox" />
                <span className="min-w-0 flex-1">
                  <span className="flex flex-wrap items-center justify-between gap-2"><span className="font-medium">{recommendation.problem}</span><span className={canSelect && saving != null ? "text-sm font-medium text-success" : "text-sm text-muted-foreground"}>{saving != null ? `-${formatMoney(saving, currency)} /mo` : canSelect ? "Estimate on simulate" : unavailableReason}</span></span>
                  <span className="mt-1 block text-sm text-muted-foreground">{recommendation.service} · {realResourceId(recommendation.resourceId) ?? "Service total"}</span>
                </span>
              </label>;
            })}
          </div>)}
      {selectedRecommendations.length > 0 && <div className="grid gap-3 sm:grid-cols-3">
        <SimulationMetric label="Current monthly cost" value={formatMoney(currentMonthlyCost, currency) ?? INSUFFICIENT_DATA} />
        <SimulationMetric label="Known savings" value={hasKnownSavings ? formatMoney(selectedKnownSavings, currency) ?? INSUFFICIENT_DATA : INSUFFICIENT_DATA} />
        <SimulationMetric label="Projected (known)" value={formatMoney(projectedFromKnown, currency) ?? INSUFFICIENT_DATA} />
      </div>}
      {hasUnpricedSelection && <p className="text-xs text-muted-foreground">Findings without a stored estimate are derived from billing data when you simulate.</p>}
    </div>}

    {simulation.isError && <p className="text-sm text-destructive">{simulation.error instanceof Error ? simulation.error.message : "Unable to simulate the selected optimizations."}</p>}
    <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
      <p className="text-xs text-muted-foreground">Estimates are labelled <span className="font-medium text-foreground">{HEURISTIC_SAVINGS_LABEL}</span>. Simulation does not modify AWS resources or billing data.</p>
      <Button disabled={!canSimulate || simulation.isPending} onClick={() => simulation.mutate()}>{simulation.isPending ? "Simulating..." : "Simulate"}</Button>
    </div>

    {simulation.data && <SimulationResult result={simulation.data} currency={currency} maxCost={previewMaxCost} />}
  </section>;
}

function SimulationResult({ result, currency, maxCost }: { result: AwsCostSimulationResponse; currency: string; maxCost: number }) {
  const money = (value: number | null) => formatMoney(value, currency) ?? INSUFFICIENT_DATA;
  if (result.status === "NO_BILLING_DATA") return <div className="rounded-lg border border-warning/30 bg-warning/5 p-4 text-sm text-warning">{result.message ?? "Billing data is unavailable for this month."}</div>;
  if (result.status === "INSUFFICIENT_PRICING_DATA") return <div className="space-y-3 rounded-lg border border-warning/30 bg-warning/5 p-4"><p className="text-sm font-medium text-warning">{result.message ?? "Pricing data is insufficient for a complete estimate."}</p><ScenarioBreakdown currency={currency} scenarios={result.scenarios} /></div>;
  if (result.status !== "SUCCESS") return <div className="rounded-lg border border-destructive/30 p-4 text-sm text-destructive">Simulation could not be completed.</div>;
  const savingsPercentage = result.currentMonthlyCost == null || result.currentMonthlyCost <= 0 || result.estimatedMonthlySaving == null
    ? null
    : Math.min(100, (result.estimatedMonthlySaving / result.currentMonthlyCost) * 100);

  return <div className="space-y-4 border-t border-border pt-5" aria-label="Simulation results">
    <div className="flex flex-wrap items-center justify-between gap-2">
      <h3 className="text-base font-semibold">Simulation result</h3>
      <Badge variant="info">{result.mode === "WHAT_IF" ? "What-if reduction" : "Selected findings"}</Badge>
    </div>
    <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-5">
      <SimulationMetric label="Current monthly cost" value={money(result.currentMonthlyCost)} />
      <SimulationMetric label="Estimated monthly savings" value={money(result.estimatedMonthlySaving)} />
      <SimulationMetric label="Projected monthly cost" value={money(result.estimatedMonthlyCost)} />
      <SimulationMetric label="Estimated savings percentage" value={savingsPercentage == null ? INSUFFICIENT_DATA : `${savingsPercentage.toFixed(1)}%`} />
      <SimulationMetric label="Estimated annual saving" value={money(result.estimatedAnnualSaving)} />
    </div>
    <div className="rounded-lg border border-border p-4">
      <h3 className="text-sm font-medium">Before vs After</h3>
      <div className="mt-4 space-y-4">
        <CostBar currency={currency} label="Current" maxCost={maxCost} value={result.currentMonthlyCost} />
        <CostBar currency={currency} label="Projected" maxCost={maxCost} value={result.estimatedMonthlyCost} />
      </div>
    </div>
    <ScenarioBreakdown currency={currency} scenarios={result.scenarios} />
    <p className="text-xs text-muted-foreground">All figures are heuristic estimates labelled {HEURISTIC_SAVINGS_LABEL}; they reuse stored billing data and never change AWS resources.</p>
  </div>;
}

function scenariosOverlap(first: AwsRecommendation, second: AwsRecommendation) {
  const firstId = realResourceId(first.resourceId);
  const secondId = realResourceId(second.resourceId);
  if (firstId && secondId) return firstId === secondId;
  return false;
}

function CostBar({ label, value, maxCost, currency }: { label: string; value: number | null; maxCost: number; currency: string }) {
  const percent = value == null ? 0 : Math.min(100, (value / maxCost) * 100);
  return <div className="space-y-2"><div className="flex items-center justify-between text-sm"><span className="text-muted-foreground">{label}</span><span className="font-medium">{formatMoney(value, currency) ?? INSUFFICIENT_DATA}</span></div><div className="h-2.5 rounded-full bg-muted"><div className="h-full rounded-full bg-primary transition-[width]" style={{ width: `${percent}%` }} /></div></div>;
}

function SimulationMetric({ label, value }: { label: string; value: string }) {
  return <div className="rounded-lg border border-border bg-surface-muted p-4"><p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">{label}</p><p className="mt-1.5 text-lg font-semibold">{value}</p></div>;
}

function ScenarioBreakdown({ scenarios, currency }: { scenarios: AwsCostSimulationResponse["scenarios"]; currency: string }) {
  if (scenarios.length === 0) return null;
  return <div className="space-y-2" aria-label="Scenario breakdown">
    {scenarios.map((scenario) => <div className="flex flex-wrap items-center justify-between gap-2 text-sm" key={scenario.recommendationId ?? scenario.title}>
      <span className="flex flex-wrap items-center gap-2">
        <span>{scenario.title}</span>
        {estimateBasisLabel(scenario.estimateBasis) && <Badge className="font-normal text-muted-foreground" variant="outline">{estimateBasisLabel(scenario.estimateBasis)}</Badge>}
      </span>
      <span className={scenario.status === "AVAILABLE" ? "font-medium text-success" : "text-muted-foreground"}>{scenario.status === "AVAILABLE" ? `-${formatMoney(scenario.estimatedMonthlySaving, currency)} /mo` : scenario.message ?? INSUFFICIENT_DATA}</span>
    </div>)}
  </div>;
}

function Filter({ label, value, options, onChange }: { label: string; value: string; options: string[]; onChange: (value: string) => void }) {
  return <label className="flex items-center gap-2 text-sm"><span className="text-xs font-medium text-muted-foreground">{label}</span><select className="h-9 rounded-md border border-input bg-background px-2.5 text-sm" onChange={(event) => onChange(event.target.value)} value={value}>{options.map((option) => <option key={option} value={option}>{option}</option>)}</select></label>;
}
