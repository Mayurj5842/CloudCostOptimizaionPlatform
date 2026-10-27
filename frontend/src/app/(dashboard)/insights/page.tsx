"use client";

import Link from "next/link";
import { AlertTriangle, Bot, CheckCircle2, CircleDollarSign, Lightbulb, LoaderCircle, Server, ShieldCheck } from "lucide-react";
import { useQuery } from "@tanstack/react-query";
import { useState } from "react";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { MonthSelector } from "@/components/month-selector";
import { fetchAwsInsights, type AwsInsight, type AwsRecommendation } from "@/services/aws-connection";
import { useCloudAccountStore } from "@/store/cloud-account-store";
import { useMonthSelectionStore } from "@/store/month-selection-store";

const severityStyles = {
  High: "border-destructive/30 bg-destructive/10 text-destructive",
  Medium: "border-warning/30 bg-warning/10 text-warning",
  Low: "border-success/10 text-success",
} as const;

const PRIORITY_STYLES: Record<string, string> = {
  High: "border-destructive/30 bg-destructive/10 text-destructive",
  Medium: "border-warning/30 bg-warning/10 text-warning",
  Low: "border-success/10 text-success",
};

const STATUS_STYLES: Record<string, string> = {
  New: "border-primary/30 bg-primary/10 text-primary",
  Applied: "border-success/30 bg-success/10 text-success",
  Dismissed: "border-border bg-muted text-muted-foreground",
};

const NO_RESOURCE_ID = "N/A";
const HEURISTIC_SAVINGS_LABEL = "Heuristic Estimate — Not Guaranteed";
const INSUFFICIENT_DATA = "Insufficient Data";

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

function formatMoney(value: number | null | undefined, currency: string) {
  if (value == null || !Number.isFinite(value)) return null;
  const maximumFractionDigits = value !== 0 && Math.abs(value) < 1 ? 4 : 2;
  return new Intl.NumberFormat("en-US", { style: "currency", currency, minimumFractionDigits: 2, maximumFractionDigits }).format(value);
}

export default function InsightsPage() {
  const { account, connectionToken } = useCloudAccountStore();
  const selectedMonth = useMonthSelectionStore((state) => state.selectedMonth);
  const setSelectedMonth = useMonthSelectionStore((state) => state.setSelectedMonth);
  const [severity, setSeverity] = useState("All");
  const [service, setService] = useState("All");
  const [region, setRegion] = useState("All");
  const insights = useQuery({
    queryKey: ["aws-insights", account?.accountId, connectionToken, selectedMonth],
    queryFn: () => fetchAwsInsights(connectionToken as string, selectedMonth),
    enabled: Boolean(connectionToken),
    staleTime: 5 * 60 * 1000,
    retry: false,
  });

  if (!account || !connectionToken) {
    return <Card><CardContent className="flex min-h-[360px] flex-col items-center justify-center px-6 py-12 text-center"><Bot className="size-10 text-primary" /><h1 className="mt-5 text-xl font-semibold">Connect an AWS account first</h1><p className="mt-2 max-w-md text-sm leading-6 text-muted-foreground">AI Insights uses persisted billing data and your resource inventory.</p></CardContent></Card>;
  }

  const data = insights.data;
  const filtered = data?.insights.filter((item) => (severity === "All" || item.severity === severity) && (service === "All" || item.service === service) && (region === "All" || item.region === region)) ?? [];
  const services = [...new Set(data?.insights.map((item) => item.service) ?? [])].sort();
  const regions = [...new Set(data?.insights.map((item) => item.region) ?? [])].sort();

  return <div className="mx-auto max-w-7xl space-y-8">
    <header className="flex flex-col gap-4 lg:flex-row lg:items-end lg:justify-between">
      <div><p className="text-sm font-medium text-primary">Optimization</p><h1 className="mt-1 text-2xl font-semibold tracking-tight sm:text-3xl">AI Insights</h1><p className="mt-2 text-sm text-muted-foreground">Actionable findings from persisted billing data and AWS resource inventory.</p></div>
      <MonthSelector value={selectedMonth} onChange={setSelectedMonth} />
      {data && <div className="flex items-center gap-2 text-xs text-muted-foreground"><span className="size-2 rounded-full bg-success" />{data.insights.length} findings generated</div>}
    </header>

    {insights.isLoading && <Card><CardContent className="flex min-h-[260px] items-center justify-center text-sm text-muted-foreground"><LoaderCircle className="mr-2 size-4 animate-spin" />Analyzing CUR and resource data...</CardContent></Card>}
    {insights.isError && <Card><CardContent className="flex min-h-[260px] flex-col items-center justify-center text-center"><AlertTriangle className="size-8 text-destructive" /><p className="mt-4 font-medium">Unable to generate insights</p><p className="mt-2 text-sm text-muted-foreground">{insights.error instanceof Error ? insights.error.message : "Insights could not be calculated from the available billing data."}</p><Button className="mt-5" onClick={() => insights.refetch()} variant="outline">Try again</Button></CardContent></Card>}
    {data?.status === "NO_CUR_DATA" && <Card><CardContent className="flex min-h-[260px] flex-col items-center justify-center text-center"><CheckCircle2 className="size-9 text-muted-foreground" /><p className="mt-4 font-medium">No billing data is available for this period.</p><p className="mt-2 text-sm text-muted-foreground">Import CUR data or fetch live AWS cost, then refresh Insights.</p></CardContent></Card>}
    {data && data.status !== "NO_CUR_DATA" && <>
      <section className="flex flex-col gap-3 rounded-xl border border-border bg-card p-4 shadow-[var(--shadow-card)] sm:flex-row sm:flex-wrap sm:items-center">
        <Filter label="Severity" value={severity} options={["All", "High", "Medium", "Low"]} onChange={setSeverity} />
        <Filter label="Service" value={service} options={["All", ...services]} onChange={setService} />
        <Filter label="Region" value={region} options={["All", ...regions]} onChange={setRegion} />
        <span className="text-xs text-muted-foreground sm:ml-auto">Showing {filtered.length} of {data.insights.length}</span>
      </section>
      {filtered.length === 0 ? <Card><CardContent className="flex min-h-[260px] flex-col items-center justify-center text-center"><CheckCircle2 className="size-9 text-success" /><p className="mt-4 font-medium">No findings match these filters</p><p className="mt-2 text-sm text-muted-foreground">Your current filters do not reveal any additional action items.</p></CardContent></Card> : <section className="grid gap-5 xl:grid-cols-2">{filtered.map((item) => <InsightCard currency={data.currency} insight={item} key={item.id} />)}</section>}
      {data.recommendations.length > 0 && <RecommendationList recommendations={data.recommendations} currency={data.currency} />}
    </>}
  </div>;
}

