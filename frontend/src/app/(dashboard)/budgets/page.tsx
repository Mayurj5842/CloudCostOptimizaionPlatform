"use client";

import { useState, type FormEvent, type ReactNode } from "react";
import { AlertTriangle, Check, LoaderCircle, Plus, Trash2 } from "lucide-react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useCloudAccountStore } from "@/store/cloud-account-store";
import { useMonthSelectionStore } from "@/store/month-selection-store";
import {
  createBudget,
  deleteBudget,
  fetchBudgetAlertsFiltered,
  fetchBudgets,
  fetchBudgetServices,
  markBudgetAlertRead,
  updateBudget,
  type Budget,
  type BudgetAlert,
} from "@/services/aws-connection";
import { MonthSelector } from "@/components/month-selector";
import {
  BUDGET_TONE_BADGE,
  BUDGET_TONE_BAR,
  BUDGET_TONE_LABEL,
  BUDGET_TONE_TEXT,
  budgetTone,
  type BudgetTone,
} from "@/features/budgets/budget-metrics";
import { BudgetOverview } from "@/features/budgets/budget-overview";

export default function BudgetsPage() {
  const account = useCloudAccountStore((state) => state.account);
  const connectionToken = useCloudAccountStore((state) => state.connectionToken);
  const connectionStatus = useCloudAccountStore((state) => state.connectionStatus);
  const selectedMonth = useMonthSelectionStore((state) => state.selectedMonth);
  const setSelectedMonth = useMonthSelectionStore((state) => state.setSelectedMonth);
  const queryClient = useQueryClient();
  const [editingBudgetId, setEditingBudgetId] = useState<string | null>(null);
  const [name, setName] = useState("");
  const [amount, setAmount] = useState("");
  const [currency, setCurrency] = useState("USD");
  const [serviceName, setServiceName] = useState("");
  const [thresholds, setThresholds] = useState("50, 75, 90, 100");
  const [spikeThreshold, setSpikeThreshold] = useState("50");
  const [formEnabled, setFormEnabled] = useState(true);
  const [formError, setFormError] = useState("");
  const [severityFilter, setSeverityFilter] = useState("");
  const [typeFilter, setTypeFilter] = useState("");
  const [fromFilter, setFromFilter] = useState("");
  const [toFilter, setToFilter] = useState("");
  const enabled = Boolean(account && connectionToken);
  const alertFilters = { month: selectedMonth, severity: severityFilter, type: typeFilter, from: fromFilter, to: toFilter };
  const budgets = useQuery({ queryKey: ["budgets", account?.accountId, selectedMonth], queryFn: () => fetchBudgets(connectionToken as string, selectedMonth), enabled, retry: false });
  const services = useQuery({ queryKey: ["budget-services", account?.accountId, selectedMonth], queryFn: () => fetchBudgetServices(connectionToken as string, selectedMonth), enabled, retry: false });
  const alerts = useQuery({ queryKey: ["budget-alerts", account?.accountId, connectionToken, alertFilters], queryFn: () => fetchBudgetAlertsFiltered(connectionToken as string, alertFilters), enabled, retry: false });
  const refresh = () => {
    void queryClient.invalidateQueries({ queryKey: ["budgets", account?.accountId, selectedMonth] });
    void queryClient.invalidateQueries({ queryKey: ["budget-alerts", account?.accountId, connectionToken] });
    void queryClient.invalidateQueries({ queryKey: ["aws-dashboard"] });
  };

  const resetForm = () => {
    setEditingBudgetId(null);
    setName("");
    setAmount("");
    setCurrency("USD");
    setServiceName("");
    setThresholds("50, 75, 90, 100");
    setSpikeThreshold("50");
    setFormEnabled(true);
    setFormError("");
  };

  const save = useMutation({
    mutationFn: () => {
      const parsedAmount = Number(amount);
      const parsedSpikeThreshold = Number(spikeThreshold);
      const parsedThresholds = thresholds.split(",").map((value) => Number(value.trim()));
      if (!name.trim()) throw new Error("Budget name is required.");
      if (!account?.accountId) throw new Error("Select a connected AWS account.");
      if (!Number.isFinite(parsedAmount) || parsedAmount <= 0) throw new Error("Budget amount must be greater than 0.");
      if (!Number.isInteger(parsedSpikeThreshold) || parsedSpikeThreshold < 1 || parsedSpikeThreshold > 500) {
        throw new Error("Spending spike threshold must be between 1% and 500%.");
      }
      if (parsedThresholds.some((value) => !Number.isInteger(value) || value < 1 || value > 100)) {
        throw new Error("Budget thresholds must be whole percentages between 1 and 100.");
      }
      const request = {
        name: name.trim(),
        awsAccountId: account.accountId,
        amount: parsedAmount,
        currency,
        period: "MONTHLY",
        thresholds: [...new Set(parsedThresholds)].sort((left, right) => left - right),
        month: selectedMonth,
        serviceName,
        enabled: formEnabled,
        spikeThresholdPercent: parsedSpikeThreshold,
      };
      return editingBudgetId
        ? updateBudget(connectionToken as string, editingBudgetId, request)
        : createBudget(connectionToken as string, request);
    },
    onSuccess: () => { resetForm(); refresh(); },
    onError: (error) => setFormError(error instanceof Error ? error.message : "Unable to save budget."),
  });
  const remove = useMutation({ mutationFn: (id: string) => deleteBudget(connectionToken as string, id), onSuccess: refresh });
  const toggleBudget = useMutation({
    mutationFn: (budget: Budget) => updateBudget(connectionToken as string, budget.id, {
      name: budget.name,
      awsAccountId: budget.awsAccountId,
      amount: budget.amount,
      currency: budget.currency,
      period: budget.period,
      thresholds: budget.thresholds,
      month: selectedMonth,
      serviceName: budget.serviceName,
      enabled: !budget.enabled,
      spikeThresholdPercent: budget.spikeThresholdPercent,
    }),
    onSuccess: refresh,
  });
  const markRead = useMutation({ mutationFn: (id: string) => markBudgetAlertRead(connectionToken as string, id), onSuccess: refresh });

  if (connectionStatus === "error") return <EmptyPanel title="AWS connection expired" detail="AWS connection expired. Please reconnect." />;
  if (!account || !connectionToken) return <EmptyPanel title="Connect an AWS account first" detail="Budget monitoring uses persisted billing data from the connected account." />;

  const editBudget = (budget: Budget) => {
    setEditingBudgetId(budget.id);
    setName(budget.name);
    setAmount(String(budget.amount));
    setCurrency(budget.currency);
    setServiceName(budget.serviceName ?? "");
    setThresholds(budget.thresholds.join(", "));
    setSpikeThreshold(String(budget.spikeThresholdPercent));
    setFormEnabled(budget.enabled);
    setFormError("");
  };

  return <main className="mx-auto max-w-6xl space-y-6">
    <header className="flex flex-col gap-4 md:flex-row md:items-center md:justify-between">
      <div><p className="text-sm font-medium text-primary">Optimization</p><h1 className="mt-1 text-2xl font-semibold">Budgets & Alerts</h1></div>
      <div className="flex items-center gap-4"><MonthSelector value={selectedMonth} onChange={setSelectedMonth} /><span className="text-sm text-muted-foreground">Account {account.accountId}</span></div>
    </header>
    {budgets.data?.length ? <BudgetOverview budgets={budgets.data} month={selectedMonth} /> : null}
    <div className="grid gap-6 lg:grid-cols-[1.1fr_0.9fr]">
      <section className="rounded-xl border border-border bg-card p-5">
        <h2 className="text-lg font-semibold">{editingBudgetId ? "Edit budget" : "Create budget"}</h2>
        <form className="mt-5 space-y-4" onSubmit={(event: FormEvent) => { event.preventDefault(); save.mutate(); }}>
          <label className="block text-sm"><span className="mb-1.5 block text-muted-foreground">Budget name</span><input required maxLength={200} value={name} onChange={(event) => setName(event.target.value)} className="w-full rounded-lg border border-input bg-background px-3 py-2" /></label>
          <div className="grid gap-4 sm:grid-cols-2">
            <label className="block text-sm"><span className="mb-1.5 block text-muted-foreground">Monthly amount</span><input required value={amount} onChange={(event) => setAmount(event.target.value)} className="w-full rounded-lg border border-input bg-background px-3 py-2" type="number" min="0.01" step="0.01" /></label>
            <label className="block text-sm"><span className="mb-1.5 block text-muted-foreground">Currency</span><select value={currency} onChange={(event) => setCurrency(event.target.value)} className="w-full rounded-lg border border-input bg-background px-3 py-2"><option>USD</option><option>EUR</option><option>GBP</option></select></label>
          </div>
          <label className="block text-sm"><span className="mb-1.5 block text-muted-foreground">AWS service</span><select value={serviceName} onChange={(event) => setServiceName(event.target.value)} className="w-full rounded-lg border border-input bg-background px-3 py-2"><option value="">All services</option>{serviceName && !services.data?.includes(serviceName) && <option value={serviceName}>{serviceName}</option>}{services.data?.map((service) => <option key={service}>{service}</option>)}</select></label>
          <label className="block text-sm"><span className="mb-1.5 block text-muted-foreground">Alert thresholds (%)</span><input required value={thresholds} onChange={(event) => setThresholds(event.target.value)} placeholder="50, 75, 90, 100" className="w-full rounded-lg border border-input bg-background px-3 py-2" /></label>
          <label className="block text-sm"><span className="mb-1.5 block text-muted-foreground">Spending spike rule (%)</span><input required value={spikeThreshold} onChange={(event) => setSpikeThreshold(event.target.value)} className="w-full rounded-lg border border-input bg-background px-3 py-2" type="number" min="1" max="500" step="1" /></label>
          <label className="flex items-center gap-2 text-sm"><input type="checkbox" checked={formEnabled} onChange={(event) => setFormEnabled(event.target.checked)} className="size-4 accent-primary" />Enabled</label>
          {formError && <p className="text-sm text-destructive">{formError}</p>}
          <div className="flex flex-wrap gap-2"><button disabled={save.isPending} className="inline-flex items-center gap-2 rounded-lg bg-primary px-4 py-2 text-sm font-medium text-primary-foreground disabled:opacity-60" type="submit"><Plus className="size-4" />{save.isPending ? "Saving..." : editingBudgetId ? "Save changes" : "Create budget"}</button>{editingBudgetId && <button className="rounded-lg border border-border px-4 py-2 text-sm" onClick={resetForm} type="button">Cancel</button>}</div>
        </form>
      </section>
      <section className="space-y-4">
        <h2 className="text-lg font-semibold">Budget overview</h2>
        {budgets.isLoading ? <PanelMessage><LoaderCircle className="mr-2 size-4 animate-spin" />Loading budgets...</PanelMessage>
          : budgets.isError ? <PanelMessage>Unable to load budget information.</PanelMessage>
            : budgets.data?.length ? budgets.data.map((budget) => <BudgetCard key={budget.id} budget={budget} onEdit={editBudget} onToggle={(item) => toggleBudget.mutate(item)} onDelete={(id) => remove.mutate(id)} />)
              : <PanelMessage><strong>No budgets for {selectedMonth}</strong><span className="mt-2">Create a budget to monitor persisted AWS billing for this period.</span></PanelMessage>}
      </section>
    </div>
    {budgets.data?.some((budget) => budget.serviceName) ? <ServiceBudgetProgress budgets={budgets.data} month={selectedMonth} /> : null}
    <section className="rounded-xl border border-border bg-card p-5">
      <div className="flex flex-col gap-4 lg:flex-row lg:items-end lg:justify-between"><div><h2 className="text-lg font-semibold">Alert history</h2><p className="mt-1 text-sm text-muted-foreground">Budget threshold and spending spike events for {selectedMonth}.</p><AlertSummary alerts={alerts.data ?? []} /></div>
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
          <label className="text-xs text-muted-foreground">Severity<select value={severityFilter} onChange={(event) => setSeverityFilter(event.target.value)} className="mt-1 block h-9 w-full rounded-lg border border-input bg-background px-2 text-sm text-foreground"><option value="">All severities</option><option>INFO</option><option>WARNING</option><option>CRITICAL</option></select></label>
          <label className="text-xs text-muted-foreground">Alert type<select value={typeFilter} onChange={(event) => setTypeFilter(event.target.value)} className="mt-1 block h-9 w-full rounded-lg border border-input bg-background px-2 text-sm text-foreground"><option value="">All types</option><option value="BUDGET_THRESHOLD">Budget threshold</option><option value="BUDGET_EXCEEDED">Budget exceeded</option><option value="SPENDING_SPIKE">Spending spike</option></select></label>
          <label className="text-xs text-muted-foreground">From<input type="date" value={fromFilter} onChange={(event) => setFromFilter(event.target.value)} className="mt-1 block h-9 w-full rounded-lg border border-input bg-background px-2 text-sm text-foreground" /></label>
          <label className="text-xs text-muted-foreground">To<input type="date" value={toFilter} onChange={(event) => setToFilter(event.target.value)} className="mt-1 block h-9 w-full rounded-lg border border-input bg-background px-2 text-sm text-foreground" /></label>
        </div>
      </div>
      <AlertList alerts={alerts.data ?? []} loading={alerts.isLoading} error={alerts.isError} onRead={(id) => markRead.mutate(id)} />
    </section>
  </main>;
}

