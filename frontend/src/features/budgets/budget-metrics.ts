import type { Budget } from "@/services/aws-connection";

export type BudgetTone = "success" | "warning" | "destructive" | "muted";

export const DEFAULT_THRESHOLDS = [50, 75, 90, 100] as const;

export function budgetThresholds(thresholds: number[] | null | undefined): number[] {
  const values = (thresholds ?? [])
    .filter((value) => Number.isFinite(value) && value > 0 && value <= 100)
    .sort((left, right) => left - right);
  return values.length ? values : [...DEFAULT_THRESHOLDS];
}

export function budgetTone(usagePercentage: number | null | undefined, thresholds: number[] | null | undefined): BudgetTone {
  if (usagePercentage == null || !Number.isFinite(usagePercentage)) return "muted";
  const levels = budgetThresholds(thresholds);
  const exceeded = levels[levels.length - 1];
  const warning = levels[Math.floor((levels.length - 1) / 2)];
  if (usagePercentage >= exceeded) return "destructive";
  if (usagePercentage >= warning) return "warning";
  return "success";
}

export function worstBudgetTone(budgets: Budget[]): BudgetTone {
  const tones = budgets.map((budget) => budgetTone(budget.usagePercentage, budget.thresholds));
  if (tones.includes("destructive")) return "destructive";
  if (tones.includes("warning")) return "warning";
  if (tones.includes("success")) return "success";
  return "muted";
}

export type BudgetSummary = {
  totalBudget: number;
  actualSpend: number | null;
  remaining: number | null;
  usagePercentage: number | null;
  currency: string;
  budgetsWithData: number;
  totalBudgets: number;
};

export function summarizeBudgets(budgets: Budget[]): BudgetSummary {
  const currency = budgets.find((budget) => budget.currency)?.currency ?? "USD";
  let totalBudget = 0;
  let actualSpend: number | null = null;
  let budgetsWithData = 0;
  for (const budget of budgets) {
    if (Number.isFinite(budget.amount)) totalBudget += budget.amount;
    if (budget.actualSpend != null && Number.isFinite(budget.actualSpend)) {
      actualSpend = (actualSpend ?? 0) + budget.actualSpend;
      budgetsWithData += 1;
    }
  }
  const remaining = actualSpend == null ? null : totalBudget - actualSpend;
  const usagePercentage = actualSpend == null || totalBudget <= 0 ? null : (actualSpend / totalBudget) * 100;
  return { totalBudget, actualSpend, remaining, usagePercentage, currency, budgetsWithData, totalBudgets: budgets.length };
}

export const BUDGET_TONE_TEXT: Record<BudgetTone, string> = {
  success: "text-success",
  warning: "text-warning",
  destructive: "text-destructive",
  muted: "text-muted-foreground",
};

export const BUDGET_TONE_BAR: Record<BudgetTone, string> = {
  success: "bg-success",
  warning: "bg-warning",
  destructive: "bg-destructive",
  muted: "bg-muted-foreground",
};

export const BUDGET_TONE_BADGE: Record<BudgetTone, string> = {
  success: "bg-success/10 text-success",
  warning: "bg-warning/10 text-warning",
  destructive: "bg-destructive/10 text-destructive",
  muted: "bg-muted text-muted-foreground",
};

export const BUDGET_TONE_LABEL: Record<BudgetTone, string> = {
  success: "On track",
  warning: "Warning",
  destructive: "Exceeded",
  muted: "No billing data",
};