function Filter({ label, value, options, onChange }: { label: string; value: string; options: string[]; onChange: (value: string) => void }) {
  return <label className="flex items-center gap-2 text-sm"><span className="text-xs font-medium text-muted-foreground">{label}</span><select className="h-9 max-w-[180px] rounded-lg border border-input bg-background px-2.5 text-sm" onChange={(event) => onChange(event.target.value)} value={value}>{options.map((option) => <option key={option} value={option}>{option}</option>)}</select></label>;
}

function InsightCard({ insight, currency }: { insight: AwsInsight; currency: string }) {
  const format = (value: number | null) => value == null ? "Unavailable" : new Intl.NumberFormat("en-US", { style: "currency", currency, minimumFractionDigits: 2 }).format(value);
  return <Card className="overflow-hidden"><CardHeader className="gap-4 border-b border-border sm:flex-row sm:items-start sm:justify-between"><div className="flex items-start gap-3"><div className="flex size-9 shrink-0 items-center justify-center rounded-lg bg-primary/10 text-primary"><CircleDollarSign className="size-5" /></div><div><div className="flex flex-wrap items-center gap-2"><CardTitle className="text-base">{insight.category}</CardTitle><span className={`rounded-full border px-2 py-0.5 text-[11px] font-medium ${severityStyles[insight.severity]}`}>{insight.severity}</span></div><p className="mt-1 text-sm text-muted-foreground">{insight.service} / {insight.region}</p></div></div><div className="text-left sm:text-right"><p className="text-xs text-muted-foreground">Current cost</p><p className="font-semibold">{format(insight.currentCost)}</p></div></CardHeader><CardContent className="space-y-4 p-5"><div><p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">Problem detected</p><p className="mt-1 text-sm">{insight.problemDetected}</p><p className="mt-1 text-sm leading-6 text-muted-foreground">{insight.reason}</p></div><div className="grid gap-4 border-y border-border py-4 sm:grid-cols-2"><div><p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">Estimated impact / saving</p><p className="mt-1 text-sm font-medium">{insight.estimatedImpact > 0 ? format(insight.estimatedImpact) : "No material impact"}</p><p className="mt-1 text-[11px] text-muted-foreground">Heuristic Estimate — Not Guaranteed</p></div><div><p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">Resource</p><p className="mt-1 truncate text-sm" title={insight.resource}>{insight.resource}</p></div></div><div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between"><p className="flex gap-2 text-sm leading-6"><Server className="mt-1 size-4 shrink-0 text-primary" /><span><strong>Recommended:</strong> {insight.recommendedAction}</span></p><Button asChild className="shrink-0" size="sm" variant="outline"><Link href={insight.resourcePath}>View Resource</Link></Button></div></CardContent></Card>;
}

