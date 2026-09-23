"use client";

import * as React from "react";
import axios from "axios";
import { useQueryClient } from "@tanstack/react-query";
import { Check, Cloud, Eye, EyeOff, KeyRound, ShieldCheck, TriangleAlert, X } from "lucide-react";
import { useRouter } from "next/navigation";

import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { awsRegions } from "@/mock/cloud-account";
import { connectAwsAccount, disconnectAwsAccount } from "@/services/aws-connection";
import { useCloudAccountStore, type ConnectionStatus } from "@/store/cloud-account-store";
import { ConnectionStatusBadge } from "./connection-status";

type FormValues = { alias: string; region: string; accessKeyId: string; secretAccessKey: string };
type FormErrors = Partial<Record<keyof FormValues, string>>;
const emptyForm: FormValues = { alias: "", region: "", accessKeyId: "", secretAccessKey: "" };
const progress = ["Validating configuration", "Establishing secure connection", "Verifying AWS account"];

export default function CloudAccountsPage() {
  const { account, connectionToken, connectionStatus, connect, disconnect, setConnectionStatus } = useCloudAccountStore();
  const queryClient = useQueryClient();
  const router = useRouter();
  const [formOpen, setFormOpen] = React.useState(false);
  const [disconnectOpen, setDisconnectOpen] = React.useState(false);
  const [form, setForm] = React.useState<FormValues>(emptyForm);
  const [errors, setErrors] = React.useState<FormErrors>({});
  const [showSecret, setShowSecret] = React.useState(false);
  const [step, setStep] = React.useState(-1);
  const [connectionError, setConnectionError] = React.useState("");
  const [disconnectError, setDisconnectError] = React.useState("");

  const closeForm = () => { if (connectionStatus !== "connecting") { setFormOpen(false); setForm(emptyForm); setErrors({}); setShowSecret(false); setConnectionError(""); } };
  const update = (field: keyof FormValues, value: string) => { setForm((previous) => ({ ...previous, [field]: value })); setErrors((previous) => ({ ...previous, [field]: undefined })); setConnectionError(""); };
  const validate = () => {
    const next: FormErrors = {};
    if (form.alias.trim().length < 2 || form.alias.trim().length > 60) next.alias = "Enter an account alias between 2 and 60 characters.";
    if (!form.region) next.region = "Select an AWS region.";
    if (!form.accessKeyId.trim()) next.accessKeyId = "Enter your AWS Access Key ID.";
    if (!form.secretAccessKey) next.secretAccessKey = "Enter your AWS Secret Access Key.";
    setErrors(next);
    return Object.keys(next).length === 0;
  };
  const submit = async (event: React.FormEvent) => {
    event.preventDefault();
    if (!validate()) return;
    const accountAlias = form.alias.trim();
    const region = form.region;
    const accessKey = form.accessKeyId.trim();
    const secretKey = form.secretAccessKey;
    setForm(emptyForm);
    setConnectionError("");
    setConnectionStatus("connecting");
    setStep(1);

    try {
      const verifiedAccount = await connectAwsAccount({ accountAlias, accessKey, secretKey, region });
      connect({ provider: "AWS", accountAlias, region, accountId: verifiedAccount.accountId, arn: verifiedAccount.arn, userId: verifiedAccount.userId, lastVerified: "Just now" }, verifiedAccount.connectionToken);
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ["aws-connection"] }),
        queryClient.invalidateQueries({ queryKey: ["aws-resources"], exact: false }),
        queryClient.invalidateQueries({ queryKey: ["aws-dashboard"], exact: false }),
        queryClient.invalidateQueries({ queryKey: ["aws-cost-analytics"], exact: false }),
      ]);
      setStep(-1);
      setFormOpen(false);
    } catch (error) {
      setConnectionStatus("error");
      setStep(-1);
      const message = axios.isAxiosError<{ message?: string }>(error) ? error.response?.data?.message : undefined;
      setConnectionError(message ?? "Unable to connect to AWS. Please verify your credentials and region.");
    }
  };

  const handleDisconnect = async () => {
    if (!connectionToken) {
      setDisconnectError("AWS connection is missing. Please reconnect to AWS.");
      return;
    }

    try {
      await disconnectAwsAccount(connectionToken);
      disconnect();
      setDisconnectOpen(false);
      setDisconnectError("");
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ["aws-connection"] }),
        queryClient.invalidateQueries({ queryKey: ["aws-resources"], exact: false }),
        queryClient.invalidateQueries({ queryKey: ["aws-dashboard"], exact: false }),
        queryClient.invalidateQueries({ queryKey: ["aws-cost-analytics"], exact: false }),
      ]);
    } catch (error) {
      const message = axios.isAxiosError<{ message?: string }>(error) ? error.response?.data?.message : undefined;
      setDisconnectError(message ?? "Unable to disconnect from AWS. Please try again.");
    }
  };

  return <div className="mx-auto max-w-5xl space-y-8">
    <div><p className="text-sm font-medium text-primary">Integrations</p><h1 className="mt-1 text-2xl font-semibold tracking-tight sm:text-3xl">Cloud Accounts</h1><p className="mt-2 text-sm text-muted-foreground sm:text-base">Connect and manage your cloud provider accounts.</p></div>
    {!account && connectionStatus === "restoring" ? <Card><CardContent className="flex min-h-[360px] items-center justify-center text-sm text-muted-foreground"><Cloud className="mr-2 size-4 animate-pulse" />Restoring AWS connection...</CardContent></Card> : !account ? <Card className="overflow-visible"><CardContent className="flex min-h-[360px] flex-col items-center justify-center px-6 py-12 text-center"><div className="flex size-12 items-center justify-center rounded-xl bg-primary/10 text-primary"><Cloud className="size-6" /></div><h2 className="mt-5 text-xl font-semibold tracking-tight">Connect your AWS account</h2><p className="mt-2 max-w-md text-sm leading-6 text-muted-foreground">Connect AWS to unlock cost analytics, resource insights, forecasting, and AI optimization recommendations.</p><Button className="mt-6 h-10 px-4" onClick={() => { setConnectionError(""); setConnectionStatus("disconnected"); setFormOpen(true); }}><Cloud className="size-4" />Connect AWS Account</Button></CardContent></Card> : <ConnectedAccount account={account} status={connectionStatus} onDisconnect={() => setDisconnectOpen(true)} onReconnect={() => { setConnectionError(""); setConnectionStatus("disconnected"); setFormOpen(true); }} onManage={() => router.push("/settings")} />}

    {formOpen && connectionError && <div className="fixed inset-x-4 top-4 z-[80] mx-auto max-w-xl rounded-lg border border-destructive/30 bg-popover p-3 text-sm text-destructive shadow-lg"><TriangleAlert className="mr-2 inline size-4" />{connectionError}</div>}
    {formOpen && <ConnectionDialog form={form} errors={errors} status={connectionStatus} step={step} showSecret={showSecret} onClose={closeForm} onSubmit={submit} onUpdate={update} onToggleSecret={() => setShowSecret((value) => !value)} />}
    {disconnectOpen && <ConfirmDisconnect error={disconnectError} onCancel={() => { setDisconnectError(""); setDisconnectOpen(false); }} onDisconnect={handleDisconnect} />}
  </div>;
}

