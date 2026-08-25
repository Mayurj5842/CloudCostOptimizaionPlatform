"use client";

import Link from "next/link";
import { AlertTriangle, Bot, CheckCircle2, CircleDollarSign, LoaderCircle, Server } from "lucide-react";
import { useQuery } from "@tanstack/react-query";
import { useState } from "react";

import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { fetchAwsInsights, type AwsInsight } from "@/services/aws-connection";
import { useCloudAccountStore } from "@/store/cloud-account-store";

const severityStyles = {
  High: "border-destructive/30 bg-destructive/10 text-destructive",
  Medium: "border-warning/30 bg-warning/10 text-warning",
  Low: "border-success/30 bg-success/10 text-success",
} as const;

export default function InsightsPage() {
  const { account, connectionToken } = useCloudAccountStore();
  const [severity, setSeverity] = useState("All");
  const [service, setService] = useState("All");
  const [region, setRegion] = useState("All");
  const insights = useQuery({
    queryKey: ["aws-insights", account?.accountId, connectionToken],
    queryFn: () => fetchAwsInsights(connectionToken as string),
    enabled: Boolean(connectionToken),
    staleTime: 5 * 60 * 1000,
    retry: false,
  });

  if (!account || !connectionToken) {
    return <Card><CardContent className="flex min-h-[360px] flex-col items-center justify-center px-6 py-12 text-center"><Bot className="size-10 text-primary" /><h1 className="mt-5 text-xl font-semibold">Connect an AWS account first</h1><p className="mt-2 max-w-md text-sm leading-6 text-muted-foreground">AI Insights uses CUR 2.0 billing data and your resource inventory.</p></CardContent></Card>;
  }

  const data = insights.data;
  const filtered = data?.insights.filter((item) => (severity === "All" || item.severity === severity) && (service === "All" || item.service === service) && (region === "All" || item.region === region)) ?? [];
  const services = [...new Set(data?.insights.map((item) => item.service) ?? [])].sort();
  const regions = [...new Set(data?.insights.map((item) => item.region) ?? [])].sort();

  return <div className="mx-auto max-w-7xl space-y-8">
    <header className="flex flex-col gap-4 lg:flex-row lg:items-end lg:justify-between">
      <div><p className="text-sm font-medium text-primary">Optimization</p><h1 className="mt-1 text-2xl font-semibold tracking-tight sm:text-3xl">AI Insights</h1><p className="mt-2 text-sm text-muted-foreground">Actionable findings from your latest CUR 2.0 data and AWS resource inventory.</p></div>
      {data && <div className="flex items-center gap-2 text-xs text-muted-foreground"><span className="size-2 rounded-full bg-success" />{data.insights.length} findings generated</div>}
    </header>

    {insights.isLoading && <Card><CardContent className="flex min-h-[260px] items-center justify-center text-sm text-muted-foreground"><LoaderCircle className="mr-2 size-4 animate-spin" />Analyzing CUR and resource data...</CardContent></Card>}
    {insights.isError && <Card><CardContent className="flex min-h-[260px] flex-col items-center justify-center text-center"><AlertTriangle className="size-8 text-destructive" /><p className="mt-4 font-medium">Unable to generate insights</p><p className="mt-2 text-sm text-muted-foreground">Verify the account connection and that CUR data has been imported.</p><Button className="mt-5" onClick={() => insights.refetch()} variant="outline">Try again</Button></CardContent></Card>}
    {data && <>
      <section className="flex flex-col gap-3 rounded-xl border border-border bg-card p-4 shadow-[var(--shadow-card)] sm:flex-row sm:flex-wrap sm:items-center">
        <Filter label="Severity" value={severity} options={["All", "High", "Medium", "Low"]} onChange={setSeverity} />
        <Filter label="Service" value={service} options={["All", ...services]} onChange={setService} />
        <Filter label="Region" value={region} options={["All", ...regions]} onChange={setRegion} />
        <span className="text-xs text-muted-foreground sm:ml-auto">Showing {filtered.length} of {data.insights.length}</span>
      </section>
      {filtered.length === 0 ? <Card><CardContent className="flex min-h-[260px] flex-col items-center justify-center text-center"><CheckCircle2 className="size-9 text-success" /><p className="mt-4 font-medium">No findings match these filters</p><p className="mt-2 text-sm text-muted-foreground">Your current filters do not reveal any additional action items.</p></CardContent></Card> : <section className="grid gap-5 xl:grid-cols-2">{filtered.map((item) => <InsightCard currency={data.currency} insight={item} key={item.id} />)}</section>}
    </>}
  </div>;
}

function Filter({ label, value, options, onChange }: { label: string; value: string; options: string[]; onChange: (value: string) => void }) {
  return <label className="flex items-center gap-2 text-sm"><span className="text-xs font-medium text-muted-foreground">{label}</span><select className="h-9 max-w-[180px] rounded-lg border border-input bg-background px-2.5 text-sm" onChange={(event) => onChange(event.target.value)} value={value}>{options.map((option) => <option key={option} value={option}>{option}</option>)}</select></label>;
}

function InsightCard({ insight, currency }: { insight: AwsInsight; currency: string }) {
  const format = (value: number) => new Intl.NumberFormat("en-US", { style: "currency", currency, minimumFractionDigits: 2 }).format(value);
  return <Card className="overflow-hidden"><CardHeader className="gap-4 border-b border-border sm:flex-row sm:items-start sm:justify-between"><div className="flex items-start gap-3"><div className="flex size-9 shrink-0 items-center justify-center rounded-lg bg-primary/10 text-primary"><CircleDollarSign className="size-5" /></div><div><div className="flex flex-wrap items-center gap-2"><CardTitle className="text-base">{insight.category}</CardTitle><span className={`rounded-full border px-2 py-0.5 text-[11px] font-medium ${severityStyles[insight.severity]}`}>{insight.severity}</span></div><p className="mt-1 text-sm text-muted-foreground">{insight.service} / {insight.region}</p></div></div><div className="text-left sm:text-right"><p className="text-xs text-muted-foreground">Current cost</p><p className="font-semibold">{format(insight.currentCost)}</p></div></CardHeader><CardContent className="space-y-4 p-5"><div><p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">Problem detected</p><p className="mt-1 text-sm">{insight.problemDetected}</p><p className="mt-1 text-sm leading-6 text-muted-foreground">{insight.reason}</p></div><div className="grid gap-4 border-y border-border py-4 sm:grid-cols-2"><div><p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">Estimated impact / saving</p><p className="mt-1 text-sm font-medium">{insight.estimatedImpact > 0 ? format(insight.estimatedImpact) : "No material impact"}</p></div><div><p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">Resource</p><p className="mt-1 truncate text-sm" title={insight.resource}>{insight.resource}</p></div></div><div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between"><p className="flex gap-2 text-sm leading-6"><Server className="mt-1 size-4 shrink-0 text-primary" /><span><strong>Recommended:</strong> {insight.recommendedAction}</span></p><Button asChild className="shrink-0" size="sm" variant="outline"><Link href={insight.resourcePath}>View Resource</Link></Button></div></CardContent></Card>;
}
