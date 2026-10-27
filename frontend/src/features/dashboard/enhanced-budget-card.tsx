"use client";

import { useState } from "react";
import { LucideIcon, X, LoaderCircle, AlertTriangle } from "lucide-react";
import { useQuery } from "@tanstack/react-query";
import { fetchBudgets, type Budget } from "@/services/aws-connection";
import { useCloudAccountStore } from "@/store/cloud-account-store";
import { Card, CardContent } from "@/components/ui/card";

interface EnhancedBudgetCardProps {
  month: string;
  icon: LucideIcon;
}

export default function EnhancedBudgetCard({ month, icon: Icon }: EnhancedBudgetCardProps) {
  const [isOpen, setIsOpen] = useState(false);
  const connectionToken = useCloudAccountStore((state) => state.connectionToken);
  const { data: budgets, isLoading, isError } = useQuery({
    queryKey: ["budgets", connectionToken, month],
    queryFn: () => fetchBudgets(connectionToken as string, month),
    enabled: Boolean(connectionToken),
    staleTime: 5 * 60 * 1000,
    retry: false,
  });

  const overallBudget = budgets?.find(b => !b.serviceName);
  const serviceBudgets = budgets?.filter(b => b.serviceName) ?? [];

  return (
    <>
      <Card
        className="cursor-pointer border-border transition-all duration-200 hover:border-primary/40"
        role="button"
        tabIndex={0}
        onClick={() => setIsOpen(true)}
        onKeyDown={(event) => {
          if (event.key === "Enter" || event.key === " ") {
            event.preventDefault();
            setIsOpen(true);
          }
        }}
      >
        <CardContent className="p-6">
          <div className="flex items-center justify-between">
            <Icon className="h-5 w-5 text-primary" />
            {overallBudget && <BudgetStatusBadge status={overallBudget.status} />}
          </div>

          <h3 className="mt-6 text-sm font-medium text-muted-foreground">
            Budget Usage
          </h3>

          {isLoading && (
            <div className="mt-4 flex items-center justify-center py-8">
              <LoaderCircle className="h-6 w-6 animate-spin text-muted-foreground" />
            </div>
          )}

          {isError && (
            <div className="mt-4 flex items-center gap-2 text-sm text-destructive">
              <AlertTriangle className="h-4 w-4" />
              Unable to load budget data
            </div>
          )}

          {!isLoading && !isError && budgets && budgets.length > 0 && (
            <>
              {overallBudget ? (
                <CompactOverallBudgetSummary budget={overallBudget} />
              ) : (
                <div className="mt-4 text-sm text-muted-foreground">
                  {serviceBudgets.length > 0 ? "Service-wise budgets configured" : "No budget configured"}
                </div>
              )}

              {serviceBudgets.length > 0 && (
                <CompactServiceBudgetBreakdown budgets={serviceBudgets} />
              )}
            </>
          )}

          {!isLoading && !isError && budgets && budgets.length === 0 && (
            <div className="mt-4 text-sm text-muted-foreground">
              No budget configured for this period
            </div>
          )}
        </CardContent>
      </Card>

      {isOpen && (
        <BudgetDetailsDialog
          budgets={budgets ?? []}
          overallBudget={overallBudget}
          serviceBudgets={serviceBudgets}
          isLoading={isLoading}
          isError={isError}
          onClose={() => setIsOpen(false)}
        />
      )}
    </>
  );
}

