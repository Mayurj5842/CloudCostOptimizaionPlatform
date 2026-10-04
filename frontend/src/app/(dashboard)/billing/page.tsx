"use client";

import * as React from "react";
import Link from "next/link";
import ReactECharts from "echarts-for-react";
import { AlertTriangle, BarChart3, CalendarDays, RefreshCw } from "lucide-react";
import { useQuery } from "@tanstack/react-query";
import { useTheme } from "@/providers/theme-provider";

import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { getChartTheme } from "@/lib/chart-theme";
import { fetchAwsCostAnalytics, type AwsCostAnalyticsResponse } from "@/services/aws-connection";
import { useCloudAccountStore } from "@/store/cloud-account-store";
import { useMonthSelectionStore } from "@/store/month-selection-store";

type BreakdownView = "Service" | "Region" | "Account";
type TooltipEntry = { axisValue?: string | number; data?: unknown; seriesName?: string };

export default function BillingPage() {
  const { resolvedTheme } = useTheme();
  const month = useMonthSelectionStore((state) => state.selectedMonth);
  const setMonth = useMonthSelectionStore((state) => state.setSelectedMonth);
  const { account, connectionToken } = useCloudAccountStore();
  const analytics = useQuery({
    queryKey: ["aws-cost-analytics", account?.accountId, connectionToken, month],
    queryFn: () => fetchAwsCostAnalytics(connectionToken as string, "CURRENT_MONTH", month),
    enabled: Boolean(connectionToken),
    staleTime: 5 * 60 * 1000,
    retry: false,
    refetchOnWindowFocus: false,
    refetchOnReconnect: false,
  });

  if (!account || !connectionToken) {
    return <Card><CardContent className="flex min-h-[360px] flex-col items-center justify-center px-6 py-12 text-center"><BarChart3 className="size-10 text-primary" /><h1 className="mt-5 text-xl font-semibold">Connect an AWS account first</h1><p className="mt-2 max-w-md text-sm leading-6 text-muted-foreground">Cost Analytics uses billing data from the connected AWS account.</p></CardContent></Card>;
  }

  return <div className="mx-auto max-w-7xl space-y-8">
    <header className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
      <div>
        <p className="text-sm font-medium text-primary">Analytics</p>
        <h1 className="mt-1 text-2xl font-semibold tracking-tight sm:text-3xl">Cost Analytics</h1>
        <p className="mt-2 text-sm text-muted-foreground">AWS Account: {account.accountAlias}</p>
      </div>
      <div className="flex flex-col gap-3 sm:items-end">
        <div className="inline-flex overflow-hidden rounded-lg border border-border bg-background">
          <Link href="/billing" className="inline-flex items-center justify-center px-4 py-2 text-sm font-medium text-foreground hover:bg-accent/10">Analytics</Link>
          <Link href="/billing/bills" className="inline-flex items-center justify-center border-l border-border px-4 py-2 text-sm font-medium text-foreground hover:bg-accent/10">Bills</Link>
        </div>
        <label className="flex items-center gap-2 text-sm"><CalendarDays className="size-4 text-muted-foreground" /><span className="sr-only">Billing month</span><input aria-label="Billing month" className="h-10 rounded-lg border border-input bg-background px-3 text-sm" type="month" value={month} onChange={(event) => setMonth(event.target.value)} /></label>
      </div>
    </header>
    {analytics.isLoading && <LoadingState />}
    {analytics.isError && <ErrorState onRetry={() => analytics.refetch()} />}
    {analytics.data && <AnalyticsReport data={analytics.data} resolvedTheme={resolvedTheme} />}
  </div>;
}