function ServiceBudgetProgress({ budgets, month }: { budgets: Budget[]; month: string }) {
  const serviceBudgets = budgets.filter((budget) => budget.serviceName);
  if (serviceBudgets.length === 0) return null;
  return <section className="rounded-xl border border-border bg-card p-5">
    <div className="flex flex-wrap items-end justify-between gap-2"><div><h2 className="text-lg font-semibold">Service-wise budget usage</h2><p className="mt-1 text-sm text-muted-foreground">Actual spend against configured service budgets for {month}.</p></div><p className="text-xs text-muted-foreground">{serviceBudgets.length} service budget{serviceBudgets.length === 1 ? "" : "s"}</p></div>
    <div className="mt-5 space-y-5">{serviceBudgets.map((budget) => <ServiceBudgetRow key={budget.id} budget={budget} />)}</div>
  </section>;
}

function ServiceBudgetRow({ budget }: { budget: Budget }) {
  const usage = budget.usagePercentage;
  const hasSpend = usage != null && budget.actualSpend != null && budget.remainingAmount != null;
  const tone = budgetTone(usage, budget.thresholds);
  return <div>
    <div className="flex flex-wrap items-center justify-between gap-2 text-sm"><span className="font-medium">{budget.serviceName}</span><span className={`rounded-full px-2 py-0.5 text-[10px] font-semibold ${BUDGET_TONE_BADGE[tone]}`}>{BUDGET_TONE_LABEL[tone]}</span></div>
    {hasSpend ? <>
      <div className="mt-2 flex items-center justify-between text-xs text-muted-foreground"><span>{money(budget.actualSpend, budget.currency)} of {money(budget.amount, budget.currency)}</span><span className={BUDGET_TONE_TEXT[tone]}>{usage.toFixed(1)}%</span></div>
      <div className="mt-1.5 h-2.5 overflow-hidden rounded-full bg-muted" role="progressbar" aria-label={`${budget.serviceName} budget utilization`} aria-valuemin={0} aria-valuemax={100} aria-valuenow={Math.max(0, Math.min(usage, 100))}><div className={`h-full rounded-full ${BUDGET_TONE_BAR[tone]}`} style={{ width: `${Math.min(Math.max(usage, 0), 100)}%` }} /></div>
      <p className="mt-1 text-xs text-muted-foreground">{budget.remainingAmount! < 0 ? "Over by" : "Remaining"} {money(Math.abs(budget.remainingAmount!), budget.currency)}</p>
    </> : <p className="mt-2 text-xs text-muted-foreground">No billing data is available for this service in the selected period.</p>}
  </div>;
}

