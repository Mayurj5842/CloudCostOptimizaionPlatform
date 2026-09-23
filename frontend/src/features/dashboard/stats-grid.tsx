"use client";

import { useState } from "react";
import StatCard from "./stat-card";
import { DollarSign, LoaderCircle, PiggyBank, Server, Wallet, X } from "lucide-react";
import { useAwsDashboard } from "./use-aws-dashboard";
import { fetchAwsResources, type AwsResourceResponse } from "@/services/aws-connection";
import { useCloudAccountStore } from "@/store/cloud-account-store";
import { useQuery } from "@tanstack/react-query";

const icons = [DollarSign, Server, PiggyBank, Wallet] as const;

export default function StatsGrid({ month }: { month: string }) {
  const dashboard = useAwsDashboard(month);
  const connectionToken = useCloudAccountStore((state) => state.connectionToken);
  const [isResourcesOpen, setIsResourcesOpen] = useState(false);
  const resources = useQuery({
    queryKey: ["aws-resources", connectionToken],
    queryFn: () => fetchAwsResources(connectionToken as string),
    enabled: isResourcesOpen && Boolean(connectionToken),
    retry: false,
  });
  const data = dashboard.data;
  const stats = [
    {
      title: "Total Cloud Spend",
      value: data?.totalCloudSpend ? formatCurrency(data.totalCloudSpend.value, data.totalCloudSpend.currency) : dashboard.isError ? "Unavailable" : data ? "Unavailable" : "Loading...",
      change: data?.totalCloudSpend?.percentageChange == null ? "No previous-month data" : formatChange(data.totalCloudSpend.percentageChange),
      description: "Compared to last month",
    },
    {
      title: "Active Resources",
      value: data ? String(data.activeResources.value) : dashboard.isError ? "Unavailable" : "Loading...",
      change: "",
      description: "Currently active",
    },
    {
      title: "Estimated Savings",
      value: data?.estimatedSavings ? formatCurrency(data.estimatedSavings.value, data.estimatedSavings.currency) : data ? "Unavailable" : dashboard.isError ? "Unavailable" : "Loading...",
      change: "",
      description: data?.estimatedSavings ? "Optimization opportunities" : "No recommendations available",
    },
    {
      title: "Budget Usage",
      value: data?.budgetUsage?.configured ? (data.budgetUsage.usagePercentage == null ? "Unavailable" : `${data.budgetUsage.usagePercentage}%`) : data ? "No budget configured" : dashboard.isError ? "Unavailable" : "Loading...",
      change: data?.budgetUsage?.configured ? budgetStatus(data.budgetUsage.status) : "",
      description: "Current monthly budget",
    },
  ];

  return (
    <section className="grid grid-cols-1 gap-6 sm:grid-cols-2 xl:grid-cols-4">
      {stats.map((stat, index) => (
        <StatCard
          key={stat.title}
          title={stat.title}
          value={stat.value}
          change={stat.change}
          description={stat.description}
          icon={icons[index]}
          interactive={stat.title === "Active Resources"}
          onClick={stat.title === "Active Resources" ? () => setIsResourcesOpen(true) : undefined}
          onKeyDown={stat.title === "Active Resources" ? (event) => {
            if (event.key === "Enter" || event.key === " ") {
              event.preventDefault();
              setIsResourcesOpen(true);
            }
          } : undefined}
        />
      ))}
      {isResourcesOpen && <ActiveResourcesDialog resources={resources} onClose={() => setIsResourcesOpen(false)} />}
    </section>
  );
}

