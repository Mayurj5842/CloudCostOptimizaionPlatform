"use client";

import Link from "next/link";
import { useState } from "react";
import { RefreshCw, Server } from "lucide-react";
import { useQuery } from "@tanstack/react-query";

import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { fetchAwsResources, type AwsResourceResponse } from "@/services/aws-connection";
import { useCloudAccountStore } from "@/store/cloud-account-store";

const summaryCards = [["Total AWS resources", "all", "totalResources"], ["EC2 instances", "ec2", "ec2Instances"], ["Running EC2", "ec2", "runningEc2Instances"], ["EBS volumes", "ebs", "ebsVolumes"], ["Unused volumes", "ebs", "unusedVolumes"], ["S3 buckets", "s3", "s3Buckets"], ["RDS databases", "rds", "rdsDatabases"], ["Lambda functions", "lambda", "lambdaFunctions"], ["VPCs", "vpc", "vpcs"], ["Security groups", "security-groups", "securityGroups"], ["Healthy resources", "all", "healthyResources"]] as const;

export default function ResourcesPage() {
  const { account, connectionToken } = useCloudAccountStore();
  const [selectedType, setSelectedType] = useState("all");
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
    {resources.data && <ResourceInventory data={resources.data} selectedType={selectedType} onSelect={setSelectedType} />}
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

function ResourceInventory({ data, selectedType, onSelect }: { data: AwsResourceResponse; selectedType: string; onSelect: (type: string) => void }) {
  const [search, setSearch] = useState("");
  const matches = (values: Array<string | number | null | undefined>) => !search || values.some(value => String(value ?? "").toLowerCase().includes(search.toLowerCase()));
  const ec2 = data.ec2Instances.filter(item => matches([item.instanceId, item.name, item.instanceType, item.state, item.region]));
  const ebs = data.ebsVolumes.filter(item => matches([item.volumeId, item.volumeType, item.state, item.availabilityZone, item.attachedInstanceId]));
  const s3 = data.s3Buckets.filter(item => matches([item.bucketName, item.region]));
  const rds = data.rdsDatabases.filter(item => matches([item.identifier, item.engine, item.status, item.region]));
  const lambda = data.lambdaFunctions.filter(item => matches([item.functionName, item.runtime, item.state, item.region]));
  const vpcs = data.vpcs.filter(item => matches([item.vpcId, item.name, item.cidrBlock, item.state, item.region]));
  const groups = data.securityGroups.filter(item => matches([item.groupId, item.groupName, item.description, item.vpcId, item.region]));
  const show = (type: string) => selectedType === "all" || selectedType === type;
  return <>
    <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-5">{summaryCards.map(([label, type, key]) => <button className="text-left" key={`${label}-${key}`} onClick={() => onSelect(type)} type="button"><Card className={selectedType === type ? "border-primary" : "transition-colors hover:border-primary/60"}><CardContent className="p-5"><p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">{label}</p><p className="mt-2 text-2xl font-semibold">{data.summary[key]}</p></CardContent></Card></button>)}</div>
    <div className="flex flex-col gap-3 sm:flex-row"><input className="h-10 flex-1 rounded-md border border-input bg-background px-3 text-sm" onChange={event => setSearch(event.target.value)} placeholder="Search current resources" value={search} /><select className="h-10 rounded-md border border-input bg-background px-3 text-sm" onChange={event => onSelect(event.target.value)} value={selectedType}><option value="all">All resource types</option><option value="ec2">EC2</option><option value="ebs">EBS</option><option value="s3">S3</option><option value="rds">RDS</option><option value="lambda">Lambda</option><option value="vpc">VPC</option><option value="security-groups">Security groups</option></select></div>
    {selectedType === "all" && <ResourceTable title="Recent resources" columns={["Type", "Resource", "Created/updated", "Status"]} rows={data.recentResources.map(item => [item.resourceType, item.name || item.resourceId, formatDate(item.timestamp), item.status])} />}
    <div className="grid gap-6 xl:grid-cols-2">
      {show("ec2") && <ResourceTable title="EC2 instances" columns={["Instance ID", "Name", "Type", "State", "Zone", "Private IP", "Public IP", "Platform"]} rows={ec2.map(item => [item.instanceId, item.name, item.instanceType, item.state, item.availabilityZone, item.privateIp, item.publicIp, item.platform])} />}
      {show("ebs") && <ResourceTable title="EBS volumes" columns={["Volume ID", "Type", "Size", "State", "Zone", "Attached instance", "Encrypted"]} rows={ebs.map(item => [item.volumeId, item.volumeType, item.size === null ? "Not available" : `${item.size} GiB`, item.state, item.availabilityZone, item.attachedInstanceId || "Unattached", item.encrypted === null ? "Not available" : item.encrypted ? "Yes" : "No"])} />}
      {show("s3") && <ResourceTable title="S3 buckets" columns={["Bucket name", "Created", "Region"]} rows={s3.map(item => [item.bucketName, formatDate(item.creationDate), item.region])} />}
      {show("rds") && <ResourceTable title="RDS databases" columns={["Identifier", "Engine", "Storage", "Status", "Multi-AZ", "Endpoint"]} rows={rds.map(item => [item.identifier, `${item.engine || "Not available"} ${item.engineVersion || ""}`.trim(), item.allocatedStorage ? `${item.allocatedStorage} GiB` : "Not available", item.status, item.multiAz === null ? "Not available" : item.multiAz ? "Yes" : "No", item.endpoint])} />}
      {show("lambda") && <ResourceTable title="Lambda functions" columns={["Function", "Runtime", "State", "Memory", "Timeout", "Last modified"]} rows={lambda.map(item => [item.functionName, item.runtime, item.state, item.memorySize ? `${item.memorySize} MB` : "Not available", item.timeout ? `${item.timeout}s` : "Not available", formatDate(item.lastModified)])} />}
      {show("vpc") && <ResourceTable title="VPCs" columns={["VPC ID", "Name", "CIDR", "State", "Default", "Region"]} rows={vpcs.map(item => [item.vpcId, item.name, item.cidrBlock, item.state, item.defaultVpc ? "Yes" : "No", item.region])} />}
      {show("security-groups") && <ResourceTable title="Security groups" columns={["Group ID", "Name", "Description", "VPC ID", "Region", "Inbound", "Outbound"]} rows={groups.map(item => [item.groupId, item.groupName, item.description, item.vpcId, item.region, item.inboundRuleCount, item.outboundRuleCount])} />}
    </div>
  </>;
}

