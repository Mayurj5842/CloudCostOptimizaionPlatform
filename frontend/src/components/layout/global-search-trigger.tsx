"use client";

import * as React from "react";
import { useRouter } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import { BarChart3, Check, Cloud, Database, FileSearch, Search, Server, X } from "lucide-react";

import { Button } from "@/components/ui/button";
import { fetchAwsResources, type AwsResourceResponse } from "@/services/aws-connection";
import { useCloudAccountStore } from "@/store/cloud-account-store";

type SearchItem = {
  title: string;
  description: string;
  route: string;
  category: "Pages" | "AWS Services" | "AWS Resources";
  keywords: string[];
  icon: typeof Search;
  resource?: boolean;
};

const pages: SearchItem[] = [
  { title: "Overview", description: "Cloud operations overview", route: "/", category: "Pages", keywords: ["overview", "dashboard", "home"], icon: BarChart3 },
  { title: "Cost Analytics", description: "Analyze AWS cloud spending", route: "/billing", category: "Pages", keywords: ["cost", "analytics", "billing", "spend", "aws"], icon: BarChart3 },
  { title: "Resources", description: "Browse live AWS resources", route: "/resources", category: "Pages", keywords: ["resource", "inventory", "ec2", "s3", "rds", "lambda"], icon: Server },
  { title: "Cloud Accounts", description: "Connect and manage cloud accounts", route: "/integrations", category: "Pages", keywords: ["account", "aws", "integration", "connect"], icon: Cloud },
];

const services: SearchItem[] = [
  ["EC2", "AWS compute resources", ["ec2", "compute", "instance"]],
  ["S3", "AWS object storage", ["s3", "storage", "bucket"]],
  ["RDS", "AWS managed databases", ["rds", "database"]],
  ["Lambda", "AWS serverless functions", ["lambda", "function", "serverless"]],
  ["EBS", "AWS block storage volumes", ["ebs", "volume"]],
  ["VPC", "AWS virtual networks", ["vpc", "network"]],
  ["CloudWatch", "AWS monitoring and observability", ["cloudwatch", "monitoring"]],
  ["DynamoDB", "AWS NoSQL database", ["dynamodb", "database"]],
  ["ECS", "AWS container orchestration", ["ecs", "container"]],
  ["EKS", "AWS Kubernetes service", ["eks", "kubernetes"]],
  ["CloudFront", "AWS content delivery", ["cloudfront", "cdn"]],
  ["ELB", "AWS load balancing", ["elb", "load balancer"]],
].map(([title, description, keywords]) => ({ title, description, route: "/resources", category: "AWS Services", keywords: keywords as string[], icon: Database }));

const categoryOrder = ["Pages", "AWS Services", "AWS Resources"] as const;

function rank(item: SearchItem, query: string) {
  const title = item.title.toLowerCase();
  const normalized = query.toLowerCase();
  if (title === normalized) return 0;
  if (title.startsWith(normalized)) return 1;
  if (title.includes(normalized)) return 2;
  if (item.keywords.some((keyword) => keyword.includes(normalized))) return 3;
  if (item.description.toLowerCase().includes(normalized)) return 4;
  return 5;
}

function resourceItems(data?: AwsResourceResponse): SearchItem[] {
  if (!data) return [];
  return [
    ...data.ec2Instances.map((item) => ({ title: item.name || item.instanceId, description: `EC2 Instance · ${item.instanceId} · ${item.state}`, route: "/resources", category: "AWS Resources" as const, keywords: [item.name, item.instanceId, item.region, item.state, "ec2"], icon: Server, resource: true })),
    ...data.s3Buckets.map((item) => ({ title: item.bucketName, description: `S3 Bucket · ${item.region}`, route: "/resources", category: "AWS Resources" as const, keywords: [item.bucketName, item.region, "s3", "bucket"], icon: Cloud, resource: true })),
    ...data.rdsDatabases.map((item) => ({ title: item.identifier, description: `RDS Database · ${item.status} · ${item.region}`, route: "/resources", category: "AWS Resources" as const, keywords: [item.identifier, item.engine, item.region, "rds", "database"], icon: Database, resource: true })),
    ...data.lambdaFunctions.map((item) => ({ title: item.functionName, description: `Lambda Function · ${item.runtime} · ${item.region}`, route: "/resources", category: "AWS Resources" as const, keywords: [item.functionName, item.region, "lambda", "function"], icon: Cloud, resource: true })),
    ...data.ebsVolumes.map((item) => ({ title: item.volumeId, description: `EBS Volume · ${item.state} · ${item.availabilityZone}`, route: "/resources", category: "AWS Resources" as const, keywords: [item.volumeId, item.state, item.availabilityZone, "ebs", "volume"], icon: Server, resource: true })),
    ...data.vpcs.map((item) => ({ title: item.vpcId, description: `VPC · ${item.cidrBlock} · ${item.region}`, route: "/resources", category: "AWS Resources" as const, keywords: [item.vpcId, item.cidrBlock, item.region, "vpc"], icon: Cloud, resource: true })),
    ...data.securityGroups.map((item) => ({ title: item.groupName || item.groupId, description: `Security Group · ${item.groupId}`, route: "/resources", category: "AWS Resources" as const, keywords: [item.groupName, item.groupId, "security group"], icon: Server, resource: true })),
  ];
}

