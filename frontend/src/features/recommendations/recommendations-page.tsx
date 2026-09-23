"use client";

import Link from "next/link";
import { AlertTriangle, CheckCircle2, Lightbulb, LoaderCircle, TrendingDown } from "lucide-react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { fetchAwsRecommendations, generateAwsRecommendations, updateAwsRecommendationStatus, type AwsRecommendation } from "@/services/aws-connection";
import { useCloudAccountStore } from "@/store/cloud-account-store";
import { useMonthSelectionStore } from "@/store/month-selection-store";
import { MonthSelector } from "@/components/month-selector";

const priorityStyle = { High: "destructive", Medium: "warning", Low: "success", HIGH: "destructive", MEDIUM: "warning", LOW: "success" } as const;
const statusStyle = { New: "info", Applied: "success", Dismissed: "outline", NEW: "info", REVIEWED: "success", DISMISSED: "outline" } as const;

export default function RecommendationsPage({ compact = false, month }: { compact?: boolean; month?: string }) {
  const { account, connectionToken } = useCloudAccountStore();
  const storedMonth = useMonthSelectionStore((state) => state.selectedMonth);
  const selectedMonth = month ?? storedMonth;
  const setSelectedMonth = useMonthSelectionStore((state) => state.setSelectedMonth);
  const queryClient = useQueryClient();
  const [priority, setPriority] = useState("All");
  const [service, setService] = useState("All");
  const [region, setRegion] = useState("All");
  const [category, setCategory] = useState("All");
  const [status, setStatus] = useState("All");
  const queryKey = ["aws-recommendations", account?.accountId, connectionToken, selectedMonth];
  const query = useQuery({ queryKey, queryFn: () => fetchAwsRecommendations(connectionToken as string, selectedMonth), enabled: Boolean(connectionToken), staleTime: 5 * 60 * 1000, retry: false });
  const mutation = useMutation({ mutationFn: ({ id, value }: { id: string; value: AwsRecommendation["status"] }) => updateAwsRecommendationStatus(connectionToken as string, id, value, selectedMonth), onSuccess: (data) => queryClient.setQueryData(queryKey, data) });
  const generateMutation = useMutation({ mutationFn: () => generateAwsRecommendations(connectionToken as string, selectedMonth), onSuccess: (data) => queryClient.setQueryData(queryKey, data) });

  if (!account || !connectionToken) return <Card><CardContent className="flex min-h-[280px] flex-col items-center justify-center text-center"><Lightbulb className="size-9 text-primary" /><p className="mt-4 font-medium">Connect an AWS account first</p><p className="mt-2 text-sm text-muted-foreground">Recommendations use imported CUR 2.0 costs and AWS resource data.</p></CardContent></Card>;
  if (query.isLoading) return <Card><CardContent className="flex min-h-[220px] items-center justify-center text-sm text-muted-foreground"><LoaderCircle className="mr-2 size-4 animate-spin" />Analyzing AWS cost data...</CardContent></Card>;
  if (query.isError) return <Card><CardContent className="flex min-h-[220px] flex-col items-center justify-center text-center"><AlertTriangle className="size-8 text-destructive" /><p className="mt-3 font-medium">Unable to load recommendations</p><Button className="mt-4" onClick={() => query.refetch()} variant="outline">Try again</Button></CardContent></Card>;

  const all = query.data?.recommendations ?? [];
  const filtered = all.filter((item) => (priority === "All" || item.priority === priority) && (service === "All" || item.service === service) && (region === "All" || item.region === region) && (category === "All" || item.category === category) && (status === "All" || item.status === status));
  const visible = compact ? filtered.slice(0, 4) : filtered;
  return <div className={compact ? "space-y-4" : "mx-auto max-w-7xl space-y-8"}>
    {!compact && <header className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between"><div><p className="text-sm font-medium text-primary">Optimization</p><h1 className="mt-1 text-2xl font-semibold tracking-tight sm:text-3xl">Recommendations</h1><p className="mt-2 text-sm text-muted-foreground">Actionable findings generated from stored PostgreSQL billing data.</p></div><div className="flex flex-wrap items-center gap-3"><MonthSelector value={selectedMonth} onChange={setSelectedMonth} /><Button onClick={() => generateMutation.mutate()} disabled={generateMutation.isPending}>{generateMutation.isPending ? "Analyzing stored data..." : "Generate recommendations"}</Button></div></header>}
    <Card><CardHeader className="flex flex-row items-center justify-between"><CardTitle className="flex items-center gap-2"><Lightbulb className="size-5 text-warning" />{compact ? "AI Recommendations" : "AWS cost recommendations"}</CardTitle><Badge variant="secondary">{all.length} findings</Badge></CardHeader><CardContent>
      {!compact && <><div className="mb-5 grid gap-3 sm:grid-cols-4"><Summary label="Total" value={all.length} /><Summary label="High priority" value={all.filter((item) => item.priority === "High").length} /><Summary label="Medium priority" value={all.filter((item) => item.priority === "Medium").length} /><Summary label="Low priority" value={all.filter((item) => item.priority === "Low").length} /></div><div className="mb-5 flex flex-col gap-3 rounded-lg border border-border bg-surface-muted p-3 sm:flex-row sm:flex-wrap"><Filter label="Service" value={service} options={["All", ...new Set(all.map((item) => item.service))]} onChange={setService} /><Filter label="Region" value={region} options={["All", ...new Set(all.map((item) => item.region))]} onChange={setRegion} /><Filter label="Severity" value={priority} options={["All", "High", "Medium", "Low"]} onChange={setPriority} /><Filter label="Category" value={category} options={["All", ...new Set(all.map((item) => item.category))]} onChange={setCategory} /><Filter label="Status" value={status} options={["All", "New", "Applied", "Dismissed"]} onChange={setStatus} /></div></>}
      {query.data?.status === "NO_DATA" ? <div className="py-12 text-center"><CheckCircle2 className="mx-auto size-8 text-muted-foreground" /><p className="mt-3 text-sm font-medium">No CUR billing data is available for {selectedMonth}</p><p className="mt-1 text-sm text-muted-foreground">Recommendations are not generated without real billing data.</p></div> : visible.length === 0 ? <div className="py-12 text-center"><CheckCircle2 className="mx-auto size-8 text-success" /><p className="mt-3 text-sm font-medium">No recommendations match these filters</p><p className="mt-1 text-sm text-muted-foreground">Recommendations appear when imported data supports an action.</p></div> : <div className="space-y-4">{visible.map((item) => <RecommendationCard currency={query.data?.currency ?? "USD"} item={item} key={item.id} onStatus={(value) => mutation.mutate({ id: item.id, value })} />)}</div>}
      {compact && all.length > visible.length && <Button asChild className="mt-5 w-full" variant="outline"><Link href="/recommendations">View all recommendations</Link></Button>}
    </CardContent></Card>
  </div>;
}

function Filter({ label, value, options, onChange }: { label: string; value: string; options: Iterable<string>; onChange: (value: string) => void }) { return <label className="flex items-center gap-2 text-sm"><span className="text-xs font-medium text-muted-foreground">{label}</span><select className="h-9 rounded-lg border border-input bg-background px-2.5 text-sm" onChange={(event) => onChange(event.target.value)} value={value}>{[...options].map((option) => <option key={option}>{option}</option>)}</select></label>; }

function Summary({ label, value }: { label: string; value: number }) { return <div className="rounded-lg border border-border bg-surface-muted p-3"><p className="text-xs text-muted-foreground">{label}</p><p className="mt-1 text-xl font-semibold">{value}</p></div>; }

function RecommendationCard({ item, currency, onStatus }: { item: AwsRecommendation; currency: string; onStatus: (value: AwsRecommendation["status"]) => void }) { const money = (value: number | null) => value == null ? "Not available" : new Intl.NumberFormat("en-US", { style: "currency", currency }).format(value); return <article className="rounded-lg border border-border bg-surface-muted p-4 transition-colors hover:border-primary/50"><div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between"><div><div className="flex flex-wrap items-center gap-2"><h3 className="font-medium">{item.problem}</h3><Badge variant={priorityStyle[item.priority]}>{item.priority}</Badge><Badge variant={statusStyle[item.status]}>{item.status}</Badge></div><p className="mt-1 text-sm text-muted-foreground">{item.service} / {item.region} / {item.resource}</p></div><div className="flex items-center gap-2 text-success"><TrendingDown className="size-4" /><span className="text-sm font-medium">{money(item.estimatedMonthlySaving)} potential savings</span></div></div><div className="mt-4 grid gap-4 border-y border-border py-3 text-sm sm:grid-cols-2"><div><p className="text-xs uppercase tracking-wide text-muted-foreground">Current cost</p><p className="mt-1 font-medium">{money(item.currentCost)}</p><p className="mt-3 text-xs uppercase tracking-wide text-muted-foreground">Evidence</p><p className="mt-1 text-muted-foreground">{item.reason}</p></div><div><p className="text-xs uppercase tracking-wide text-muted-foreground">Recommendation</p><p className="mt-1 text-muted-foreground">{item.recommendation}</p><p className="mt-3 text-xs uppercase tracking-wide text-muted-foreground">Suggested action</p><p className="mt-1 text-muted-foreground">{item.recommendedAction}</p></div></div><div className="mt-4 flex flex-col gap-2 sm:flex-row sm:justify-end"><Button asChild size="sm" variant="outline"><Link href={item.resourcePath}>View Resource</Link></Button>{item.status === "New" && <><Button onClick={() => onStatus("Applied")} size="sm">Apply</Button><Button onClick={() => onStatus("Dismissed")} size="sm" variant="ghost">Dismiss</Button></>}</div></article>; }
