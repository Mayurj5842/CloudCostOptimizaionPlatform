"use client";

import { LoaderCircle } from "lucide-react";
import { useQuery } from "@tanstack/react-query";
import {
  Card,
  CardContent,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { fetchAwsRecentActivity } from "@/services/aws-connection";
import { useCloudAccountStore } from "@/store/cloud-account-store";



export default function ActivityFeed() {
  const connectionToken = useCloudAccountStore((state) => state.connectionToken);
  const activity = useQuery({ queryKey: ["aws-recent-activity", connectionToken], queryFn: () => fetchAwsRecentActivity(connectionToken as string), enabled: Boolean(connectionToken), retry: false, staleTime: 5 * 60 * 1000 });
  return (
    <Card>
      <CardHeader>
        <CardTitle>Recent Cloud Activity</CardTitle>
      </CardHeader>

      <CardContent className="space-y-5">
        {activity.isLoading && <p className="flex items-center justify-center py-8 text-sm text-muted-foreground"><LoaderCircle className="mr-2 size-4 animate-spin" />Loading recent activity...</p>}
        {activity.isError && <p className="py-8 text-center text-sm text-muted-foreground">Recent cloud activity is unavailable for this account.</p>}
        {activity.data?.activities.length === 0 && <p className="py-8 text-center text-sm text-muted-foreground">No recent activity.</p>}
        {activity.data && activity.data.activities.length > 0 && activity.data.activities.map((item) => <div className="border-b border-border pb-4 last:border-0 last:pb-0" key={item.id}><div className="flex items-center justify-between gap-3"><p className="text-sm font-medium">{item.message}</p><time className="shrink-0 text-xs text-muted-foreground" dateTime={item.timestamp}>{formatTimestamp(item.timestamp)}</time></div><p className="mt-1 text-xs text-muted-foreground">{item.service} · {item.resourceId} · {item.region}</p></div>)}
      </CardContent>
    </Card>
  );
}

function formatTimestamp(value: string) {
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? "Not available" : date.toLocaleString();
}