export function GlobalSearchTrigger() {
  const router = useRouter();
  const account = useCloudAccountStore((state) => state.account);
  const connectionToken = useCloudAccountStore((state) => state.connectionToken);
  const [open, setOpen] = React.useState(false);
  const [query, setQuery] = React.useState("");
  const [debouncedQuery, setDebouncedQuery] = React.useState("");
  const [selectedIndex, setSelectedIndex] = React.useState(0);
  const [recent, setRecent] = React.useState<string[]>([]);
  const inputRef = React.useRef<HTMLInputElement>(null);
  const resourceQuery = useQuery({ queryKey: ["aws-resources", account?.accountId, connectionToken], queryFn: () => fetchAwsResources(connectionToken as string), enabled: open && Boolean(connectionToken) && debouncedQuery.trim().length >= 2, staleTime: 5 * 60 * 1000, retry: false });

  React.useEffect(() => {
    const stored = window.localStorage.getItem("cloudcost-search-recent");
    if (stored) setRecent(JSON.parse(stored));
  }, []);
  React.useEffect(() => {
    const timer = window.setTimeout(() => setDebouncedQuery(query), 300);
    return () => window.clearTimeout(timer);
  }, [query]);
  React.useEffect(() => {
    if (!open) return;
    inputRef.current?.focus();
  }, [open]);
  React.useEffect(() => {
    const handleShortcut = (event: KeyboardEvent) => {
      if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === "k") { event.preventDefault(); setOpen(true); }
      if (event.key === "Escape") setOpen(false);
    };
    window.addEventListener("keydown", handleShortcut);
    return () => window.removeEventListener("keydown", handleShortcut);
  }, []);

  const allItems = [...pages, ...services, ...resourceItems(resourceQuery.data)];
  const results = (query.trim() ? allItems.filter((item) => rank(item, query.trim()) < 5).sort((a, b) => rank(a, query.trim()) - rank(b, query.trim())) : pages.filter((item) => recent.includes(item.title) || ["Overview", "Cost Analytics", "Resources", "Cloud Accounts"].includes(item.title))).slice(0, 30);
  const select = (item: SearchItem) => {
    const nextRecent = [item.title, ...recent.filter((value) => value !== item.title)].slice(0, 5);
    setRecent(nextRecent);
    window.localStorage.setItem("cloudcost-search-recent", JSON.stringify(nextRecent));
    setOpen(false);
    setQuery("");
    router.push(item.route);
  };
  const handleKeyDown = (event: React.KeyboardEvent<HTMLInputElement>) => {
    if (event.key === "ArrowDown") { event.preventDefault(); setSelectedIndex((value) => Math.min(value + 1, results.length - 1)); }
    if (event.key === "ArrowUp") { event.preventDefault(); setSelectedIndex((value) => Math.max(value - 1, 0)); }
    if (event.key === "Enter" && results[selectedIndex]) { event.preventDefault(); select(results[selectedIndex]); }
  };

  return <>
    <Button aria-label="Search CloudCost AI" className="h-9 w-full max-w-md justify-start gap-2 border-border bg-surface-muted px-3 text-muted-foreground hover:bg-muted sm:w-[320px]" onClick={() => setOpen(true)} variant="outline"><Search className="size-4" /><span className="flex-1 text-left text-sm">Search CloudCost AI...</span><kbd className="hidden rounded border border-border bg-card px-1.5 py-0.5 font-mono text-[10px] text-muted-foreground sm:inline">Ctrl K</kbd></Button>
    {open && <div className="fixed inset-0 z-50 bg-foreground/20 p-4 sm:p-8" onMouseDown={() => setOpen(false)}><section aria-label="Global search" aria-modal="true" className="mx-auto mt-8 w-full max-w-2xl overflow-hidden rounded-xl border border-border bg-popover text-popover-foreground shadow-2xl sm:mt-12" onMouseDown={(event) => event.stopPropagation()} role="dialog"><div className="flex items-center gap-3 border-b border-border px-4"><Search className="size-4 text-muted-foreground" /><input aria-label="Search CloudCost AI" autoComplete="off" className="h-12 min-w-0 flex-1 bg-transparent text-sm outline-none placeholder:text-muted-foreground" onChange={(event) => { setQuery(event.target.value); setSelectedIndex(0); }} onKeyDown={handleKeyDown} placeholder="Search CloudCost AI..." ref={inputRef} value={query} /><button aria-label="Close search" className="rounded p-1 text-muted-foreground hover:bg-muted" onClick={() => setOpen(false)} type="button"><X className="size-4" /></button></div><div className="max-h-[min(70vh,520px)] overflow-y-auto p-2">{resourceQuery.isFetching && query.trim().length >= 2 && <p className="px-3 py-2 text-xs text-muted-foreground">Searching AWS resources...</p>}{results.length === 0 && !resourceQuery.isFetching && <p className="px-3 py-8 text-center text-sm text-muted-foreground">No results found{query ? ` for "${query}"` : ""}</p>}{categoryOrder.map((category) => { const grouped = results.filter((item) => item.category === category); return grouped.length ? <div key={category}><p className="px-3 pb-1 pt-2 text-[10px] font-semibold uppercase tracking-wider text-muted-foreground">{category}</p>{grouped.map((item) => { const index = results.indexOf(item); const Icon = item.icon; return <button aria-label={`${item.title}, ${item.description}`} className={`flex w-full items-center gap-3 rounded-lg px-3 py-2.5 text-left ${index === selectedIndex ? "bg-accent text-accent-foreground" : "hover:bg-muted"}`} key={`${item.category}-${item.title}-${item.description}`} onClick={() => select(item)} type="button"><Icon className="size-4 shrink-0 text-primary" /><span className="min-w-0 flex-1"><span className="block truncate text-sm font-medium">{item.title}</span><span className="block truncate text-xs text-muted-foreground">{item.description}</span></span>{item.resource && <span className="text-[10px] text-muted-foreground">Live</span>}{index === selectedIndex && <Check className="size-4 shrink-0" />}</button>; })}</div> : null; })}</div></section></div>}
  </>;
}