function AlertSummary({ alerts }: { alerts: BudgetAlert[] }) {
  if (alerts.length === 0) return null;
  const unread = alerts.filter((alert) => !alert.read).length;
  const critical = alerts.filter((alert) => alert.severity === "CRITICAL").length;
  return <div className="mt-2 flex flex-wrap items-center gap-2 text-xs">
    <span className="rounded-full bg-muted px-2 py-0.5 font-medium text-muted-foreground">{alerts.length} total</span>
    {unread > 0 && <span className="rounded-full bg-warning/10 px-2 py-0.5 font-medium text-warning">{unread} unread</span>}
    {critical > 0 && <span className="rounded-full bg-destructive/10 px-2 py-0.5 font-medium text-destructive">{critical} critical</span>}
  </div>;
}

function BudgetCard({ budget, onEdit, onToggle, onDelete }: { budget: Budget; onEdit: (budget: Budget) => void; onToggle: (budget: Budget) => void; onDelete: (id: string) => void }) {
  const usage = budget.usagePercentage;
  const hasSpend = usage != null && budget.actualSpend != null && budget.remainingAmount != null;
  const tone = budgetTone(usage, budget.thresholds);
  const progressColor = BUDGET_TONE_BAR[tone];
  const variance = budget.projectedVariance;
  return <article className="rounded-xl border border-border bg-card p-5">
    <div className="flex flex-wrap items-start justify-between gap-3"><div><div className="flex flex-wrap items-center gap-2"><h3 className="font-semibold">{budget.name}</h3><span className={`rounded-full px-2 py-0.5 text-[10px] font-semibold ${BUDGET_TONE_BADGE[tone]}`}>{BUDGET_TONE_LABEL[tone]}</span></div><p className="mt-1 text-xs text-muted-foreground">{budget.serviceName ?? "All AWS services"} · {budget.enabled ? "Enabled" : "Disabled"}</p></div><div className="flex gap-1"><button className="rounded-md border border-border px-2.5 py-1.5 text-xs" onClick={() => onEdit(budget)} type="button">Edit</button><button className="rounded-md border border-border px-2.5 py-1.5 text-xs" onClick={() => onToggle(budget)} type="button">{budget.enabled ? "Disable" : "Enable"}</button><button aria-label={`Delete ${budget.name}`} className="rounded-md p-2 text-muted-foreground hover:text-destructive" onClick={() => onDelete(budget.id)} type="button"><Trash2 className="size-4" /></button></div></div>
    <div className="mt-4 rounded-lg bg-surface-muted p-4"><div className="flex items-end justify-between gap-3"><div><p className="text-xs text-muted-foreground">Actual spending</p><p className="mt-1 text-lg font-semibold">{money(budget.actualSpend, budget.currency)}</p></div><div className="text-right"><p className="text-xs text-muted-foreground">Budget</p><p className="mt-1 font-medium">{money(budget.amount, budget.currency)}</p></div></div>
      {hasSpend ? <><div className="mt-4 flex items-center justify-between text-sm"><span className={`font-medium ${BUDGET_TONE_TEXT[tone]}`}>{usage.toFixed(1)}% used</span><span className="text-muted-foreground">{budget.remainingAmount! < 0 ? "Over" : "Remaining"} {money(Math.abs(budget.remainingAmount!), budget.currency)}</span></div><div className="mt-2 h-2.5 overflow-hidden rounded-full bg-muted" role="progressbar" aria-label={`${budget.name} budget utilization`} aria-valuemin={0} aria-valuemax={100} aria-valuenow={Math.max(0, Math.min(usage, 100))}><div className={`h-full rounded-full ${progressColor}`} style={{ width: `${Math.min(Math.max(usage, 0), 100)}%` }} /></div></> : <p className="mt-3 text-sm text-muted-foreground">No billing data is available for {budget.period.toLowerCase()} {budget.serviceName ? `for ${budget.serviceName}` : ""}.</p>}
    </div>
    <div className="mt-3 grid gap-3 sm:grid-cols-2"><MetricBadge label="Status" tone={tone} /><Metric label="Alert thresholds" value={`${budget.thresholds.join("%, ")}%`} /><Metric label="Spike rule" value={`${budget.spikeThresholdPercent}% vs prior month`} /></div>
    <div className="mt-3 rounded-lg border border-border p-3"><div className="flex items-center justify-between gap-3"><p className="text-sm font-medium">Projected month-end</p><span className="text-[10px] font-medium uppercase tracking-wide text-muted-foreground">Estimate</span></div>{budget.forecastAvailable ? <><p className="mt-1 text-xl font-semibold">{money(budget.forecastSpend, budget.currency)}</p><p className={`mt-1 text-sm ${variance != null && variance > 0 ? "font-medium text-destructive" : "text-muted-foreground"}`}>{variance != null && variance > 0 ? `Projected overrun ${money(variance, budget.currency)}` : variance != null && variance < 0 ? `Projected remaining ${money(Math.abs(variance), budget.currency)}` : "Projected to meet budget"}</p></> : <p className="mt-1 text-sm text-muted-foreground">{budget.forecastMessage}</p>}</div>
  </article>;
}