function ResourceTable({ title, columns, rows }: { title: string; columns: string[]; rows: Array<Array<string | number | null | undefined>> }) {
  return <Card className="overflow-hidden"><CardHeader><CardTitle className="text-base">{title} <span className="font-normal text-muted-foreground">({rows.length})</span></CardTitle></CardHeader><CardContent className="overflow-x-auto p-0"><table className="w-full min-w-[640px] text-left text-sm"><thead className="border-y border-border bg-muted/30 text-xs uppercase tracking-wide text-muted-foreground"><tr>{columns.map(column => <th className="whitespace-nowrap px-4 py-3 font-medium" key={column}>{column}</th>)}</tr></thead><tbody>{rows.length === 0 ? <tr><td className="px-4 py-6 text-muted-foreground" colSpan={columns.length}>No resources found.</td></tr> : rows.map((row, index) => <tr className="border-b border-border last:border-0" key={`${title}-${index}`}>{row.map((value, valueIndex) => <td className="max-w-[260px] truncate px-4 py-3" key={`${title}-${index}-${valueIndex}`} title={String(value ?? "Not available")}>{value ?? "Not available"}</td>)}</tr>)}</tbody></table></CardContent></Card>;
}

function formatDate(value: string | null | undefined) {
  if (!value) return "Not available";
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString();
}
