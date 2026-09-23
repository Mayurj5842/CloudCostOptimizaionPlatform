"use client";

import { useMutation, useQuery } from "@tanstack/react-query";
import { AlertTriangle, CheckCircle2, LoaderCircle, RefreshCw, ShieldCheck, Unplug } from "lucide-react";
import { useEffect, useState } from "react";
import { useTheme } from "@/providers/theme-provider";

import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { fetchAwsDashboard, disconnectAwsAccount, fetchSystemDiagnostics, testAwsConnection } from "@/services/aws-connection";
import { getCurrentUserProfile } from "@/lib/current-user";
import { useCloudAccountStore } from "@/store/cloud-account-store";

export default function SettingsPage() {
  const { account, connectionToken, connectionStatus, disconnect } = useCloudAccountStore();
  const { theme, setTheme } = useTheme();
  const [notice, setNotice] = useState<string | null>(null);
  const [notifications, setNotifications] = useState(true);
  const user = getCurrentUserProfile();
  const dashboard = useQuery({ queryKey: ["settings-dashboard", account?.accountId, connectionToken], queryFn: () => fetchAwsDashboard(connectionToken as string), enabled: Boolean(connectionToken), retry: false });
  const connectionTest = useMutation({ mutationFn: () => testAwsConnection(connectionToken as string), onSuccess: (result) => setNotice(result.message), onError: () => setNotice("Connection test failed. Your AWS state was not changed.") });
  const diagnostics = useQuery({ queryKey: ["system-diagnostics", account?.accountId, connectionToken], queryFn: () => fetchSystemDiagnostics(connectionToken as string), enabled: false, retry: false });
  const diagnosticsPassed = diagnostics.data?.checks.every((check) => check.status === "HEALTHY");
  const diagnosticsFailed = diagnostics.data?.checks.some((check) => check.status === "FAILED" || check.status === "UNAVAILABLE");
  const diagnosticsMessage = diagnostics.isError
    ? (diagnostics.error instanceof Error ? diagnostics.error.message : "Diagnostics could not be completed. Verify the AWS connection and try again.")
    : diagnosticsPassed
      ? "All AWS, session, permission, database, and backend checks passed."
      : diagnosticsFailed
        ? "Diagnostics completed with failed checks. Review the details below."
        : diagnostics.data
          ? "Diagnostics completed with warnings. Review the details below."
          : null;

  useEffect(() => {
    const timer = window.setTimeout(() => setNotifications(window.localStorage.getItem("cloudcost-notifications") !== "false"), 0);
    return () => window.clearTimeout(timer);
  }, []);

  const handleDisconnect = async () => {
    if (!window.confirm("Disconnect this AWS account?")) return;
    try {
      await disconnectAwsAccount(connectionToken as string);
      disconnect();
      setNotice("AWS account disconnected.");
    } catch {
      setNotice("Disconnect failed. AWS connection remains unchanged.");
    }
  };

  const maskedAccountId = account?.accountId ? `******${account.accountId.slice(-4)}` : "Unavailable";
  const lastSync = dashboard.data?.billing.lastUpdated ? new Date(dashboard.data.billing.lastUpdated).toLocaleString() : "Unavailable";
  const setNotificationPreference = (value: boolean) => {
    setNotifications(value);
    window.localStorage.setItem("cloudcost-notifications", String(value));
  };

  return <div className="mx-auto max-w-6xl space-y-6">
    <header><p className="text-sm font-medium text-primary">SYSTEM</p><h1 className="mt-1 text-2xl font-semibold tracking-tight sm:text-3xl">Settings</h1><p className="mt-2 text-sm text-muted-foreground">Manage workspace preferences and inspect the active AWS session.</p></header>
    {notice && <div className="rounded-lg border border-border bg-muted/40 px-4 py-3 text-sm">{notice}</div>}
    <div className="grid gap-6 lg:grid-cols-2">
      <Card><CardHeader><CardTitle>AWS Connection</CardTitle></CardHeader><CardContent className="space-y-3"><Info label="Account alias" value={account?.accountAlias ?? "No AWS account connected"} /><Info label="Account ID" value={maskedAccountId} /><Info label="Region" value={account?.region ?? "Unavailable"} /><Info label="Status" value={account ? connectionStatus.toUpperCase() : "DISCONNECTED"} /><Info label="Last sync" value={lastSync} /><div className="flex flex-wrap gap-2 pt-2"><Button disabled={!connectionToken || connectionTest.isPending} onClick={() => connectionTest.mutate()} variant="outline"><RefreshCw className="size-4" />Test Connection</Button><Button disabled={!account} onClick={handleDisconnect} variant="destructive"><Unplug className="size-4" />Disconnect</Button></div></CardContent></Card>
      <Card><CardHeader><CardTitle>Profile</CardTitle></CardHeader><CardContent className="space-y-3"><Info label="Name" value={user.name ?? "Unavailable"} /><Info label="Email" value={user.email ?? "Unavailable"} /><Info label="Role" value={user.role ?? "Unavailable"} /><p className="flex gap-2 rounded-lg border border-border bg-muted/30 p-3 text-sm"><ShieldCheck className="size-4 shrink-0 text-primary" />Credentials and session tokens are never displayed.</p></CardContent></Card>
      <Card><CardHeader><CardTitle>Billing / CUR 2.0</CardTitle></CardHeader><CardContent className="space-y-3"><Info label="Data source" value={dashboard.data?.billing.dataSource ?? "Unavailable"} /><Info label="Status" value={dashboard.data?.billing.status ?? "Unavailable"} /><Info label="Period" value={dashboard.data?.billing.billingPeriodStart && dashboard.data.billing.billingPeriodEnd ? `${dashboard.data.billing.billingPeriodStart} to ${dashboard.data.billing.billingPeriodEnd}` : "Unavailable"} /></CardContent></Card>
      <Card><CardHeader><CardTitle>Notification Preferences</CardTitle></CardHeader><CardContent><label className="flex items-center justify-between gap-4 text-sm"><span><span className="font-medium">Billing and system notifications</span><span className="mt-1 block text-muted-foreground">Stored locally in this browser.</span></span><input checked={notifications} className="size-4 accent-primary" onChange={(event) => setNotificationPreference(event.target.checked)} type="checkbox" /></label></CardContent></Card>
      <Card><CardHeader><CardTitle>Appearance</CardTitle></CardHeader><CardContent><label className="text-sm"><span className="mb-1.5 block text-muted-foreground">Theme</span><select className="h-9 w-full rounded-lg border border-border bg-background px-3" value={theme ?? "system"} onChange={(event) => setTheme(event.target.value)}><option value="system">System</option><option value="light">Light</option><option value="dark">Dark</option></select></label></CardContent></Card>
      <Card><CardHeader><CardTitle>Security / Session</CardTitle></CardHeader><CardContent className="space-y-3"><Info label="Credentials" value="Never displayed or stored in the browser" /><Info label="Session token" value="Not displayed" /><Info label="Automatic disconnect" value="Disabled" /><Info label="Logout" value="No logout provider configured" /></CardContent></Card>
    </div>
    <Card><CardHeader className="flex-row items-center justify-between"><CardTitle>System Diagnostics</CardTitle><Button disabled={!connectionToken || diagnostics.isFetching} onClick={() => diagnostics.refetch()} variant="outline"><LoaderCircle className={`size-4 ${diagnostics.isFetching ? "animate-spin" : ""}`} />Run Diagnostics</Button></CardHeader><CardContent>{!connectionToken && <p className="text-sm text-muted-foreground">Connect AWS to run live checks.</p>}{diagnosticsMessage && <p className={`flex gap-2 text-sm ${diagnosticsPassed ? "text-success" : diagnosticsFailed || diagnostics.isError ? "text-destructive" : "text-warning"}`}>{diagnosticsPassed ? <CheckCircle2 className="size-4" /> : <AlertTriangle className="size-4" />}{diagnosticsMessage}</p>}{diagnostics.data && <div className="mt-4 grid gap-3 sm:grid-cols-2 xl:grid-cols-3">{diagnostics.data.checks.map((check) => <div className="rounded-lg border border-border bg-muted/20 p-3" key={check.key}><div className="flex items-center justify-between gap-2"><p className="text-sm font-medium">{check.label}</p><Status status={check.status} /></div><p className="mt-2 text-xs text-muted-foreground">{check.detail}</p></div>)}</div>}</CardContent></Card>
  </div>;
}

function Info({ label, value }: { label: string; value: string }) { return <div className="flex items-start justify-between gap-4 border-b border-border pb-2 text-sm"><span className="text-muted-foreground">{label}</span><span className="text-right font-medium">{value}</span></div>; }
function Status({ status }: { status: string }) { return <span className={`flex items-center gap-1 text-xs font-medium ${status === "HEALTHY" ? "text-success" : status === "WARNING" ? "text-warning" : "text-destructive"}`}>{status === "HEALTHY" ? <CheckCircle2 className="size-3.5" /> : <AlertTriangle className="size-3.5" />}{status}</span>; }
