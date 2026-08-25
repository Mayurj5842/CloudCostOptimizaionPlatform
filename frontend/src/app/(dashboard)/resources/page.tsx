"use client";

import Link from "next/link";
import { RefreshCw, Server } from "lucide-react";
import { useQuery } from "@tanstack/react-query";

import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { fetchAwsResources, type AwsResourceResponse } from "@/services/aws-connection";
import { useCloudAccountStore } from "@/store/cloud-account-store";

const summaryCards = [
  ["Total AWS resources", "totalResources"],
  ["EC2 instances", "ec2Instances"],
  ["Running EC2", "runningEc2Instances"],
  ["EBS volumes", "ebsVolumes"],
  ["S3 buckets", "s3Buckets"],
  ["RDS databases", "rdsDatabases"],
  ["Lambda functions", "lambdaFunctions"],
  ["VPCs", "vpcs"],
  ["Security groups", "securityGroups"],
] as const;

export default function ResourcesPage() {
  const { account, connectionToken } = useCloudAccountStore();
  const resources = useQuery({
    queryKey: ["aws-resources", account?.accountId, connectionToken],
    queryFn: () => fetchAwsResources(connectionToken as string),
    enabled: Boolean(connectionToken),
    retry: false,
  });

  if (!account || !connectionToken) {
    return <EmptyResourcesState />;
  }

  return <div className="mx-auto max-w-7xl space-y-8">
    <header className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
      <div><p className="text-sm font-medium text-primary">AWS inventory</p><h1 className="mt-1 text-2xl font-semibold tracking-tight sm:text-3xl">Resources</h1><p className="mt-2 text-sm text-muted-foreground">Live resource inventory for {account.accountAlias} in {account.region}.</p></div>
      <Button disabled={resources.isFetching} onClick={() => resources.refetch()} variant="outline"><RefreshCw className={resources.isFetching ? "size-4 animate-spin" : "size-4"} />Refresh resources</Button>
    </header>

    {resources.isLoading && <LoadingState />}
    {resources.isError && <ErrorState onRetry={() => resources.refetch()} />}
    {resources.data && <ResourceInventory data={resources.data} />}
  </div>;
}

function EmptyResourcesState() {
  return <Card><CardContent className="flex min-h-[360px] flex-col items-center justify-center px-6 py-12 text-center"><Server className="size-10 text-primary" /><h1 className="mt-5 text-xl font-semibold">Connect an AWS account first</h1><p className="mt-2 max-w-md text-sm leading-6 text-muted-foreground">Resource discovery uses the short-lived connection created from your AWS account connection.</p><Button asChild className="mt-6"><Link href="/integrations">Go to Cloud Accounts</Link></Button></CardContent></Card>;
}

function LoadingState() {
  return <Card><CardContent className="flex min-h-[220px] items-center justify-center text-sm text-muted-foreground"><RefreshCw className="mr-2 size-4 animate-spin" />Fetching AWS resources...</CardContent></Card>;
}

function ErrorState({ onRetry }: { onRetry: () => void }) {
  return <Card><CardContent className="flex min-h-[220px] flex-col items-center justify-center text-center"><p className="font-medium text-destructive">Unable to fetch AWS resources.</p><p className="mt-2 text-sm text-muted-foreground">Please verify your AWS permissions and connection.</p><Button className="mt-5" onClick={onRetry} variant="outline">Try again</Button></CardContent></Card>;
}

function ResourceInventory({ data }: { data: AwsResourceResponse }) {
  return <>
    <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-5">{summaryCards.map(([label, key]) => <Card key={key}><CardContent className="p-5"><p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">{label}</p><p className="mt-2 text-2xl font-semibold">{data.summary[key]}</p></CardContent></Card>)}</div>
    <div className="grid gap-6 xl:grid-cols-2">
      <ResourceTable title="EC2 instances" columns={["Instance ID", "Name", "Type", "State", "Zone", "Private IP", "Public IP"]} rows={data.ec2Instances.map(item => [item.instanceId, item.name || "-", item.instanceType, item.state, item.availabilityZone || "-", item.privateIp || "-", item.publicIp || "-"])} />
      <ResourceTable title="EBS volumes" columns={["Volume ID", "Type", "Size", "State", "Zone", "Attached instance"]} rows={data.ebsVolumes.map(item => [item.volumeId, item.volumeType, `${item.size} GiB`, item.state, item.availabilityZone, item.attachedInstanceId || "Unattached"])} />
      <ResourceTable title="S3 buckets" columns={["Bucket name", "Created", "Region"]} rows={data.s3Buckets.map(item => [item.bucketName, formatDate(item.creationDate), item.region])} />
      <ResourceTable title="RDS databases" columns={["Identifier", "Engine", "Class", "Status", "Zone", "Endpoint"]} rows={data.rdsDatabases.map(item => [item.identifier, `${item.engine} ${item.engineVersion}`, item.instanceClass, item.status, item.availabilityZone || "-", item.endpoint || "-"])} />
      <ResourceTable title="Lambda functions" columns={["Function", "Runtime", "Memory", "Timeout", "Last modified"]} rows={data.lambdaFunctions.map(item => [item.functionName, item.runtime, item.memorySize ? `${item.memorySize} MB` : "-", item.timeout ? `${item.timeout}s` : "-", formatDate(item.lastModified)])} />
      <ResourceTable title="VPCs" columns={["VPC ID", "CIDR", "State", "Default", "Region"]} rows={data.vpcs.map(item => [item.vpcId, item.cidrBlock, item.state, item.defaultVpc ? "Yes" : "No", item.region])} />
      <ResourceTable title="Security groups" columns={["Group ID", "Name", "Description", "VPC ID"]} rows={data.securityGroups.map(item => [item.groupId, item.groupName, item.description || "-", item.vpcId || "-"])} />
    </div>
  </>;
}

function ResourceTable({ title, columns, rows }: { title: string; columns: string[]; rows: string[][] }) {
  return <Card className="overflow-hidden"><CardHeader><CardTitle className="text-base">{title} <span className="font-normal text-muted-foreground">({rows.length})</span></CardTitle></CardHeader><CardContent className="overflow-x-auto p-0"><table className="w-full min-w-[640px] text-left text-sm"><thead className="border-y border-border bg-muted/30 text-xs uppercase tracking-wide text-muted-foreground"><tr>{columns.map(column => <th className="whitespace-nowrap px-4 py-3 font-medium" key={column}>{column}</th>)}</tr></thead><tbody>{rows.length === 0 ? <tr><td className="px-4 py-6 text-muted-foreground" colSpan={columns.length}>No resources found.</td></tr> : rows.map((row, index) => <tr className="border-b border-border last:border-0" key={`${title}-${index}`}>{row.map((value, valueIndex) => <td className="max-w-[260px] truncate px-4 py-3" key={`${title}-${index}-${valueIndex}`} title={value}>{value}</td>)}</tr>)}</tbody></table></CardContent></Card>;
}

function formatDate(value: string | null) {
  if (!value) return "-";
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString();
}
