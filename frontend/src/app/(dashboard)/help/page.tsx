"use client";

import { ArrowRight, BookOpen, CheckCircle2, CircleHelp, Search, Workflow } from "lucide-react";
import { useMemo, useState } from "react";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";

const topics = [
  ["Getting Started", "Connect AWS, confirm the session, and open the dashboard."],
  ["AWS Connection", "CloudCostAI validates credentials with STS and uses a short-lived backend session."],
  ["CUR 2.0 Billing", "AWS delivers CUR 2.0 data to S3; the backend imports it into PostgreSQL."],
  ["Cost Analytics", "Analytics reads persisted CUR data for service, region, and trend views."],
  ["AI Insights", "Insights identify cost drivers and resource findings from stored billing and inventory."],
  ["Recommendations", "Recommendations use billing evidence and resource context without inventing savings."],
  ["Budgets & Alerts", "Budgets compare configured thresholds with stored billing data."],
  ["Reports & PDF", "Reports and PDF use the same AWS session as the report API."],
  ["Troubleshooting", "Use Settings diagnostics to check API, database, session, CUR, billing, and resources."],
  ["FAQ", "Normal API, resource, insight, recommendation, or PDF errors never disconnect AWS."],
] as const;

export default function HelpPage() {
  const [query, setQuery] = useState("");
  const visible = useMemo(() => topics.filter(([title, detail]) => `${title} ${detail}`.toLowerCase().includes(query.trim().toLowerCase())), [query]);
  const flow = ["AWS", "CUR 2.0", "S3", "Backend", "PostgreSQL", "Analytics", "Insights", "Recommendations", "Reports/PDF"];
  return <div className="mx-auto max-w-6xl space-y-8"><header><p className="text-sm font-medium text-primary">SYSTEM</p><h1 className="mt-1 text-2xl font-semibold tracking-tight sm:text-3xl">Help & Documentation</h1><p className="mt-2 text-sm text-muted-foreground">Guides and operational context for CloudCostAI.</p><label className="mt-5 flex max-w-xl items-center gap-2 rounded-lg border border-border bg-background px-3"><Search className="size-4 text-muted-foreground" /><input aria-label="Search documentation" className="h-10 min-w-0 flex-1 bg-transparent text-sm outline-none" onChange={(event) => setQuery(event.target.value)} placeholder="Search documentation..." value={query} /></label></header><Card><CardHeader><CardTitle className="flex items-center gap-2"><Workflow className="size-5 text-primary" />How CloudCostAI Works</CardTitle></CardHeader><CardContent><div className="flex flex-wrap items-center gap-2 text-sm">{flow.map((step, index) => <span className="flex items-center gap-2" key={step}><span className="rounded-lg border border-border bg-muted/30 px-3 py-2 font-medium">{step}</span>{index < flow.length - 1 && <ArrowRight className="size-4 text-muted-foreground" />}</span>)}</div></CardContent></Card>{visible.length === 0 ? <Card><CardContent className="py-12 text-center text-sm text-muted-foreground">No documentation matches “{query}”.</CardContent></Card> : <section className="grid gap-4 md:grid-cols-2">{visible.map(([title, detail]) => <Card key={title}><CardHeader><CardTitle className="flex items-center gap-2 text-base"><BookOpen className="size-4 text-primary" />{title}</CardTitle></CardHeader><CardContent><p className="text-sm leading-6 text-muted-foreground">{detail}</p></CardContent></Card>)}</section>}<Card><CardHeader><CardTitle className="flex items-center gap-2"><CircleHelp className="size-5 text-primary" />Operational guarantees</CardTitle></CardHeader><CardContent className="grid gap-3 text-sm sm:grid-cols-2"><p className="flex gap-2"><CheckCircle2 className="size-4 shrink-0 text-success" />Credentials and tokens are never displayed.</p><p className="flex gap-2"><CheckCircle2 className="size-4 shrink-0 text-success" />Only explicit Disconnect ends the AWS connection.</p><p className="flex gap-2"><CheckCircle2 className="size-4 shrink-0 text-success" />Cost Explorer is not used.</p><p className="flex gap-2"><CheckCircle2 className="size-4 shrink-0 text-success" />The same session supports dashboard, reports, PDF, and diagnostics.</p></CardContent></Card></div>;
}