function ConnectedAccount({ account, status, onDisconnect, onReconnect, onManage }: { account: NonNullable<ReturnType<typeof useCloudAccountStore.getState>["account"]>; status: ConnectionStatus; onDisconnect: () => void; onReconnect: () => void; onManage: () => void }) {
  const region = awsRegions.find((item) => item.value === account.region)?.label ?? account.region;
  const expired = status === "error";
  return <Card className="overflow-visible"><CardHeader className="flex flex-row items-center justify-between gap-4"><div className="flex items-center gap-3"><div className="flex size-9 items-center justify-center rounded-lg bg-primary/10 text-primary"><Cloud className="size-5" /></div><div><CardTitle>AWS</CardTitle><p className="text-xs text-muted-foreground">Cloud provider account</p></div></div><ConnectionStatusBadge status={status} /></CardHeader><CardContent><div className="grid gap-5 border-y border-border py-5 sm:grid-cols-2 lg:grid-cols-3"><Info label="Account Alias" value={account.accountAlias} /><Info label="AWS Account ID" value={account.accountId} /><Info label="Region" value={region} /><Info label="ARN" value={account.arn} /><Info label="User ID" value={account.userId} /><Info label="Last verified" value={account.lastVerified} /></div><div className="mt-5 flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between"><div className={expired ? "flex items-center gap-2 text-sm text-destructive" : "flex items-center gap-2 text-sm text-success"}>{expired ? <TriangleAlert className="size-4" /> : <Check className="size-4" />}{expired ? "AWS connection has expired. Please connect the account again." : "Successfully connected to AWS"}</div><div className="flex gap-2">{expired ? <Button onClick={onReconnect}><Cloud className="size-4" />Reconnect AWS</Button> : <Button onClick={onManage} variant="outline">Manage</Button>}<Button onClick={onDisconnect} variant="destructive">Disconnect</Button></div></div></CardContent></Card>;
}
function Info({ label, value }: { label: string; value: string }) { return <div><p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">{label}</p><p className="mt-1 text-sm font-medium text-foreground">{value}</p></div>; }

function ConnectionDialog({ form, errors, status, step, showSecret, onClose, onSubmit, onUpdate, onToggleSecret }: { form: FormValues; errors: FormErrors; status: string; step: number; showSecret: boolean; onClose: () => void; onSubmit: (event: React.FormEvent) => void; onUpdate: (field: keyof FormValues, value: string) => void; onToggleSecret: () => void }) {
  React.useEffect(() => { const close = (event: KeyboardEvent) => event.key === "Escape" && onClose(); window.addEventListener("keydown", close); return () => window.removeEventListener("keydown", close); }, [onClose]);
  const connecting = status === "connecting";
  return <div className="fixed inset-0 z-[60] flex items-end bg-foreground/30 p-0 sm:items-center sm:justify-center sm:p-6" role="presentation"><button aria-label="Close AWS connection form" className="absolute inset-0" onClick={onClose} /><section aria-describedby="aws-connection-description" aria-labelledby="aws-connection-title" aria-modal="true" className="relative z-10 max-h-[92vh] w-full overflow-y-auto rounded-t-2xl border border-border bg-popover shadow-2xl sm:max-w-xl sm:rounded-2xl" role="dialog"><div className="flex items-start justify-between border-b border-border px-5 py-4"><div><h2 className="text-lg font-semibold" id="aws-connection-title">Connect AWS account</h2><p className="mt-1 text-sm text-muted-foreground" id="aws-connection-description">Add a cloud account for cost and resource analysis.</p></div><Button aria-label="Close" disabled={connecting} onClick={onClose} size="icon-sm" variant="ghost"><X /></Button></div>{connecting ? <ConnectingState step={step} /> : <form className="space-y-4 p-5" onSubmit={onSubmit}><Field error={errors.alias} label="Account Alias"><input autoFocus className={inputClass} onChange={(event) => onUpdate("alias", event.target.value)} placeholder="Production AWS" value={form.alias} /></Field><p className="-mt-2 text-xs text-muted-foreground">A friendly name to identify this account inside CloudCost AI.</p><Field error={errors.region} label="AWS Region"><select className={inputClass} onChange={(event) => onUpdate("region", event.target.value)} value={form.region}><option value="">Select a region</option>{awsRegions.map((region) => <option key={region.value} value={region.value}>{region.label}</option>)}</select></Field><Field error={errors.accessKeyId} label="AWS Access Key ID"><input autoComplete="off" className={inputClass} onChange={(event) => onUpdate("accessKeyId", event.target.value)} value={form.accessKeyId} /></Field><Field error={errors.secretAccessKey} label="AWS Secret Access Key"><div className="relative"><input autoComplete="new-password" className={inputClass} onChange={(event) => onUpdate("secretAccessKey", event.target.value)} type={showSecret ? "text" : "password"} value={form.secretAccessKey} /><Button aria-label={showSecret ? "Hide secret access key" : "Show secret access key"} className="absolute right-1 top-1" onClick={onToggleSecret} size="icon-sm" type="button" variant="ghost">{showSecret ? <EyeOff /> : <Eye />}</Button></div></Field><div className="rounded-lg border border-warning/25 bg-warning/10 p-3 text-xs leading-5 text-foreground"><div className="flex gap-2"><TriangleAlert className="mt-0.5 size-4 shrink-0 text-warning" /><p><strong>Your AWS credentials are sensitive.</strong> For production connections, credentials will be securely handled by the CloudCost AI backend. This frontend demo never transmits or stores them.</p></div></div><div className="flex justify-end gap-2 pt-1"><Button onClick={onClose} type="button" variant="outline">Cancel</Button><Button type="submit"><KeyRound className="size-4" />Connect AWS</Button></div></form>}</section></div>;
}
const inputClass = "h-10 w-full rounded-lg border border-input bg-background px-3 text-sm outline-none placeholder:text-muted-foreground focus-visible:ring-2 focus-visible:ring-ring";
function Field({ label, error, children }: { label: string; error?: string; children: React.ReactNode }) { return <label className="block space-y-1.5"><span className="text-sm font-medium">{label}</span>{children}{error && <span className="block text-xs text-destructive">{error}</span>}</label>; }
function ConnectingState({ step }: { step: number }) { return <div className="space-y-5 p-6"><div className="flex items-center gap-3"><div className="flex size-10 items-center justify-center rounded-lg bg-info/10 text-info"><ShieldCheck className="size-5" /></div><div><p className="font-medium">Connecting to AWS...</p><p className="text-sm text-muted-foreground">Preparing a secure connection simulation.</p></div></div><div className="space-y-3">{progress.map((item, index) => <div className="flex items-center gap-3 text-sm" key={item}>{index < step ? <Check className="size-4 text-success" /> : <span className={index === step ? "size-2 rounded-full bg-info animate-pulse" : "size-2 rounded-full bg-border"} />}<span className={index <= step ? "text-foreground" : "text-muted-foreground"}>{item}</span></div>)}</div></div>; }
function ConfirmDisconnect({ error, onCancel, onDisconnect }: { error: string; onCancel: () => void; onDisconnect: () => void }) { return <div className="fixed inset-0 z-[70] flex items-center justify-center bg-foreground/30 p-4" role="presentation"><section aria-labelledby="disconnect-title" aria-modal="true" className="w-full max-w-md rounded-xl border border-border bg-popover p-5 shadow-2xl" role="dialog"><h2 className="text-lg font-semibold" id="disconnect-title">Disconnect AWS account?</h2><p className="mt-2 text-sm leading-6 text-muted-foreground">You will stop receiving cost and resource data from this account until it is connected again.</p>{error && <p className="mt-3 text-sm text-destructive">{error}</p>}<div className="mt-5 flex justify-end gap-2"><Button onClick={onCancel} variant="outline">Cancel</Button><Button onClick={onDisconnect} variant="destructive">Disconnect</Button></div></section></div>; }