function CompactOverallBudgetSummary({ budget }: { budget: Budget }) {
  const hasSpend = budget.actualSpend != null && budget.remainingAmount != null && budget.usagePercentage != null;
  const progressColor = budget.usagePercentage != null && budget.usagePercentage >= 100
    ? "bg-destructive"
    : budget.usagePercentage != null && budget.usagePercentage >= 80
    ? "bg-warning"
    : "bg-primary";

  return (
    <div className="mt-4 space-y-3">
      <div className="flex items-end justify-between gap-2">
        <div>
          <p className="text-xs text-muted-foreground">Total Budget</p>
          <p className="mt-1 text-xl font-semibold">{formatCurrency(budget.amount, budget.currency)}</p>
        </div>
        {hasSpend && (
          <div className="text-right">
            <p className="text-xs text-muted-foreground">Actual Spend</p>
            <p className="mt-1 text-base font-medium">{formatCurrency(budget.actualSpend!, budget.currency)}</p>
          </div>
        )}
      </div>

      {hasSpend && (
        <>
          <div className="flex items-center justify-between text-sm">
            <span className="font-medium">{budget.usagePercentage!.toFixed(1)}% used</span>
            <span className="text-muted-foreground">
              {budget.remainingAmount! < 0 ? "Over" : "Remaining"} {formatCurrency(Math.abs(budget.remainingAmount!), budget.currency)}
            </span>
          </div>

          <div
            className="h-2 overflow-hidden rounded-full bg-muted"
            role="progressbar"
            aria-label="Budget utilization"
            aria-valuemin={0}
            aria-valuemax={100}
            aria-valuenow={Math.max(0, Math.min(budget.usagePercentage!, 100))}
          >
            <div
              className={`h-full rounded-full ${progressColor}`}
              style={{ width: `${Math.min(Math.max(budget.usagePercentage!, 0), 100)}%` }}
            />
          </div>
        </>
      )}

      {!hasSpend && (
        <p className="text-sm text-muted-foreground">
          No billing data available
        </p>
      )}
    </div>
  );
}

function OverallBudgetSummary({ budget }: { budget: Budget }) {
  const hasSpend = budget.actualSpend != null && budget.remainingAmount != null && budget.usagePercentage != null;
  const progressColor = budget.usagePercentage != null && budget.usagePercentage >= 100
    ? "bg-destructive"
    : budget.usagePercentage != null && budget.usagePercentage >= 80
    ? "bg-warning"
    : "bg-primary";
  const statusColor = budget.status === "EXCEEDED"
    ? "text-destructive"
    : budget.status === "CRITICAL" || budget.status === "WARNING"
    ? "text-warning"
    : "text-success";

  return (
    <div className="space-y-4">
      <div className="flex items-end justify-between gap-2">
        <div>
          <p className="text-xs text-muted-foreground">Total Budget</p>
          <p className="mt-1 text-2xl font-semibold">{formatCurrency(budget.amount, budget.currency)}</p>
        </div>
        {hasSpend && (
          <div className="text-right">
            <p className="text-xs text-muted-foreground">Actual Spend</p>
            <p className="mt-1 text-lg font-medium">{formatCurrency(budget.actualSpend!, budget.currency)}</p>
          </div>
        )}
      </div>

      {hasSpend && (
        <>
          <div className="flex items-center justify-between text-sm">
            <span className="font-medium">{budget.usagePercentage!.toFixed(1)}% used</span>
            <span className="text-muted-foreground">
              {budget.remainingAmount! < 0 ? "Over" : "Remaining"} {formatCurrency(Math.abs(budget.remainingAmount!), budget.currency)}
            </span>
          </div>

          <div
            className="h-2.5 overflow-hidden rounded-full bg-muted"
            role="progressbar"
            aria-label="Budget utilization"
            aria-valuemin={0}
            aria-valuemax={100}
            aria-valuenow={Math.max(0, Math.min(budget.usagePercentage!, 100))}
          >
            <div
              className={`h-full rounded-full ${progressColor}`}
              style={{ width: `${Math.min(Math.max(budget.usagePercentage!, 0), 100)}%` }}
            />
          </div>

          <div className="flex items-center justify-between text-xs">
            <span className={statusColor}>
              {budget.status === "EXCEEDED" ? "Exceeded" : budget.status === "CRITICAL" ? "Critical" : budget.status === "WARNING" ? "Warning" : budget.status === "NORMAL" ? "On Track" : budget.status === "SAFE" ? "On Track" : budget.status}
            </span>
            <span className="text-muted-foreground">Monthly budget</span>
          </div>
        </>
      )}

      {!hasSpend && (
        <p className="text-sm text-muted-foreground">
          No billing data available for {budget.period.toLowerCase()}
        </p>
      )}
    </div>
  );
}

