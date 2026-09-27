"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import axios from "axios";
import { AlertTriangle, CheckCircle2, LoaderCircle, Mail, Send } from "lucide-react";
import { useEffect, useState } from "react";

import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { getAuthError } from "@/services/auth";
import { fetchNotificationPreferences, saveNotificationPreferences, sendNotificationTest, type NotificationPreferences } from "@/services/notifications";
import { useAuthStore } from "@/store/auth-store";
import { useCloudAccountStore } from "@/store/cloud-account-store";

export function EmailNotificationSettings() {
  const token = useAuthStore((state) => state.token);
  const userEmail = useAuthStore((state) => state.user?.email);
  const validateSession = useAuthStore((state) => state.validate);
  const account = useCloudAccountStore((state) => state.account);
  const queryClient = useQueryClient();
  const [form, setForm] = useState<NotificationPreferences>(() => initialPreferences("", userEmail ?? ""));
  const [notice, setNotice] = useState<{ message: string; error: boolean } | null>(null);

  const preferencesQuery = useQuery({
    queryKey: ["notification-preferences", token, account?.accountId],
    queryFn: () => fetchNotificationPreferences(token as string, account?.accountId as string),
    enabled: Boolean(token && account?.accountId),
    retry: false,
  });

  useEffect(() => {
    if (preferencesQuery.data) setForm(preferencesQuery.data);
  }, [preferencesQuery.data]);

  const saveMutation = useMutation({
    mutationFn: () => saveNotificationPreferences(token as string, form),
    onSuccess: (saved) => {
      setForm(saved);
      setNotice({ message: "Email notification preferences saved.", error: false });
      void queryClient.invalidateQueries({ queryKey: ["notification-preferences", token, account?.accountId] });
    },
    onError: (error) => {
      setNotice({ message: getAuthError(error, "Could not save email notification preferences."), error: true });
      if (axios.isAxiosError(error) && error.response?.status === 401) void validateSession();
    },
  });

  const testMutation = useMutation({
    mutationFn: () => sendNotificationTest(token as string, account?.accountId as string),
    onSuccess: (message) => setNotice({ message, error: false }),
    onError: (error) => setNotice({ message: getAuthError(error, "Test email could not be sent. Check SMTP configuration."), error: true }),
  });
  const hasUnsavedChanges = Boolean(preferencesQuery.data && JSON.stringify(form) !== JSON.stringify(preferencesQuery.data));

  const update = <K extends keyof NotificationPreferences>(key: K, value: NotificationPreferences[K]) => {
    setForm((current) => ({ ...current, [key]: value }));
    setNotice(null);
  };

  return <Card>
    <CardHeader><CardTitle className="flex items-center gap-2"><Mail className="size-4 text-primary" />Email Notifications</CardTitle></CardHeader>
    <CardContent className="space-y-5">
      {!account && <p className="text-sm text-muted-foreground">Connect an AWS account to configure account-specific email notifications.</p>}
      {account && preferencesQuery.isError && <p className="text-sm text-destructive">{getAuthError(preferencesQuery.error, "Notification preferences could not be loaded.")}</p>}
      {account && <>
        <Toggle label="Enable Email Notifications" checked={form.emailNotificationsEnabled} onChange={(value) => update("emailNotificationsEnabled", value)} />
        <label className="block text-sm">
          <span className="mb-1.5 block text-muted-foreground">Email address</span>
          <input className="h-10 w-full rounded-lg border border-input bg-background px-3" type="email" value={form.emailAddress} onChange={(event) => update("emailAddress", event.target.value)} />
        </label>
        <label className="block text-sm">
          <span className="mb-1.5 block text-muted-foreground">Budget alert threshold</span>
          <select className="h-10 w-full rounded-lg border border-input bg-background px-3" value={form.budgetAlertThreshold} onChange={(event) => update("budgetAlertThreshold", Number(event.target.value))}>
            <option value={80}>80%</option><option value={90}>90%</option><option value={100}>100%</option>
          </select>
        </label>
        <div className="space-y-3">
          <Toggle label="Budget Alert" checked={form.budgetAlertEnabled} onChange={(value) => update("budgetAlertEnabled", value)} />
          <Toggle label="Budget Exceeded" checked={form.budgetExceededEnabled} onChange={(value) => update("budgetExceededEnabled", value)} />
          <Toggle label="Cost Anomaly" checked={form.costAnomalyEnabled} onChange={(value) => update("costAnomalyEnabled", value)} />
          <Toggle label="Monthly Cost Summary" checked={form.monthlySummaryEnabled} onChange={(value) => update("monthlySummaryEnabled", value)} />
        </div>
        <div className="flex flex-wrap gap-2 border-t border-border pt-4">
          <Button disabled={saveMutation.isPending || preferencesQuery.isLoading} onClick={() => saveMutation.mutate()}>
            {saveMutation.isPending ? <LoaderCircle className="size-4 animate-spin" /> : null}Save
          </Button>
          <Button disabled={testMutation.isPending || !preferencesQuery.data || hasUnsavedChanges} onClick={() => testMutation.mutate()} variant="outline">
            {testMutation.isPending ? <LoaderCircle className="size-4 animate-spin" /> : <Send className="size-4" />}Test Email
          </Button>
        </div>
        {notice && <p role="status" className={`flex items-start gap-2 text-sm ${notice.error ? "text-destructive" : "text-success"}`}>
          {notice.error ? <AlertTriangle className="mt-0.5 size-4 shrink-0" /> : <CheckCircle2 className="mt-0.5 size-4 shrink-0" />}{notice.message}
        </p>}
      </>}
    </CardContent>
  </Card>;
}

function Toggle({ label, checked, onChange }: { label: string; checked: boolean; onChange: (value: boolean) => void }) {
  return <label className="flex items-center justify-between gap-4 text-sm">
    <span className="font-medium">{label}</span>
    <input aria-label={label} checked={checked} className="size-4 accent-primary" onChange={(event) => onChange(event.target.checked)} type="checkbox" />
  </label>;
}

function initialPreferences(awsAccountId: string, emailAddress: string): NotificationPreferences {
  return {
    awsAccountId,
    emailAddress,
    emailNotificationsEnabled: false,
    budgetAlertEnabled: true,
    budgetAlertThreshold: 80,
    budgetExceededEnabled: true,
    costAnomalyEnabled: true,
    monthlySummaryEnabled: true,
  };
}