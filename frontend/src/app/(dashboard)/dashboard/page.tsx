"use client";

import { useState } from "react";
import StatsGrid from "@/features/dashboard/stats-grid";
import CostTrendChart from "@/features/dashboard/charts/cost-trend-chart";
import ServiceDistributionChart from "@/features/dashboard/charts/service-distribution-chart";
import RecommendationPanel from "@/features/dashboard/recommendation-panel";
import ActivityFeed from "@/features/dashboard/activity-feed";

export default function DashboardPage() {
  const [range, setRange] = useState("30D");
  const [month, setMonth] = useState(() => new Date().toISOString().slice(0, 7));
  return (
    <div className="space-y-6 lg:space-y-8">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <div><h1 className="text-2xl font-semibold tracking-tight">Dashboard</h1><p className="text-sm text-muted-foreground">CUR billing and live AWS resource overview</p></div>
        <label className="flex items-center gap-2 text-sm font-medium"><span className="text-muted-foreground">Billing month</span><input className="h-9 rounded-lg border border-input bg-background px-3" type="month" value={month} onChange={(event) => setMonth(event.target.value)} /></label>
      </div>
      <StatsGrid month={month} />
      <div className="grid grid-cols-1 gap-6 xl:grid-cols-3">
        <div className="xl:col-span-2"><CostTrendChart range={range} month={month} onRangeChange={setRange} /></div>
        <ServiceDistributionChart range={range} month={month} />
      </div>
      <RecommendationPanel month={month} />
      <ActivityFeed />
    </div>
  );
}