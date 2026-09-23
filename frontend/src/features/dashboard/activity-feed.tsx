"use client";

import {
  Card,
  CardContent,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";



export default function ActivityFeed() {
  return (
    <Card>
      <CardHeader>
        <CardTitle>Recent Cloud Activity</CardTitle>
      </CardHeader>

      <CardContent className="space-y-5">
        <p className="py-8 text-center text-sm text-muted-foreground">No cloud activity feed is available for this account.</p>
      </CardContent>
    </Card>
  );
}
