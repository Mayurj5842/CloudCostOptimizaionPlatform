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

  return <div className="mx-auto max-w-7xl space-y-8">
    <header className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
      <div><p className="text-sm font-medium text-primary">Reporting</p><h1 className="mt-1 text-2xl font-semibold tracking-tight sm:text-3xl">AWS FinOps Report</h1><p className="mt-2 text-sm text-muted-foreground">{data.billingPeriodStart} through {data.billingPeriodEnd} · {data.dataSource}</p></div>
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center"><MonthSelector value={selectedMonth} onChange={setSelectedMonth} /><button className="h-10 rounded-lg bg-primary px-4 text-sm font-medium text-primary-foreground disabled:opacity-60" disabled={pdfState === "loading"} onClick={generatePdf}>{pdfState === "loading" ? "Generating PDF..." : "Generate PDF Report"}</button></div>
    </header>
    {data.status === "PARTIAL" && <p className="text-sm text-warning">{data.message}</p>}
    {pdfState === "success" && <p className="text-sm text-success">PDF downloaded successfully</p>}
    {pdfState === "error" && <p className="text-sm text-destructive">{pdfError}</p>}
    <section className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4"><Metric label="Total cost" value={money(data.summary.totalCost)} /><Metric label="Previous month" value={money(data.summary.previousMonthCost)} /><Metric label="Change" value={data.summary.changePercentage == null ? "Unavailable" : `${data.summary.changePercentage > 0 ? "+" : ""}${data.summary.changePercentage}%`} /><Metric label="Potential savings" value={data.savingsSummary.available ? money(data.savingsSummary.potentialMonthlySavings) : "Unavailable"} /></section>
    <div className="grid gap-6 xl:grid-cols-2"><ListCard title="Service breakdown" items={data.serviceBreakdown.map(item => `${item.name}: ${money(item.cost)}`)} /><ListCard title="Top cost drivers" items={data.topCostDrivers.map(item => `${item.service}: ${money(item.cost)} (${item.percentage}%)`)} /><ListCard title="Cost trend" items={data.costTrend.map(item => `${item.date}: ${money(item.cost)}`)} /><ListCard title="AWS resources" items={data.resourceSummary ? [`EC2: ${data.resourceSummary.ec2Instances} (${data.resourceSummary.runningEc2Instances} running)`, `EBS: ${data.resourceSummary.ebsVolumes} (${data.resourceSummary.unattachedEbsVolumes} unattached)`, `S3: ${data.resourceSummary.s3Buckets}`, `RDS: ${data.resourceSummary.rdsDatabases}`, `Lambda: ${data.resourceSummary.lambdaFunctions}`] : ["Resource data unavailable"]} /></div>
    <ListCard title="AI Insights" items={data.insights.length ? data.insights.map(item => `${item.category}: ${item.reason}`) : ["No insights for this billing period"]} />
    <ListCard title="Recommendations" items={data.recommendations.length ? data.recommendations.map(item => `${item.problem}: ${item.evidence ?? item.reason}`) : ["No recommendations for this billing period"]} />
    <ListCard title="Budget usage" items={data.budgets.length ? data.budgets.map(item => `${item.name}: ${item.actualSpend == null ? "Unavailable" : money(item.actualSpend)} / ${money(item.amount)} · ${item.status}`) : ["No budget configured"]} />
    <ListCard title="Alerts" items={data.alerts.length ? data.alerts.map(item => `${item.type} ${item.threshold}%: ${item.message}`) : ["No alerts triggered for this billing period"]} />
  </div>;
}

function Metric({ label, value }: { label: string; value: string }) { return <Card><CardContent className="p-5"><p className="text-xs uppercase tracking-wide text-muted-foreground">{label}</p><p className="mt-2 text-2xl font-semibold">{value}</p></CardContent></Card>; }
function ListCard({ title, items }: { title: string; items: string[] }) { return <Card><CardHeader><CardTitle className="text-base">{title}</CardTitle></CardHeader><CardContent className="space-y-2 text-sm">{items.map((item, index) => <p className="border-b border-border pb-2 last:border-0" key={`${item}-${index}`}>{item}</p>)}</CardContent></Card>; }
function EmptyState({ title, message, error = false }: { title: string; message: string; error?: boolean }) { const Icon = error ? AlertTriangle : CheckCircle2; return <Card><CardContent className="flex min-h-[300px] flex-col items-center justify-center text-center"><Icon className="size-9 text-muted-foreground" /><p className="mt-4 font-medium">{title}</p><p className="mt-2 text-sm text-muted-foreground">{message}</p></CardContent></Card>; }