function RecommendationList({ recommendations, currency }: { recommendations: AwsRecommendation[]; currency: string }) {
  return <div className="space-y-4">
    <header className="flex items-center justify-between">
      <div className="flex items-center gap-2"><Lightbulb className="size-5 text-warning" /><h2 className="text-lg font-semibold">Stored recommendations</h2></div>
      <Badge variant="secondary">{recommendations.length} {recommendations.length === 1 ? "finding" : "findings"}</Badge>
    </header>
    <div className="space-y-4">{recommendations.slice(0, 10).map((item) => <InsightRecommendationCard currency={currency} item={item} key={item.id} />)}</div>
  </div>;
}

function InsightRecommendationCard({ item, currency }: { item: AwsRecommendation; currency: string }) {
  const resourceId = realResourceId(item.resourceId);
  const resourceHref = resourceId ? `/resources?resource=${encodeURIComponent(resourceId)}` : null;
  const priority = normalizePriority(item.priority);
  const status = normalizeStatus(item.status);
  const saving = formatMoney(item.estimatedMonthlySaving, currency);
  const actualCost = formatMoney(item.currentCost, currency);
  const periodTotal = formatMoney(item.periodTotalCost, currency);
  const serviceName = item.service?.toLowerCase().includes("elastic block store") ? "Amazon EBS" : item.service;

  return <article className="rounded-xl border border-border bg-card p-5 shadow-[var(--shadow-card)]">
    <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
      <div className="min-w-0">
        <div className="flex flex-wrap items-center gap-2">
          <Badge className={PRIORITY_STYLES[priority] ?? ""} variant="outline">{priority} priority</Badge>
          <Badge className={STATUS_STYLES[status] ?? ""} variant="outline">{status}</Badge>
          <Badge variant="secondary">{item.category}</Badge>
        </div>
        <h3 className="mt-3 text-base font-semibold">{item.problem}</h3>
        <p className="mt-1 text-sm text-muted-foreground">{serviceName} · {item.region}</p>
      </div>
      <div className="shrink-0 rounded-lg border border-border bg-surface-muted px-3 py-2 text-right">
        <p className="text-[11px] font-medium uppercase tracking-wide text-muted-foreground">Estimated saving</p>
        {saving ? <p className="mt-0.5 text-sm font-semibold text-success">{saving}/mo</p> : <p className="mt-0.5 text-sm font-medium text-muted-foreground">{INSUFFICIENT_DATA}</p>}
        <p className="mt-0.5 text-[10px] text-muted-foreground">{saving ? HEURISTIC_SAVINGS_LABEL : "No billing basis for a savings estimate"}</p>
      </div>
    </div>

    <div className="mt-4 grid gap-4 border-y border-border py-4 text-sm sm:grid-cols-2 lg:grid-cols-4">
      <EvidenceStat label="Actual cost" value={actualCost ?? INSUFFICIENT_DATA} />
      <EvidenceStat label="Cost share" value={item.costSharePercentage != null ? `${item.costSharePercentage.toFixed(2)}%${item.concentrationThreshold != null ? ` of ${item.concentrationThreshold.toFixed(0)}% threshold` : ""}` : INSUFFICIENT_DATA} />
      <EvidenceStat label="Period total" value={periodTotal ?? INSUFFICIENT_DATA} />
      <EvidenceStat label="Billing period" value={item.billingMonth ?? INSUFFICIENT_DATA} />
      <EvidenceStat label="Resource" value={resourceId ?? "Service-level finding"} />
      <EvidenceStat label="Resource state" value={item.resourceState ? item.resourceState.charAt(0) + item.resourceState.slice(1).toLowerCase() : INSUFFICIENT_DATA} />
      <EvidenceStat label="Data source" value={item.dataSource === "COST_EXPLORER" ? "Stored Cost Explorer billing" : item.dataSource === "CUR_2_0" ? "CUR 2.0 billing" : (item.dataSource ?? INSUFFICIENT_DATA)} />
      <EvidenceStat label="Detected" value={item.createdAt ? new Date(item.createdAt).toLocaleDateString() : INSUFFICIENT_DATA} />
    </div>

    <div className="mt-4 grid gap-4 text-sm lg:grid-cols-2">
      <div><p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">Why this was identified</p><WhyIdentifiedDiagram currency={currency} item={item} /></div>
      <div><p className="flex items-center gap-1.5 text-xs font-medium uppercase tracking-wide text-muted-foreground"><ShieldCheck className="size-3.5" />Suggested action</p><p className="mt-1 leading-6 text-muted-foreground">{item.recommendedAction ?? item.action ?? item.recommendation}</p></div>
    </div>

    <div className="mt-4 flex flex-col gap-2 sm:flex-row sm:flex-wrap sm:items-center">
      {resourceHref
        ? <Button asChild size="sm" variant="outline"><Link href={resourceHref}>View resource</Link></Button>
        : <Button disabled size="sm" variant="outline" title="Billing data for this finding has no resource identifier">View resource</Button>}
      <div className="flex flex-col gap-2 sm:ml-auto sm:flex-row">
        <Button asChild size="sm" variant="ghost"><Link href="/recommendations">View in Recommendations</Link></Button>
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