function AlertList({ alerts, loading, error, onRead }: { alerts: BudgetAlert[]; loading: boolean; error: boolean; onRead: (id: string) => void }) {
  if (loading) return <PanelMessage><LoaderCircle className="mr-2 size-4 animate-spin" />Loading alerts...</PanelMessage>;
  if (error) return <PanelMessage>Unable to load alert history.</PanelMessage>;
  if (alerts.length === 0) return <PanelMessage>No alerts match the selected period and filters.</PanelMessage>;
  return <div className="mt-4 space-y-3">{alerts.map((alert) => <article className="relative flex items-start justify-between gap-3 overflow-hidden rounded-lg border border-border p-3 pl-4" key={alert.id}>
    <span aria-hidden className={`absolute inset-y-0 left-0 w-1 ${severityBar(alert.severity)}`} />
    <div className="flex min-w-0 items-start gap-3"><AlertTriangle className={`mt-0.5 size-4 shrink-0 ${severityColor(alert.severity)}`} /><div className="min-w-0"><div className="flex flex-wrap items-center gap-2"><p className="font-medium">{alert.title}</p><span className={`rounded-full px-2 py-0.5 text-[10px] font-semibold ${severityBadge(alert.severity)}`}>{alert.severity}</span></div><p className="mt-1 text-sm text-muted-foreground">{alert.message}</p><p className="mt-1 text-xs text-muted-foreground">{alert.budgetName || alert.serviceName ? `${alert.budgetName ?? "Budget"}${alert.serviceName ? ` · ${alert.serviceName}` : ""} · ` : ""}{money(alert.actualSpend, alert.currency)}{alert.budgetAmount != null ? ` / ${money(alert.budgetAmount, alert.currency)}` : ""} · {date(alert.createdAt)}</p>{alert.comparisonPeriod && <p className="mt-1 text-xs text-muted-foreground">Compared with {alert.comparisonPeriod}{alert.percentageChange != null ? ` · +${alert.percentageChange}%` : ""}</p>}{alert.threshold != null && <p className="mt-1 text-xs text-muted-foreground">Configured threshold: {alert.threshold}%</p>}</div></div>
    {alert.read ? <span className="inline-flex shrink-0 items-center gap-1 rounded-full bg-muted px-2 py-1 text-xs text-muted-foreground"><Check className="size-3" />Read</span> : <button className="shrink-0 rounded-full bg-warning/10 px-2 py-1 text-xs text-warning" onClick={() => onRead(alert.id)} type="button">Mark read</button>}
  </article>)}</div>;
}

