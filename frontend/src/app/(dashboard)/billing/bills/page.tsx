"use client";

import * as React from "react";
import { useQuery } from "@tanstack/react-query";
import { ShieldCheck, Loader2, RefreshCw } from "lucide-react";

import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { fetchAwsBillSummary } from "@/services/aws-bill";
import { useCloudAccountStore } from "@/store/cloud-account-store";

export default function BillsPage() {
  const { account, connectionToken } = useCloudAccountStore();
  const billQuery = useQuery({
    queryKey: ["aws-bill-summary", account?.accountId, connectionToken],
    queryFn: () => fetchAwsBillSummary(connectionToken as string),
    enabled: Boolean(connectionToken),
    staleTime: 5 * 60 * 1000,
    retry: false,
    refetchOnWindowFocus: false,
    refetchOnReconnect: false,
  });

  if (!account || !connectionToken) {
    return <Card><CardContent className="flex min-h-[360px] flex-col items-center justify-center px-6 py-12 text-center"><ShieldCheck className="size-10 text-primary" /><h1 className="mt-5 text-xl font-semibold">Connect an AWS account first</h1><p className="mt-2 max-w-md text-sm leading-6 text-muted-foreground">The Bills summary uses billing data from the connected AWS account.</p></CardContent></Card>;
  }

  if (billQuery.isLoading) {
    return <LoadingState />;
  }

  if (billQuery.isError || !billQuery.data) {
    return <ErrorState onRetry={() => billQuery.refetch()} />;
  }

  return <div className="mx-auto max-w-4xl space-y-8">
    <header className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
      <div><p className="text-sm font-medium text-primary">Bills</p><h1 className="mt-1 text-2xl font-semibold tracking-tight sm:text-3xl">AWS Estimated Bill Summary</h1><p className="mt-2 text-sm text-muted-foreground">Billing data for the connected AWS account.</p></div>
      <Button variant="outline" onClick={() => billQuery.refetch()}><RefreshCw className="mr-2 h-4 w-4" />Refresh</Button>
    </header>

    <Card>
      <CardHeader><CardTitle>Bill Summary</CardTitle></CardHeader>
      <CardContent className="space-y-6">
        <div className="grid gap-6 md:grid-cols-2">
          <InfoRow label="Account ID" value={billQuery.data.accountId} />
          <InfoRow label="Billing period" value={formatBillingPeriod(billQuery.data.billingPeriod.start, billQuery.data.billingPeriod.end)} />
          <InfoRow label="Bill status" value={billQuery.data.billStatus === "NO_CUR_DATA" ? "No CUR data available" : billQuery.data.billStatus} />
          <InfoRow label="Service provider" value={billQuery.data.serviceProvider} />
        </div>
        <div className="grid gap-6 md:grid-cols-2">
          <InfoRow label="Total in USD" value={formatCurrency(billQuery.data.totalAmount, billQuery.data.currency)} />
          <InfoRow label="Estimated grand total" value={formatCurrency(billQuery.data.estimatedGrandTotal, billQuery.data.currency)} />
        </div>
        <div className="rounded-lg border border-border bg-background p-4">
          <p className="text-sm font-semibold">Payment information</p>
          <p className="mt-2 text-sm text-muted-foreground">{billQuery.data.paymentInformation || "No billing data available."}</p>
        </div>
      </CardContent>
    </Card>
  </div>;
}

function InfoRow({ label, value }: { label: string; value: string }) {
  return <div className="rounded-lg border border-border bg-muted px-4 py-3 sm:px-5">
    <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">{label}</p>
    <p className="mt-2 text-sm font-semibold text-foreground">{value}</p>
  </div>;
}

function LoadingState() {
  return <Card><CardContent className="flex min-h-[360px] flex-col items-center justify-center px-6 py-12 text-center"><Loader2 className="size-10 animate-spin text-primary" /><h1 className="mt-5 text-xl font-semibold">Loading AWS bill summary...</h1><p className="mt-2 max-w-md text-sm leading-6 text-muted-foreground">Loading billing data from the PostgreSQL CUR import.</p></CardContent></Card>;
}

function ErrorState({ onRetry }: { onRetry: () => void }) {
  return <Card><CardContent className="flex min-h-[360px] flex-col items-center justify-center px-6 py-12 text-center"><p className="text-lg font-semibold text-destructive">Unable to load AWS billing information.</p><p className="mt-2 max-w-md text-sm leading-6 text-muted-foreground">Please verify your AWS billing permissions and try again.</p><Button className="mt-5" variant="outline" onClick={onRetry}>Retry</Button></CardContent></Card>;
}

function formatBillingPeriod(start: string, end: string) {
  const startDate = new Date(start);
  const endDate = new Date(end);
  return `${startDate.toLocaleString("en-US", { month: "long" })} ${startDate.getDate()} - ${endDate.toLocaleString("en-US", { month: "long" })} ${endDate.getDate()}, ${endDate.getFullYear()}`;
}

function formatCurrency(value: number, currency: string) {
  return new Intl.NumberFormat("en-US", { style: "currency", currency, minimumFractionDigits: 2 }).format(value);
}
