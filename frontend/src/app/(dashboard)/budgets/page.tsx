"use client";

import { useState, type ReactNode } from "react";
import { AlertTriangle, Check, LoaderCircle, Plus, Trash2 } from "lucide-react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useCloudAccountStore } from "@/store/cloud-account-store";
import { useMonthSelectionStore } from "@/store/month-selection-store";
import { createBudget, deleteBudget, fetchBudgetAlerts, fetchBudgets, markBudgetAlertRead, type Budget, type BudgetAlert } from "@/services/aws-connection";
import { MonthSelector } from "@/components/month-selector";

export default function BudgetsPage() {
  const account = useCloudAccountStore((state) => state.account);
  const connectionToken = useCloudAccountStore((state) => state.connectionToken);
  const connectionStatus = useCloudAccountStore((state) => state.connectionStatus);
  const selectedMonth = useMonthSelectionStore((state) => state.selectedMonth);
  const setSelectedMonth = useMonthSelectionStore((state) => state.setSelectedMonth);
  const queryClient = useQueryClient();
  const [name, setName] = useState("");
  const [amount, setAmount] = useState("");
  const [currency, setCurrency] = useState("USD");
  const [thresholds, setThresholds] = useState("");
  const [formError, setFormError] = useState("");
  const enabled = Boolean(account && connectionToken);
  const budgets = useQuery({ queryKey: ["budgets", account?.accountId, selectedMonth], queryFn: () => fetchBudgets(connectionToken as string, selectedMonth), enabled, retry: false });
  const alerts = useQuery({ queryKey: ["budget-alerts", account?.accountId, connectionToken, selectedMonth], queryFn: () => fetchBudgetAlerts(connectionToken as string, selectedMonth), enabled: Boolean(connectionToken && account?.accountId), retry: false });
  const refresh = () => { void queryClient.invalidateQueries({ queryKey: ["budgets", account?.accountId, selectedMonth] }); void queryClient.invalidateQueries({ queryKey: ["budget-alerts", account?.accountId, connectionToken] }); void queryClient.invalidateQueries({ queryKey: ["aws-dashboard"] }); };

  if (connectionStatus === "error") {
    return <EmptyPanel title="AWS connection expired" detail="AWS connection expired. Please reconnect." />;
  }
  const create = useMutation({
    mutationFn: () => {
      const parsedAmount = Number(amount);
      const parsedThresholds = thresholds
        .split(",")
        .map((value) => Number(value.trim()))
        .filter((value) => Number.isFinite(value) && value > 0 && value <= 100);

      if (!name.trim()) {
        throw new Error("Budget name is required.");
      }

      if (!account?.accountId) {
        throw new Error("Select a connected AWS account.");
      }

      if (!Number.isFinite(parsedAmount) || parsedAmount <= 0) {
        throw new Error("Budget amount must be greater than 0.");
      }

      return createBudget(connectionToken as string, {
        name: name.trim(),
        awsAccountId: account.accountId,
        amount: parsedAmount,
        currency,
        period: "MONTHLY",
        thresholds: parsedThresholds,
        month: selectedMonth,
      });
    },
    onSuccess: () => { setName(""); setAmount(""); setThresholds(""); setFormError(""); refresh(); },
    onError: (error) => {
      setFormError(error instanceof Error ? error.message : "Unable to create budget.");
    },
  });
  const remove = useMutation({ mutationFn: (id: string) => deleteBudget(connectionToken as string, id), onSuccess: refresh });
  const markRead = useMutation({ mutationFn: (id: string) => markBudgetAlertRead(connectionToken as string, id), onSuccess: refresh });
  if (!account || !connectionToken) return <EmptyPanel title="Connect an AWS account first" detail="Budget monitoring uses CUR billing data from the connected account." />;
  const budget = budgets.data?.[0];
  return <main className="mx-auto max-w-6xl space-y-6">
    <header className="flex flex-col gap-4 md:flex-row md:items-center md:justify-between"><div><p className="text-sm font-medium text-primary">Optimization</p><h1 className="mt-1 text-2xl font-semibold">Budgets & Alerts</h1></div><div className="flex items-center gap-4"><MonthSelector value={selectedMonth} onChange={setSelectedMonth} /><span className="text-sm text-muted-foreground">Account {account.accountId}</span></div></header>
    <div className="grid gap-6 lg:grid-cols-[1.1fr_0.9fr]">
      <section className="rounded-xl border border-border bg-card p-5"><h2 className="text-lg font-semibold">Create budget</h2><form className="mt-5 space-y-4" onSubmit={(event) => { event.preventDefault(); create.mutate(); }}>
        <label className="block text-sm"><span className="mb-1.5 block text-muted-foreground">Budget name</span><input required value={name} onChange={(event) => setName(event.target.value)} className="w-full rounded-lg border border-input bg-background px-3 py-2" /></label>
        <div className="grid gap-4 sm:grid-cols-2"><label className="block text-sm"><span className="mb-1.5 block text-muted-foreground">Monthly amount</span><input required value={amount} onChange={(event) => setAmount(event.target.value)} className="w-full rounded-lg border border-input bg-background px-3 py-2" type="number" min="0.01" step="0.01" /></label><label className="block text-sm"><span className="mb-1.5 block text-muted-foreground">Currency</span><select value={currency} onChange={(event) => setCurrency(event.target.value)} className="w-full rounded-lg border border-input bg-background px-3 py-2"><option>USD</option><option>EUR</option><option>GBP</option></select></label></div>
        <label className="block text-sm"><span className="mb-1.5 block text-muted-foreground">Alert thresholds (%)</span><input value={thresholds} onChange={(event) => setThresholds(event.target.value)} placeholder="Leave blank for defaults" className="w-full rounded-lg border border-input bg-background px-3 py-2" /></label>
        {formError && <p className="text-sm text-destructive">{formError}</p>}
        <button disabled={create.isPending} className="inline-flex items-center gap-2 rounded-lg bg-primary px-4 py-2 text-sm font-medium text-primary-foreground disabled:opacity-60" type="submit"><Plus className="size-4" />{create.isPending ? "Creating..." : "Create budget"}</button>
      </form></section>
      <BudgetStatus budget={budget} loading={budgets.isLoading} error={budgets.isError} onDelete={(id) => remove.mutate(id)} />
    </div>
    <section className="rounded-xl border border-border bg-card p-5"><h2 className="text-lg font-semibold">Alerts</h2><AlertList alerts={alerts.data ?? []} loading={alerts.isLoading} error={alerts.isError} onRead={(id) => markRead.mutate(id)} /></section>
  </main>;
}