function CompactServiceBudgetBreakdown({ budgets }: { budgets: Budget[] }) {
  if (budgets.length === 0) return null;

  return (
    <div className="mt-4 space-y-2 border-t border-border pt-3">
      <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
        Service Budgets ({budgets.length})
      </p>
      <div className="space-y-2">
        {budgets.slice(0, 2).map((budget) => (
          <div key={budget.id} className="flex items-center justify-between text-xs">
            <span className="text-muted-foreground">{budget.serviceName}</span>
            <span className="font-medium">{budget.usagePercentage != null ? `${budget.usagePercentage.toFixed(1)}%` : "N/A"}</span>
          </div>
        ))}
        {budgets.length > 2 && (
          <p className="text-xs text-muted-foreground">
            +{budgets.length - 2} more services
          </p>
        )}
      </div>
    </div>
  );
}

function ServiceBudgetItem({ budget }: { budget: Budget }) {
  const hasSpend = budget.actualSpend != null && budget.remainingAmount != null && budget.usagePercentage != null;
  const progressColor = budget.usagePercentage != null && budget.usagePercentage >= 100
    ? "bg-destructive"
    : budget.usagePercentage != null && budget.usagePercentage >= 80
    ? "bg-warning"
    : "bg-primary";

  return (
    <div className="space-y-2">
      <div className="flex items-center justify-between text-sm">
        <span className="font-medium">{budget.serviceName}</span>
        {hasSpend && (
          <span className="text-muted-foreground">{budget.usagePercentage!.toFixed(1)}%</span>
        )}
      </div>

      {hasSpend && (
        <>
          <div className="flex items-center justify-between text-xs text-muted-foreground">
            <span>{formatCurrency(budget.actualSpend!, budget.currency)} / {formatCurrency(budget.amount, budget.currency)}</span>
            <span>{budget.remainingAmount! < 0 ? "Over" : "Remaining"} {formatCurrency(Math.abs(budget.remainingAmount!), budget.currency)}</span>
          </div>

          <div
            className="h-1.5 overflow-hidden rounded-full bg-muted"
            role="progressbar"
            aria-label={`${budget.serviceName} budget utilization`}
            aria-valuemin={0}
            aria-valuemax={100}
            aria-valuenow={Math.max(0, Math.min(budget.usagePercentage!, 100))}
          >
            <div
              className={`h-full rounded-full ${progressColor}`}
              style={{ width: `${Math.min(Math.max(budget.usagePercentage!, 0), 100)}%` }}
            />
          </div>
        </>
      )}

      {!hasSpend && (
        <p className="text-xs text-muted-foreground">
          No billing data available
        </p>
      )}
    </div>
  );
}

function BudgetStatusBadge({ status }: { status: string }) {
  const statusConfig = {
    EXCEEDED: { label: "Exceeded", className: "bg-destructive/10 text-destructive" },
    CRITICAL: { label: "Critical", className: "bg-destructive/10 text-destructive" },
    WARNING: { label: "Warning", className: "bg-warning/10 text-warning" },
    NORMAL: { label: "On Track", className: "bg-success/10 text-success" },
    SAFE: { label: "On Track", className: "bg-success/10 text-success" },
    NO_BILLING_DATA: { label: "No Data", className: "bg-muted text-muted-foreground" },
  };

  const config = statusConfig[status as keyof typeof statusConfig] || statusConfig.NO_BILLING_DATA;

  return (
    <span className={`rounded-full px-2 py-0.5 text-[10px] font-semibold ${config.className}`}>
      {config.label}
    </span>
  );
}

