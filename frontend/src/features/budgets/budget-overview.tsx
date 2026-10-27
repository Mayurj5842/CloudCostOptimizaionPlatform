"use client";

import { Percent, PiggyBank, TrendingUp, Wallet, type LucideIcon } from "lucide-react";

import type { Budget } from "@/services/aws-connection";
import {
  BUDGET_TONE_LABEL,
  BUDGET_TONE_TEXT,
  summarizeBudgets,
  worstBudgetTone,
  type BudgetTone,
} from "@/features/budgets/budget-metrics";

export function BudgetOverview({ budgets, month }: { budgets: Budget[]; month: string }) {
  const summary = summarizeBudgets(budgets);
  const tone = worstBudgetTone(budgets);
  const cards: Array<{ label: string; value: string; hint: string; icon: LucideIcon; tone: BudgetTone | "neutral" }> = [
    { label: "Total Budget", value: money(summary.totalBudget, summary.currency), hint: `${summary.totalBudgets} budget${summary.totalBudgets === 1 ? "" : "s"} for ${month}`, icon: Wallet, tone: "neutral" },
    { label: "Actual Spend", value: money(summary.actualSpend, summary.currency), hint: summary.budgetsWithData === summary.totalBudgets ? "All budgets have billing data" : `Billing data available for ${summary.budgetsWithData} of ${summary.totalBudgets} budget${summary.totalBudgets === 1 ? "" : "s"}`, icon: TrendingUp, tone: "neutral" },
    { label: "Remaining Budget", value: money(summary.remaining, summary.currency), hint: summary.remaining == null ? "Unavailable until billing data is present" : summary.remaining < 0 ? "Over budget" : "Available for the period", icon: PiggyBank, tone: summary.remaining == null ? "muted" : summary.remaining < 0 ? "destructive" : "success" },
    { label: "Usage", value: summary.usagePercentage == null ? "Unavailable" : `${summary.usagePercentage.toFixed(1)}%`, hint: BUDGET_TONE_LABEL[tone], icon: Percent, tone },
  ];
  return <section aria-label="Budget overview" className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-4">{cards.map((card) => <OverviewCard key={card.label} {...card} />)}</section>;
}

function OverviewCard({ label, value, hint, icon: Icon, tone }: { label: string; value: string; hint: string; icon: LucideIcon; tone: BudgetTone | "neutral" }) {
  const accent = tone === "neutral" ? "text-primary" : BUDGET_TONE_TEXT[tone];
  const valueClass = tone === "neutral" ? "text-foreground" : BUDGET_TONE_TEXT[tone];
  return <article className="rounded-xl border border-border bg-card p-5">
    <div className="flex items-center justify-between gap-3"><p className="text-sm font-medium text-muted-foreground">{label}</p><Icon className={`size-4 ${accent}`} /></div>
    <p className={`mt-3 text-2xl font-semibold ${valueClass}`}>{value}</p>
    <p className="mt-1 text-xs text-muted-foreground">{hint}</p>
  </article>;
}

function money(value: number | null, currency: string | null) {
  return value == null || currency == null ? "Unavailable" : new Intl.NumberFormat(undefined, { style: "currency", currency, minimumFractionDigits: 2 }).format(value);
}
