"use client";

import { LoaderCircle, X } from "lucide-react";

import type { Budget } from "@/services/aws-connection";
import { BUDGET_TONE_BADGE, BUDGET_TONE_BAR, BUDGET_TONE_LABEL, BUDGET_TONE_TEXT, budgetTone } from "@/features/budgets/budget-metrics";
import { BudgetOverview } from "@/features/budgets/budget-overview";

export function BudgetUsageDialog({ budgets, month, isLoading, isError, onClose }: { budgets: Budget[] | null | undefined; month: string; isLoading: boolean; isError: boolean; onClose: () => void }) {
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/35 p-4" onClick={onClose}>
      <div
        aria-labelledby="budget-overview-title"
        aria-modal="true"
        className="max-h-[min(720px,calc(100vh-2rem))] w-full max-w-3xl overflow-y-auto rounded-xl border border-border bg-card p-6 text-card-foreground shadow-2xl"
        role="dialog"
        onClick={(event) => event.stopPropagation()}
      >
        <div className="flex items-start justify-between gap-4">
          <div>
            <p className="text-xs font-medium uppercase tracking-wide text-primary">Budget &amp; Alerts</p>
            <h2 className="mt-1 text-xl font-semibold" id="budget-overview-title">Budget overview <span className="text-muted-foreground">({month})</span></h2>
          </div>
          <button aria-label="Close budget overview" className="rounded-lg p-2 text-muted-foreground transition-colors hover:bg-muted hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring" onClick={onClose} type="button"><X className="size-5" /></button>
        </div>

        {isLoading && <div className="flex items-center justify-center py-12 text-sm text-muted-foreground"><LoaderCircle className="mr-2 size-4 animate-spin" />Loading budgets...</div>}
        {isError && <p className="py-12 text-center text-sm text-destructive">Unable to load budget information.</p>}
        {budgets?.length ? <div className="mt-6 space-y-6">
          <BudgetOverview budgets={budgets} month={month} />
          <div className="space-y-4">{budgets.map((budget) => <BudgetUsageRow budget={budget} key={budget.id} />)}</div>
        </div> : null}
        {!isLoading && !isError && !budgets?.length && <p className="py-12 text-center text-sm text-muted-foreground">No budgets configured for {month}.</p>}
      </div>
    </div>
  );
}

export function BudgetUsageRow({ budget }: { budget: Budget }) {
  const usage = budget.usagePercentage;
  const tone = budgetTone(usage, budget.thresholds);
  const hasSpend = usage != null && budget.actualSpend != null && budget.amount != null;
  return <div className="rounded-lg border border-border p-4">
    <div className="flex flex-wrap items-center justify-between gap-2">
      <div><p className="text-sm font-medium">{budget.name}</p><p className="text-xs text-muted-foreground">{budget.serviceName ?? "All AWS services"}{budget.enabled ? "" : " · Disabled"}</p></div>
      <span className={`rounded-full px-2 py-0.5 text-[10px] font-semibold ${BUDGET_TONE_BADGE[tone]}`}>{BUDGET_TONE_LABEL[tone]}</span>
    </div>
    {hasSpend ? <>
      <div className="mt-3 flex items-center justify-between text-xs text-muted-foreground"><span>{formatCurrency(budget.actualSpend ?? 0, budget.currency)} of {formatCurrency(budget.amount, budget.currency)}</span><span className={`font-medium ${BUDGET_TONE_TEXT[tone]}`}>{usage.toFixed(1)}%</span></div>
      <div className="mt-1.5 h-2.5 overflow-hidden rounded-full bg-muted" role="progressbar" aria-label={`${budget.name} budget utilization`} aria-valuemin={0} aria-valuemax={100} aria-valuenow={Math.max(0, Math.min(usage, 100))}><div className={`h-full rounded-full ${BUDGET_TONE_BAR[tone]}`} style={{ width: `${Math.min(Math.max(usage, 0), 100)}%` }} /></div>
    </> : <p className="mt-2 text-xs text-muted-foreground">No billing data is available for this budget in the selected period.</p>}
  </div>;
}

function formatCurrency(value: number, currency: string) {
  return new Intl.NumberFormat("en-US", { style: "currency", currency, minimumFractionDigits: 2 }).format(value);
}
