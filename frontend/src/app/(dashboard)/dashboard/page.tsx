"use client";

import { useState } from "react";
import StatsGrid from "@/features/dashboard/stats-grid";
import CostTrendChart from "@/features/dashboard/charts/cost-trend-chart";
import ServiceDistributionChart from "@/features/dashboard/charts/service-distribution-chart";
import RecommendationPanel from "@/features/dashboard/recommendation-panel";
import ActivityFeed from "@/features/dashboard/activity-feed";

export default function DashboardPage() {
  const [range, setRange] = useState("30D");
  return (
    <div className="space-y-6 lg:space-y-8">
      <StatsGrid />
      <div className="grid grid-cols-1 gap-6 xl:grid-cols-3">
        <div className="xl:col-span-2"><CostTrendChart range={range} onRangeChange={setRange} /></div>
        <ServiceDistributionChart range={range} />
      </div>
      <RecommendationPanel />
      <ActivityFeed />
    </div>
  );
}