function AnalyticsReport({ data, resolvedTheme }: { data: AwsCostAnalyticsResponse; resolvedTheme?: string }) {
  const format = (value: number | null) => value == null ? "Unavailable" : formatCurrency(value, data.currency);
  const isUnavailable = data.dataStatus === "UNAVAILABLE" || data.dataStatus === "NO_CUR_DATA";
  
  if (isUnavailable) {
    return <Card><CardContent className="flex min-h-[280px] flex-col items-center justify-center text-center"><BarChart3 className="size-10 text-muted-foreground" /><p className="mt-4 font-medium">Billing data is not available yet.</p><p className="mt-2 text-sm text-muted-foreground">Import CUR data or fetch live AWS cost for the selected period.</p></CardContent></Card>;
  }
  
  return <>
    <p className="text-xs text-muted-foreground">Latest AWS billing data from {data.dataSource ?? "PostgreSQL"} for {data.period.start} through {data.period.end}. {data.lastUpdated ? `Last updated: ${new Date(data.lastUpdated).toLocaleString()}.` : "Last updated: Unavailable."} Status: {data.dataStatus}.</p>
    <section className="grid gap-4 sm:grid-cols-2 xl:grid-cols-5">{[
      ["Total", format(data.summary.totalCost)], ["Previous period", format(data.summary.previousPeriodCost)], ["Difference", format(data.summary.difference)], ["Change", data.summary.percentageChange == null ? "Unavailable" : `${data.summary.percentageChange}%`], ["Daily average", format(data.summary.dailyAverage)],
    ].map(([label, value]) => <Card key={label}><CardContent className="p-5"><p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">{label}</p><p className="mt-2 text-2xl font-semibold">{value}</p></CardContent></Card>)}</section>
    <div className="grid gap-6 xl:grid-cols-2"><TrendChart data={data} resolvedTheme={resolvedTheme} /><BreakdownCard title="Cost by AWS Service" items={data.serviceCosts} currency={data.currency} /><BreakdownCard title="Cost by Region" items={data.regionCosts} currency={data.currency} /></div>
    <div className="grid gap-6 xl:grid-cols-2"><DriversCard data={data} /><AnomaliesCard data={data} /></div>
    <div className="grid gap-6 xl:grid-cols-2"><BudgetCard data={data} /><Card><CardHeader><CardTitle>Potential Savings</CardTitle></CardHeader><CardContent><p className="text-sm text-muted-foreground">{data.potentialSavings.available && data.potentialSavings.monthlySavings != null ? `${format(data.potentialSavings.monthlySavings)} / month` : "No recommendations available"}</p></CardContent></Card></div>
  </>;
}

function TrendChart({ data, resolvedTheme }: { data: AwsCostAnalyticsResponse; resolvedTheme?: string }) {
  const colors = typeof document === "undefined" ? null : getChartTheme();

  const [view, setView] = React.useState<BreakdownView>("Service");

  // aggregate daily data into month buckets (YYYY-MM)
  const monthMap = new Map<string, Map<string, number>>(); // month -> (service -> cost)
  const monthTotals: Map<string, number> = new Map();
  data.dailyServiceCosts.forEach(d => {
    const month = d.date.slice(0,7); // assume YYYY-MM-DD
    if (!monthMap.has(month)) monthMap.set(month, new Map());
    const svcMap = monthMap.get(month)!;
    d.services.forEach(s => {
      const prev = svcMap.get(s.name) ?? 0;
      svcMap.set(s.name, prev + Number(s.cost));
      monthTotals.set(month, (monthTotals.get(month) ?? 0) + Number(s.cost));
    });
  });

  const months = Array.from(monthMap.keys()).sort();
  const monthLabels = months.map(m => {
    try { const [y,mo] = m.split('-'); return new Date(Number(y), Number(mo)-1, 1).toLocaleString(undefined, { month: 'short', year: 'numeric' }); } catch { return m; }
  });

  // services (unique across months)
  const serviceSet = new Set<string>();
  monthMap.forEach(smap => smap.forEach((_, name) => serviceSet.add(name)));
  const services = Array.from(serviceSet);

  const serviceSeries = services.map(serviceName => ({
    name: serviceName,
    type: 'bar',
    stack: 'total',
    emphasis: { focus: 'series' },
    data: months.map(m => Number((monthMap.get(m)?.get(serviceName) ?? 0).toFixed(2)))
  }));

  // region: use aggregated regionCosts but distribute proportionally across dates
  const regionTotalsMap = new Map<string, number>(data.regionCosts.map(r => [r.name, Number(r.cost)]));
  const monthTotalsArr = months.map(m => monthTotals.get(m) ?? 0);
  const totalPeriod = monthTotalsArr.reduce((a,b) => a+b, 0) || 1;
  const regionNames = Array.from(regionTotalsMap.keys());
  const regionSeries = regionNames.map(regionName => {
    const regionTotal = regionTotalsMap.get(regionName) ?? 0;
    const ratio = regionTotal / totalPeriod;
    return {
      name: regionName,
      type: 'bar',
      stack: 'total',
      emphasis: { focus: 'series' },
      data: monthTotalsArr.map(mt => Number((mt * ratio).toFixed(2)))
    };
  });

  // account: single series for current account (frontend only knows current account)
  const accountId = data.accountId;
  const accountSeries = [{ name: `Account ${accountId}`, type: 'bar', stack: 'total', emphasis: { focus: 'series' }, data: monthTotalsArr.map(mt => Number(mt.toFixed(2))) }];

  const series = view === 'Service' ? serviceSeries : view === 'Region' ? regionSeries : accountSeries;

  const tooltipFormatter = (params: unknown) => {
    const items: TooltipEntry[] = (Array.isArray(params) ? params : [params])
      .filter((item): item is TooltipEntry => typeof item === "object" && item !== null);
    const axisValue = items[0]?.axisValue ?? '';
    items.sort((a, b) => (Number(b.data) || 0) - (Number(a.data) || 0));
    let total = 0;
    const rows = items.map((p) => {
      const val = Number(p.data) || 0;
      total += val;
      return `<div style="display:flex;justify-content:space-between;margin-bottom:4px"><span style="margin-right:8px">${p.seriesName ?? ""}</span><span>${formatCurrency(val, data.currency)}</span></div>`;
    }).join('');
    return `<div style="max-height:320px;overflow:auto;padding:6px"><div style="font-weight:600;margin-bottom:6px">${axisValue}</div>${rows}<hr style="border:none;border-top:1px solid ${colors?.grid};margin:6px 0"/><div style="display:flex;justify-content:space-between;font-weight:600"><span>Total</span><span>${formatCurrency(total, data.currency)}</span></div></div>`;
  };

  const option = {
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      formatter: tooltipFormatter,
      backgroundColor: colors?.surface,
      borderColor: colors?.grid,
      textStyle: { color: colors?.tooltip },
      confine: true,
      extraCssText: 'max-width:420px;overflow:auto;pointer-events:none;'
    },
    legend: { bottom: 1, textStyle: { color: colors?.axis }, type: 'plain', orient: 'horizontal', itemGap: 10, itemWidth: 14, itemHeight: 10 },
    grid: { left: 14, right: 14, top: 28, bottom: 140, containLabel: true },
    xAxis: { type: 'category', data: monthLabels, axisLine: { lineStyle: { color: colors?.grid } }, axisLabel: { color: colors?.axis, rotate: 0, interval: 0, align: 'center' }, boundaryGap: true },
    yAxis: { type: 'value', axisLabel: { color: colors?.axis, formatter: (v: number) => formatCurrency(v, data.currency) }, splitLine: { lineStyle: { color: colors?.grid } } },
    series
  };

  return <Card>
    <CardHeader className="flex items-center justify-between">
      <CardTitle>Cost Breakdown <span className="ml-2 text-sm text-muted-foreground">Info</span></CardTitle>
      <div className="flex items-center gap-3">
        <div className="text-sm text-muted-foreground">Group costs by</div>
        <select className="h-9 rounded border border-input bg-background px-2 text-sm" value={view} onChange={(e) => setView(e.target.value as BreakdownView)}>
          <option value="Service">Service</option>
          <option value="Region">Region</option>
          <option value="Account">Account</option>
        </select>
      </div>
    </CardHeader>
    <CardContent>
      <ReactECharts key={resolvedTheme + view} option={option} style={{ height: "360px", width: "100%" }} opts={{ renderer: "canvas" }} />
    </CardContent>
  </Card>;
}

function BreakdownCard({ title, items, currency }: { title: string; items: Array<{ name: string; cost: number }>; currency: string }) {
  return <Card><CardHeader><CardTitle>{title}</CardTitle></CardHeader><CardContent className="space-y-3">{items.length === 0 ? <p className="text-sm text-muted-foreground">No cost data available.</p> : items.map(item => <div className="flex items-center justify-between gap-4 text-sm" key={item.name}><span className="truncate">{displayServiceLabel(item.name)}</span><span className="font-medium">{formatCurrency(item.cost, currency)}</span></div>)}</CardContent></Card>;
}

function displayServiceLabel(name: string) {
  return {
    "Amazon Elastic Compute Cloud - Compute": "EC2",
    "Amazon Simple Storage Service": "S3",
    "Amazon Relational Database Service": "RDS",
    "AWS Lambda": "Lambda",
    "Amazon Elastic Block Store": "EBS",
    "Amazon CloudWatch": "CloudWatch",
  }[name] ?? name;
}

function DriversCard({ data }: { data: AwsCostAnalyticsResponse }) {
  return <Card><CardHeader><CardTitle>Top Cost Drivers</CardTitle></CardHeader><CardContent className="space-y-3">{data.topCostDrivers.length === 0 ? <p className="text-sm text-muted-foreground">No cost data available.</p> : data.topCostDrivers.map((item, index) => <div className="flex items-center justify-between gap-4 text-sm" key={item.service}><span className="truncate">{index + 1}. {item.service}</span><span className="whitespace-nowrap font-medium">{item.percentage}%</span></div>)}</CardContent></Card>;
}

function AnomaliesCard({ data }: { data: AwsCostAnalyticsResponse }) {
  return <Card><CardHeader><CardTitle>Cost Anomalies</CardTitle></CardHeader><CardContent>{data.anomalies.length === 0 ? <p className="text-sm text-muted-foreground">No significant cost anomalies detected.</p> : <div className="space-y-3">{data.anomalies.map(item => <div className="flex items-center gap-2 text-sm" key={item.date}><AlertTriangle className="size-4 text-warning" /><span>{item.date}: {item.percentageAboveAverage}% above recent average</span></div>)}</div>}</CardContent></Card>;
}

// function BudgetCard({ data }: { data: AwsCostAnalyticsResponse }) {
//   const budget = data.budget;
//   return <Card><CardHeader><CardTitle>Budget Usage</CardTitle></CardHeader><CardContent>{!budget.configured ? <p className="text-sm text-muted-foreground">No budget configured</p> : <div className="space-y-2"><p className="text-2xl font-semibold">{budget.usagePercentage}%</p><p className="text-sm text-muted-foreground">{formatCurrency(budget.actualSpend, budget.currency)} of {formatCurrency(budget.limit ?? 0, budget.currency)}</p></div>}</CardContent></Card>;
// }

function LoadingState() { return <section className="grid gap-4 sm:grid-cols-2 xl:grid-cols-5">{["Total", "This Month", "Daily Avg", "Forecast", "Savings"].map(label => <Card key={label}><CardContent className="p-5"><p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">{label}</p><p className="mt-2 text-2xl font-semibold">Loading...</p></CardContent></Card>)}</section>; }
function BudgetCard({ data }: { data: AwsCostAnalyticsResponse }) { const budget = data.budget; return <Card><CardHeader><CardTitle>Budget Usage</CardTitle></CardHeader><CardContent>{!budget.configured ? <p className="text-sm text-muted-foreground">No budget configured</p> : <div className="space-y-2"><p className="text-2xl font-semibold">{budget.usagePercentage == null ? "Unavailable" : `${budget.usagePercentage}%`}</p><p className="text-sm text-muted-foreground">{budget.actualSpend == null ? "No CUR data available" : `${formatCurrency(budget.actualSpend, budget.currency)} of ${formatCurrency(budget.limit ?? 0, budget.currency)}`}</p></div>}</CardContent></Card>; }
function ErrorState({ onRetry }: { onRetry: () => void }) { return <Card><CardContent className="flex min-h-[280px] flex-col items-center justify-center text-center"><p className="font-medium text-destructive">Unable to load AWS CUR billing data.</p><p className="mt-2 text-sm text-muted-foreground">Please retry after confirming the CUR import and PostgreSQL billing data are available.</p><Button className="mt-5" onClick={onRetry} variant="outline"><RefreshCw className="size-4" />Retry</Button></CardContent></Card>; }
function formatCurrency(value: number, currency: string) { return new Intl.NumberFormat("en-US", { style: "currency", currency, minimumFractionDigits: 2 }).format(value); }