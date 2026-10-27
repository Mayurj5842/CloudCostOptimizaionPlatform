"use client";

import { X } from "lucide-react";

import type { AwsDashboardResponse } from "@/services/aws-connection";

export const FORECAST_METHOD_LABEL: Record<string, string> = {
  RUN_RATE_PROJECTION: "Run-rate projection",
  FINAL_ACTUAL: "Final actual",
  PRIOR_MONTH_RUN_RATE: "Prior month run rate",
  NO_BILLING_DATA: "No billing data",
};

export function ForecastDialog({ forecast, onClose }: { forecast: NonNullable<AwsDashboardResponse["forecast"]>; onClose: () => void }) {
  const hasValue = forecast.value != null;
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/35 p-4" onClick={onClose}>
      <div
        aria-labelledby="forecast-title"
        aria-modal="true"
        className="max-h-[min(720px,calc(100vh-2rem))] w-full max-w-xl overflow-y-auto rounded-xl border border-border bg-card p-6 text-card-foreground shadow-2xl"
        role="dialog"
        onClick={(event) => event.stopPropagation()}
      >
        <div className="flex items-start justify-between gap-4">
          <div>
            <p className="text-xs font-medium uppercase tracking-wide text-primary">Forecast</p>
            <h2 className="mt-1 text-xl font-semibold" id="forecast-title">Total Forecasted Cost <span className="text-muted-foreground">({forecast.period})</span></h2>
          </div>
          <button aria-label="Close forecast details" className="rounded-lg p-2 text-muted-foreground transition-colors hover:bg-muted hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring" onClick={onClose} type="button"><X className="size-5" /></button>
        </div>

        {hasValue ? <div className="mt-6 space-y-5">
          <div className="rounded-lg border border-border bg-muted/30 p-4">
            <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">Projected total</p>
            <p className="mt-1 text-2xl font-semibold tracking-tight">{formatAmount(forecast.value, forecast.currency)}</p>
            <p className="mt-1 text-xs text-muted-foreground">{FORECAST_METHOD_LABEL[forecast.method] ?? forecast.method}</p>
          </div>

          <dl className="space-y-3">
            <ForecastRow label="Actual to date" value={formatAmount(forecast.actualToDate, forecast.currency)} />
            <ForecastRow label="Daily run rate" value={forecast.dailyRunRate == null ? "Not available" : formatAmount(forecast.dailyRunRate, forecast.currency, 6)} />
            <ForecastRow label="Projected remaining" value={formatAmount(forecast.projectedRemaining, forecast.currency)} />
            <ForecastRow label="Previous month" value={forecast.previousMonth == null ? "No previous-month data" : formatAmount(forecast.previousMonth, forecast.currency)} />
            <ForecastRow label="Change vs previous month" value={forecast.percentageChange == null ? "No comparable previous month" : `${forecast.percentageChange > 0 ? "+" : ""}${forecast.percentageChange}%`} />
            <ForecastRow label="Days observed" value={`${forecast.daysObserved} of ${forecast.daysInMonth}`} />
            <ForecastRow label="Days remaining" value={String(forecast.remainingDays)} />
          </dl>

          <p className="text-xs text-muted-foreground">The projection spreads the stored billing rows across the days they cover and extends the observed daily run rate across the remaining days of the month.</p>
        </div> : <p className="py-12 text-center text-sm text-muted-foreground">No billing data is available for {forecast.period}.</p>}
      </div>
    </div>
  );
}

function ForecastRow({ label, value }: { label: string; value: string }) {
  return <div className="flex items-center justify-between gap-4 border-b border-border pb-2 last:border-b-0 last:pb-0"><dt className="text-sm text-muted-foreground">{label}</dt><dd className="text-sm font-medium">{value}</dd></div>;
}

function formatAmount(value: number | null, currency: string, maximumFractionDigits = 2) {
  if (value == null) return "Not available";
  const magnitude = Math.abs(value);
  const digits = magnitude > 0 && magnitude < 1 ? Math.max(maximumFractionDigits, 4) : maximumFractionDigits;
  return new Intl.NumberFormat("en-US", {
    style: "currency",
    currency,
    minimumFractionDigits: Math.min(digits, 2),
    maximumFractionDigits: digits,
  }).format(value);
}
