"use client";

import { AlertTriangle, CheckCircle2, LoaderCircle } from "lucide-react";
import { useQuery } from "@tanstack/react-query";
import { useState } from "react";

import { MonthSelector } from "@/components/month-selector";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { downloadAwsReportPdf, fetchAwsReport } from "@/services/aws-connection";
import { useCloudAccountStore } from "@/store/cloud-account-store";
import { useMonthSelectionStore } from "@/store/month-selection-store";

export default function ReportsPage() {
  const { account, connectionToken } = useCloudAccountStore();
  const selectedMonth = useMonthSelectionStore((state) => state.selectedMonth);
  const setSelectedMonth = useMonthSelectionStore((state) => state.setSelectedMonth);
  const [pdfState, setPdfState] = useState<"idle" | "loading" | "success" | "error">("idle");
  const [pdfError, setPdfError] = useState("Unable to generate PDF report. Please try again.");
  const report = useQuery({
    queryKey: ["aws-report", account?.accountId, connectionToken, selectedMonth],
    queryFn: () => fetchAwsReport(connectionToken as string, selectedMonth),
    enabled: Boolean(connectionToken),
    retry: false,
  });

  if (!account || !connectionToken) return <EmptyState title="Connect an AWS account first" message="Reports use persisted billing and AWS resource data." />;
  if (report.isLoading) return <Card><CardContent className="flex min-h-[260px] items-center justify-center text-sm text-muted-foreground"><LoaderCircle className="mr-2 size-4 animate-spin" />Generating report...</CardContent></Card>;
  if (report.isError) return <EmptyState title="Unable to generate report" message={report.error instanceof Error ? report.error.message : "The report could not be generated."} error />;
  const data = report.data;
  if (!data || data.status === "NO_DATA") return <EmptyState title="No report data available" message={data?.message ?? "No billing data was found for this billing period."} />;
  const money = (value: number | null) => value == null ? "Unavailable" : new Intl.NumberFormat("en-US", { style: "currency", currency: data.currency }).format(value);

  const generatePdf = async () => {
    setPdfState("loading");
    setPdfError("Unable to generate PDF report. Please try again.");
    try {
      const blob = await downloadAwsReportPdf(connectionToken, selectedMonth);
      const url = URL.createObjectURL(blob);
      const link = document.createElement("a");
      link.href = url;
      link.download = `CloudCostAI-AWS-Report-${selectedMonth}.pdf`;
      link.click();
      URL.revokeObjectURL(url);
      setPdfState("success");
    } catch (error) {
      setPdfError(error instanceof Error ? error.message : "Unable to generate PDF report. Please try again.");
      setPdfState("error");
    }
  };

  const colors = ["bg-blue-500", "bg-emerald-500", "bg-amber-500", "bg-purple-500", "bg-pink-500", "bg-cyan-500", "bg-orange-500", "bg-indigo-500"];

  return <div className="mx-auto max-w-7xl space-y-8">
    <header className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
      <div className="flex items-center flex-wrap gap-2 text-sm">
        <span className="font-medium text-primary">Reporting</span>
        <span className="text-muted-foreground">/</span>
        <h1 className="text-lg sm:text-xl font-semibold tracking-tight">AWS FinOps Report</h1>
        <span className="hidden sm:inline text-muted-foreground">-</span>
        <span className="text-muted-foreground">{data.billingPeriodStart}</span>
        <span className="text-muted-foreground">to</span>
        <span className="text-muted-foreground">{data.billingPeriodEnd}</span>
        <span className="text-muted-foreground">·</span>
        <span className="text-muted-foreground">{data.dataSource}</span>
      </div>
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center"><MonthSelector value={selectedMonth} onChange={setSelectedMonth} /><button className="h-10 rounded-lg bg-primary px-4 text-sm font-medium text-primary-foreground disabled:opacity-60" disabled={pdfState === "loading"} onClick={generatePdf}>{pdfState === "loading" ? "Generating PDF..." : "Generate PDF Report"}</button></div>
    </header>
    {data.status === "PARTIAL" && <p className="text-sm text-warning">{data.message}</p>}
    {pdfState === "success" && <p className="text-sm text-success">PDF downloaded successfully</p>}
    {pdfState === "error" && <p className="text-sm text-destructive">{pdfError}</p>}
    <section className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
      <Metric label="Total cost" value={money(data.summary.totalCost)} />
      <Metric label="Previous month" value={money(data.summary.previousMonthCost)} />
      <Metric label="Change" value={data.summary.changePercentage == null ? "Unavailable" : `${data.summary.changePercentage > 0 ? "+" : ""}${data.summary.changePercentage}%`} />
      <Metric label="Potential savings" value={data.savingsSummary.available ? money(data.savingsSummary.potentialMonthlySavings) : "Unavailable"} />
    </section>
    <div className="grid gap-6 xl:grid-cols-2">
      <Card className="shadow-sm">
        <CardHeader><CardTitle className="text-base">Service breakdown</CardTitle></CardHeader>
        <CardContent>
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead className="text-muted-foreground">
                <tr className="text-left">
                  <th className="py-2 pr-4">Service</th>
                  <th className="py-2 pr-4">Cost</th>
                  <th className="py-2">Share</th>
                </tr>
              </thead>
              <tbody>
                {data.serviceBreakdown.map((item, idx) => {
                  const total = data.summary.totalCost ?? 0;
                  const share = total > 0 ? (item.cost / total) * 100 : 0;
                  return (
                    <tr key={idx} className="border-t">
                      <td className="py-2 pr-4">{item.name}</td>
                      <td className="py-2 pr-4 font-medium">{money(item.cost)}</td>
                      <td className="py-2">
                        <div className="flex items-center gap-2">
                          <div className="h-2 w-24 rounded bg-muted">
                            <div className={`h-2 rounded ${colors[idx % colors.length]}`} style={{ width: `${Math.min(share, 100)}%` }} />
                          </div>
                          <span className="text-xs text-muted-foreground">{share.toFixed(1)}%</span>
                        </div>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        </CardContent>
      </Card>
      <Card className="shadow-sm">
        <CardHeader><CardTitle className="text-base">Top cost drivers</CardTitle></CardHeader>
        <CardContent className="space-y-2">
          {data.topCostDrivers.map((item, idx) => {
            const total = data.summary.totalCost ?? 0;
            const pct = item.percentage ?? (total > 0 ? (item.cost / total) * 100 : 0);
            return (
              <div key={idx} className="flex items-center justify-between gap-3">
                <div className="min-w-0">
                  <p className="text-sm font-medium truncate">{item.service}</p>
                  <div className="mt-1 h-2 w-40 rounded bg-muted">
                    <div className={`h-2 rounded ${colors[idx % colors.length]}`} style={{ width: `${Math.min(pct, 100)}%` }} />
                  </div>
                </div>
                <div className="text-right text-sm">
                  <p className="font-medium">{money(item.cost)}</p>
                  <p className="text-xs text-muted-foreground">{pct.toFixed(1)}%</p>
                </div>
              </div>
            );
          })}
          {!data.topCostDrivers.length && <p className="text-sm text-muted-foreground">No data</p>}
        </CardContent>
      </Card>
      <Card className="shadow-sm">
        <CardHeader><CardTitle className="text-base">Cost trend</CardTitle></CardHeader>
        <CardContent className="space-y-2">
          {data.costTrend.map((item, idx) => {
            const max = Math.max(...data.costTrend.map(t => t.cost || 0), 1);
            const w = max > 0 ? ((item.cost || 0) / max) * 100 : 0;
            return (
              <div key={idx} className="flex items-center justify-between gap-3">
                <span className="text-sm text-muted-foreground w-24">{item.date}</span>
                <div className="flex-1 h-2 rounded bg-muted">
                  <div className={`h-2 rounded ${colors[idx % colors.length]}`} style={{ width: `${w}%` }} />
                </div>
                <span className="text-sm font-medium w-24 text-right">{money(item.cost)}</span>
              </div>
            );
          })}
          {!data.costTrend.length && <p className="text-sm text-muted-foreground">No data</p>}
        </CardContent>
      </Card>
      <Card className="shadow-sm">
        <CardHeader><CardTitle className="text-base">AWS resources</CardTitle></CardHeader>
        <CardContent className="grid grid-cols-2 gap-3 text-sm">
          {data.resourceSummary ? (
            <>
              <div className="rounded border p-2"><p className="text-xs text-muted-foreground">EC2</p><p className="font-medium">{data.resourceSummary.ec2Instances} ({data.resourceSummary.runningEc2Instances} running)</p></div>
              <div className="rounded border p-2"><p className="text-xs text-muted-foreground">EBS</p><p className="font-medium">{data.resourceSummary.ebsVolumes} ({data.resourceSummary.unattachedEbsVolumes} unattached)</p></div>
              <div className="rounded border p-2"><p className="text-xs text-muted-foreground">S3</p><p className="font-medium">{data.resourceSummary.s3Buckets}</p></div>
              <div className="rounded border p-2"><p className="text-xs text-muted-foreground">RDS</p><p className="font-medium">{data.resourceSummary.rdsDatabases}</p></div>
              <div className="rounded border p-2"><p className="text-xs text-muted-foreground">Lambda</p><p className="font-medium">{data.resourceSummary.lambdaFunctions}</p></div>
            </>
          ) : <p className="text-sm text-muted-foreground col-span-2">Resource data unavailable</p>}
        </CardContent>
      </Card>
    </div>
    <div className="grid gap-6 xl:grid-cols-2">
      <Card className="shadow-sm">
        <CardHeader><CardTitle className="text-base">AI Insights</CardTitle></CardHeader>
        <CardContent className="space-y-2">
          {data.insights.length ? data.insights.map((item, idx) => (
            <div key={idx} className="rounded border p-2">
              <p className="text-sm font-medium">{item.category} <span className="text-xs text-muted-foreground">[{item.severity}]</span></p>
              <p className="text-xs text-muted-foreground">{item.service} · {item.region}</p>
              <p className="text-sm mt-1">{item.reason}</p>
            </div>
          )) : <p className="text-sm text-muted-foreground">No insights for this billing period</p>}
        </CardContent>
      </Card>
        <Card className="shadow-sm">
          <CardHeader><CardTitle className="text-base">Recommendations</CardTitle></CardHeader>
          <CardContent className="space-y-3">
            {data.recommendations.length ? data.recommendations.map((item, idx) => {
              const saving = item.estimatedMonthlySaving ?? 0;
              const current = item.currentCost ?? 0;
              const impact = current > 0 ? Math.min((saving / current) * 100, 100) : 0;
              return (
                <div key={idx} className="rounded-lg border p-3 bg-card">
                  <div className="flex items-start justify-between gap-3">
                    <div>
                      <p className="text-sm font-medium">{item.problem}</p>
                      <p className="text-xs text-muted-foreground mt-0.5">{item.service} · {item.resource || "N/A"} · Priority: {item.priority || "N/A"}</p>
                    </div>
                    <span className="text-xs px-2 py-0.5 rounded bg-emerald-50 text-emerald-700 dark:bg-emerald-950/30 dark:text-emerald-200">{item.category || "Optimization"}</span>
                  </div>
                  <div className="mt-3 grid grid-cols-3 gap-2 text-xs">
                    <div className="rounded border p-1.5"><p className="text-muted-foreground">Current</p><p className="font-medium">{money(item.currentCost)}</p></div>
                    <div className="rounded border p-1.5"><p className="text-muted-foreground">Est. Saving</p><p className="font-medium text-emerald-600">{money(item.estimatedMonthlySaving)}</p></div>
                    <div className="rounded border p-1.5"><p className="text-muted-foreground">Impact</p><p className="font-medium">{impact.toFixed(0)}%</p></div>
                  </div>
                  <div className="mt-2 h-2 rounded bg-muted">
                    <div className="h-2 rounded bg-emerald-500" style={{ width: `${impact}%` }} />
                  </div>
                  <div className="mt-2 text-sm">
                    <div className="rounded-md border bg-muted/50 p-2">
                      <p className="text-xs uppercase tracking-wide text-muted-foreground">Action</p>
                      <p className="mt-1 font-medium">{item.recommendedAction}</p>
                    </div>
                  </div>
                  {item.savingsBasis && <p className="text-xs text-muted-foreground mt-1">Basis: {item.savingsBasis}</p>}
                </div>
              );
            }) : <p className="text-sm text-muted-foreground">No recommendations for this billing period</p>}
          </CardContent>
        </Card>
      <Card className="shadow-sm">
        <CardHeader><CardTitle className="text-base">Budget usage</CardTitle></CardHeader>
        <CardContent className="space-y-2">
          {data.budgets.length ? data.budgets.map((item, idx) => {
            const spent = item.actualSpend ?? 0;
            const amt = item.amount ?? 1;
            const pct = Math.min((spent / amt) * 100, 100);
            return (
              <div key={idx} className="flex items-center justify-between gap-3">
                <div className="min-w-0">
                  <p className="text-sm font-medium truncate">{item.name}</p>
                  <div className="mt-1 h-2 w-40 rounded bg-muted">
                    <div className="h-2 rounded bg-sky-500" style={{ width: `${pct}%` }} />
                  </div>
                </div>
                <div className="text-right text-sm">
                  <p>{item.actualSpend == null ? "Unavailable" : money(item.actualSpend)} / {money(item.amount)}</p>
                  <p className="text-xs text-muted-foreground">{pct.toFixed(1)}% · {item.status}</p>
                </div>
              </div>
            );
          }) : <p className="text-sm text-muted-foreground">No budget configured</p>}
        </CardContent>
      </Card>
      <Card className="shadow-sm">
        <CardHeader><CardTitle className="text-base">Alerts</CardTitle></CardHeader>
        <CardContent className="space-y-2">
          {data.alerts.length ? data.alerts.map((item, idx) => (
            <div key={idx} className="rounded border border-amber-200 bg-amber-50 p-2 dark:border-amber-800 dark:bg-amber-950/30">
              <p className="text-sm font-medium text-amber-900 dark:text-amber-100">{item.type} {item.threshold}%</p>
              <p className="text-sm text-amber-800 dark:text-amber-200 mt-0.5">{item.message}</p>
            </div>
          )) : <p className="text-sm text-muted-foreground">No alerts triggered for this billing period</p>}
        </CardContent>
      </Card>
    </div>
  </div>;
}

function Metric({ label, value }: { label: string; value: string }) {
  const colors: Record<string, string> = { "Total cost": "border-l-emerald-500", "Previous month": "border-l-sky-500", "Change": "border-l-amber-500", "Potential savings": "border-l-purple-500" };
  return <Card className={`shadow-sm border-l-4 ${colors[label] || "border-l-border"}`}><CardContent className="p-5"><p className="text-xs uppercase tracking-wide text-muted-foreground">{label}</p><p className="mt-2 text-2xl font-semibold">{value}</p></CardContent></Card>;
}
function EmptyState({ title, message, error = false }: { title: string; message: string; error?: boolean }) { const Icon = error ? AlertTriangle : CheckCircle2; return <Card><CardContent className="flex min-h-[300px] flex-col items-center justify-center text-center"><Icon className="size-9 text-muted-foreground" /><p className="mt-4 font-medium">{title}</p><p className="mt-2 text-sm text-muted-foreground">{message}</p></CardContent></Card>; }