function BudgetStatus({ budget, loading, error, onDelete }: { budget?: Budget; loading: boolean; error: boolean; onDelete: (id: string) => void }) {
  if (loading) return <PanelMessage><LoaderCircle className="mr-2 size-4 animate-spin" />Loading budgets...</PanelMessage>;
  if (error) return <PanelMessage>Unable to load budget information.</PanelMessage>;
  if (!budget) return <PanelMessage><strong>No budget configured</strong><span className="mt-2">Create a budget to start monitoring your cloud spending.</span></PanelMessage>;
  const usage = budget.usagePercentage;
  const actualSpend = budget.actualSpend;
  const remainingAmount = budget.remainingAmount;
  const hasSpend = usage != null && actualSpend != null && remainingAmount != null;
  return <section className="rounded-xl border border-border bg-card p-5"><div className="flex items-center justify-between"><h2 className="text-lg font-semibold">Current budget status</h2><button aria-label={`Delete ${budget.name}`} className="p-2 text-muted-foreground hover:text-destructive" onClick={() => onDelete(budget.id)} type="button"><Trash2 className="size-4" /></button></div><div className="mt-5 space-y-4"><div className="rounded-lg bg-surface-muted p-4"><p className="text-sm text-muted-foreground">{budget.name}</p><p className="mt-2 text-3xl font-semibold">{hasSpend ? `${usage.toFixed(1)}%` : "Unavailable"}</p><p className="mt-1 text-sm text-muted-foreground">{hasSpend ? `${money(actualSpend, budget.currency)} / ${money(budget.amount, budget.currency)}` : "No CUR billing data is available for this month."}</p>{hasSpend && <div className="mt-4 h-2.5 overflow-hidden rounded-full bg-muted"><div className="h-full rounded-full bg-primary" style={{ width: `${Math.min(Math.max(usage, 0), 100)}%` }} /></div>}</div><Metric label={hasSpend && remainingAmount < 0 ? "Over budget" : "Remaining"} value={hasSpend ? money(Math.abs(remainingAmount), budget.currency) : "Unavailable"} /><Metric label="Status" value={hasSpend ? budget.status : "NO_BILLING_DATA"} /></div></section>;
}
function AlertList({ alerts, loading, error, onRead }: { alerts: BudgetAlert[]; loading: boolean; error: boolean; onRead: (id: string) => void }) {
  if (loading) return <PanelMessage><LoaderCircle className="mr-2 size-4 animate-spin" />Loading alerts...</PanelMessage>;
  if (error) return <PanelMessage>Unable to load budget information.</PanelMessage>;
  if (alerts.length === 0) return <PanelMessage>No alerts triggered for this billing period.</PanelMessage>;
  return <div className="mt-4 space-y-3">{alerts.map((alert) => <div className="flex items-start justify-between gap-3 rounded-lg border border-border p-3" key={alert.id}><div className="flex items-start gap-3"><AlertTriangle className="mt-0.5 size-4 text-warning" /><div><p className="font-medium">{alert.threshold}% Budget Alert</p><p className="text-sm text-muted-foreground">{alert.message}</p><p className="mt-1 text-xs text-muted-foreground">{money(alert.actualSpend, alert.currency)} / {money(alert.budgetAmount, alert.currency)} · {date(alert.createdAt)}</p></div></div>{alert.read ? <span className="inline-flex items-center gap-1 rounded-full bg-muted px-2 py-1 text-xs text-muted-foreground"><Check className="size-3" />Read</span> : <button className="rounded-full bg-warning/10 px-2 py-1 text-xs text-warning" onClick={() => onRead(alert.id)} type="button">Mark read</button>}</div>)}</div>;
}
function Metric({ label, value }: { label: string; value: string }) { return <div className="flex items-center justify-between rounded-lg border border-border p-3 text-sm"><span className="text-muted-foreground">{label}</span><span className="font-medium">{value}</span></div>; }
function PanelMessage({ children }: { children: ReactNode }) { return <div className="flex min-h-64 flex-col items-center justify-center rounded-xl border border-border bg-card p-5 text-center text-sm text-muted-foreground">{children}</div>; }
function EmptyPanel({ title, detail }: { title: string; detail: string }) { return <div className="rounded-xl border border-border bg-card p-8 text-center"><p className="text-lg font-semibold">{title}</p><p className="mt-2 text-sm text-muted-foreground">{detail}</p></div>; }
function money(value: number | null, currency: string) { return value == null ? "Unavailable" : new Intl.NumberFormat(undefined, { style: "currency", currency, minimumFractionDigits: 2 }).format(value); }
function date(value: string) { return new Intl.DateTimeFormat(undefined, { dateStyle: "medium", timeStyle: "short" }).format(new Date(value)); }