function formatCurrency(value: number, currency: string) {
  return new Intl.NumberFormat("en-US", {
    style: "currency",
    currency,
    minimumFractionDigits: 2,
  }).format(value);
}

function BudgetDetailsDialog({
  budgets,
  overallBudget,
  serviceBudgets,
  isLoading,
  isError,
  onClose,
}: {
  budgets: Budget[];
  overallBudget: Budget | undefined;
  serviceBudgets: Budget[];
  isLoading: boolean;
  isError: boolean;
  onClose: () => void;
}) {
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/35 p-4" onClick={onClose}>
      <div
        aria-labelledby="budget-details-title"
        aria-modal="true"
        className="max-h-[min(720px,calc(100vh-2rem))] w-full max-w-3xl overflow-y-auto rounded-xl border border-border bg-card p-6 text-card-foreground shadow-2xl"
        role="dialog"
        onClick={(event) => event.stopPropagation()}
      >
        <div className="flex items-start justify-between gap-4">
          <div>
            <p className="text-xs font-medium uppercase tracking-wide text-primary">Budget & Alert</p>
            <h2 className="mt-1 text-xl font-semibold" id="budget-details-title">Budget Usage Details</h2>
          </div>
          <button
            aria-label="Close budget details"
            className="rounded-lg p-2 text-muted-foreground transition-colors hover:bg-muted hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
            onClick={onClose}
            type="button"
          >
            <X className="size-5" />
          </button>
        </div>

        {isLoading && (
          <div className="mt-6 flex items-center justify-center py-12 text-sm text-muted-foreground">
            <LoaderCircle className="mr-2 size-4 animate-spin" />
            Loading budget details...
          </div>
        )}

        {isError && (
          <p className="mt-6 py-12 text-center text-sm text-destructive">
            Unable to load budget details. Please verify the AWS connection.
          </p>
        )}

        {!isLoading && !isError && budgets.length > 0 && (
          <div className="mt-6 space-y-6">
            {overallBudget && (
              <div className="rounded-lg border border-border bg-surface-muted p-5">
                <div className="flex items-center justify-between mb-4">
                  <h3 className="font-semibold">Overall AWS Budget</h3>
                  <BudgetStatusBadge status={overallBudget.status} />
                </div>
                <OverallBudgetSummary budget={overallBudget} />
              </div>
            )}

            {serviceBudgets.length > 0 && (
              <div className="rounded-lg border border-border bg-surface-muted p-5">
                <h3 className="font-semibold mb-4">Service-wise Budgets</h3>
                <div className="space-y-4">
                  {serviceBudgets.map((budget) => (
                    <div key={budget.id} className="space-y-3">
                      <div className="flex items-center justify-between">
                        <span className="font-medium">{budget.serviceName}</span>
                        <BudgetStatusBadge status={budget.status} />
                      </div>
                      <ServiceBudgetItem budget={budget} />
                    </div>
                  ))}
                </div>
              </div>
            )}

            <div className="flex justify-end">
              <button
                className="rounded-lg border border-border px-4 py-2 text-sm font-medium hover:bg-muted"
                onClick={() => {
                  window.location.href = "/budgets";
                }}
                type="button"
              >
                Manage Budgets
              </button>
            </div>
          </div>
        )}

        {!isLoading && !isError && budgets.length === 0 && (
          <div className="mt-6 py-12 text-center">
            <p className="text-sm text-muted-foreground">
              No budget configured for this period.
            </p>
            <button
              className="mt-4 rounded-lg border border-border px-4 py-2 text-sm font-medium hover:bg-muted"
              onClick={() => {
                window.location.href = "/budgets";
              }}
              type="button"
            >
              Create Budget
            </button>
          </div>
        )}
      </div>
    </div>
  );
}