function ActiveResourcesDialog({
  resources,
  onClose,
}: {
  resources: ReturnType<typeof useQuery<AwsResourceResponse>>;
  onClose: () => void;
}) {
  const data = resources.data;
  const activeEc2 = data?.ec2Instances.filter((item) => item.state?.toLowerCase() === "running") ?? [];
  const activeRds = data?.rdsDatabases.filter((item) => item.status?.toLowerCase() === "available") ?? [];
  const activeCount = data?.summary.activeResources ?? 0;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/35 p-4" onClick={onClose}>
      <div
        aria-labelledby="active-resources-title"
        aria-modal="true"
        className="max-h-[min(720px,calc(100vh-2rem))] w-full max-w-2xl overflow-y-auto rounded-xl border border-border bg-card p-6 text-card-foreground shadow-2xl"
        role="dialog"
        onClick={(event) => event.stopPropagation()}
      >
        <div className="flex items-start justify-between gap-4">
          <div>
            <p className="text-xs font-medium uppercase tracking-wide text-success">Live inventory</p>
            <h2 className="mt-1 text-xl font-semibold" id="active-resources-title">Active Resources <span className="text-muted-foreground">({activeCount})</span></h2>
          </div>
          <button aria-label="Close active resources" className="rounded-lg p-2 text-muted-foreground transition-colors hover:bg-muted hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring" onClick={onClose} type="button"><X className="size-5" /></button>
        </div>

        {resources.isLoading && <div className="flex items-center justify-center py-12 text-sm text-muted-foreground"><LoaderCircle className="mr-2 size-4 animate-spin" />Fetching active resources...</div>}
        {resources.isError && <p className="py-12 text-center text-sm text-destructive">Unable to fetch active resources. Please verify the AWS connection.</p>}
        {data && <div className="mt-6 space-y-6">
          <ActiveResourceGroup title="EC2" count={activeEc2.length} items={activeEc2.map((item) => ({ name: item.name || "Running EC2 instance", details: [`Instance ID: ${item.instanceId}`, `Region: ${item.region}`] }))} />
          <ActiveResourceGroup title="RDS" count={activeRds.length} items={activeRds.map((item) => ({ name: `${item.engine} database`, details: [`DB identifier: ${item.identifier}`, `Region: ${item.region}`] }))} />
          <ActiveResourceGroup title="Lambda" count={data.lambdaFunctions.length} items={data.lambdaFunctions.map((item) => ({ name: "Active Lambda function", details: [`Function: ${item.functionName}`, `Region: ${item.region}`] }))} />
          <ActiveResourceGroup title="S3" count={data.s3Buckets.length} items={data.s3Buckets.map((item) => ({ name: "Active S3 bucket", details: [`Bucket: ${item.bucketName}`, `Region: ${item.region}`] }))} />
          <ActiveResourceGroup title="EBS" count={data.ebsVolumes.length} items={data.ebsVolumes.map((item) => ({ name: `${item.volumeType} volume`, details: [`Volume ID: ${item.volumeId}`, `${item.size} GiB`, item.attachedInstanceId ? `Attached to: ${item.attachedInstanceId}` : "Unattached"] }))} />
          <ActiveResourceGroup title="VPC" count={data.vpcs.length} items={data.vpcs.map((item) => ({ name: item.vpcId, details: [`CIDR: ${item.cidrBlock}`, `State: ${item.state}`, `Region: ${item.region}`] }))} />
          <ActiveResourceGroup title="Security groups" count={data.securityGroups.length} items={data.securityGroups.map((item) => ({ name: item.groupName || item.groupId, details: [`Group ID: ${item.groupId}`, `VPC: ${item.vpcId || "None"}`] }))} />
        </div>}
      </div>
    </div>
  );
}

function ActiveResourceGroup({ title, count, items }: { title: string; count: number; items: Array<{ name: string; details: string[] }> }) {
  return <section><div className="flex items-center justify-between border-b border-border pb-2"><h3 className="font-medium">{title}</h3><span className="text-xs text-muted-foreground">{count}</span></div>{items.length === 0 ? <p className="py-3 text-sm text-muted-foreground">No active resources</p> : <ul className="divide-y divide-border">{items.map((item) => <li className="py-3" key={`${title}-${item.details[0]}`}><p className="text-sm font-medium">{item.name}</p>{item.details.map((detail) => <p className="mt-1 text-xs text-muted-foreground" key={detail}>{detail}</p>)}</li>)}</ul>}</section>;
}

function formatCurrency(value: number, currency: string) {
  return new Intl.NumberFormat("en-US", { style: "currency", currency, minimumFractionDigits: 2 }).format(value);
}

function formatChange(value: number) {
  return `${value > 0 ? "+" : ""}${value}%`;
}

function budgetStatus(status: string) {
  return status === "OVER_BUDGET" ? "Over Budget" : status === "NEAR_LIMIT" ? "Near Limit" : "Within Limit";
}