function severityColor(severity: BudgetAlert["severity"]) { return severity === "CRITICAL" ? "text-destructive" : severity === "WARNING" ? "text-warning" : "text-info"; }
function severityBadge(severity: BudgetAlert["severity"]) { return severity === "CRITICAL" ? "bg-destructive/10 text-destructive" : severity === "WARNING" ? "bg-warning/10 text-warning" : "bg-info/10 text-info"; }
function severityBar(severity: BudgetAlert["severity"]) { return severity === "CRITICAL" ? "bg-destructive" : severity === "WARNING" ? "bg-warning" : "bg-info"; }
function Metric({ label, value }: { label: string; value: string }) { return <div className="flex items-center justify-between rounded-lg border border-border p-3 text-sm"><span className="text-muted-foreground">{label}</span><span className="font-medium">{value}</span></div>; }
function MetricBadge({ label, tone }: { label: string; tone: BudgetTone }) { return <div className="flex items-center justify-between rounded-lg border border-border p-3 text-sm"><span className="text-muted-foreground">{label}</span><span className={`rounded-full px-2 py-0.5 text-[10px] font-semibold ${BUDGET_TONE_BADGE[tone]}`}>{BUDGET_TONE_LABEL[tone]}</span></div>; }
function PanelMessage({ children }: { children: ReactNode }) { return <div className="flex min-h-40 flex-col items-center justify-center rounded-xl border border-border bg-card p-5 text-center text-sm text-muted-foreground">{children}</div>; }
function EmptyPanel({ title, detail }: { title: string; detail: string }) { return <div className="rounded-xl border border-border bg-card p-8 text-center"><p className="text-lg font-semibold">{title}</p><p className="mt-2 text-sm text-muted-foreground">{detail}</p></div>; }
function money(value: number | null, currency: string | null) { return value == null || currency == null ? "Unavailable" : new Intl.NumberFormat(undefined, { style: "currency", currency, minimumFractionDigits: 2 }).format(value); }
function date(value: string) { return new Intl.DateTimeFormat(undefined, { dateStyle: "medium", timeStyle: "short" }).format(new Date(value)); }
