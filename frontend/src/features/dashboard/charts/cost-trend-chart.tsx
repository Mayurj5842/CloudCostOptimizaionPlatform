"use client";

import ReactECharts from "echarts-for-react";
import { useTheme } from "next-themes";
import { useQuery } from "@tanstack/react-query";

import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";

import { getChartTheme } from "@/lib/chart-theme";
import { fetchAwsCostTrend } from "@/services/aws-connection";
import { useCloudAccountStore } from "@/store/cloud-account-store";

export default function CostTrendChart({ range, onRangeChange }: { range: string; onRangeChange: (range: string) => void }) {
  const { resolvedTheme } = useTheme();
  const colors = typeof document === "undefined" ? null : getChartTheme();
  const connectionToken = useCloudAccountStore((state) => state.connectionToken);
  const trend = useQuery({ queryKey: ["aws-cost-trend", connectionToken, range], queryFn: () => fetchAwsCostTrend(connectionToken as string, range), enabled: Boolean(connectionToken), retry: false });
  const costTrendData = trend.data?.points ?? [];
  const currency = trend.data?.currency ?? "USD";
  const option = {
    tooltip: {
      trigger: "axis",
      backgroundColor: colors?.surface,
      borderColor: colors?.grid,
      textStyle: { color: colors?.tooltip },
      valueFormatter: (value: number) => new Intl.NumberFormat("en-US", { style: "currency", currency }).format(value),
    },

    grid: {
      left: 10,
      right: 10,
      top: 20,
      bottom: 20,
      containLabel: true,
    },

    xAxis: {
      type: "category",
      data: costTrendData.map((point) => point.period),
      boundaryGap: false,
      axisLine: { lineStyle: { color: colors?.grid } },
      axisLabel: { color: colors?.axis },
    },

    yAxis: {
      type: "value",
      axisLabel: { color: colors?.axis },
      splitLine: { lineStyle: { color: colors?.grid } },
    },

    series: [
      {
        data: costTrendData.map((point) => point.cost),
        type: "line",
        smooth: true,
        lineStyle: { color: colors?.primary, width: 2.5 },
        itemStyle: { color: colors?.primary },
        areaStyle: { color: colors?.primary, opacity: 0.14 },
      },
    ],
  };

  return (
    <Card>
      <CardHeader>
        <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between"><CardTitle>Cloud Cost Trend</CardTitle><div className="flex rounded-lg border border-border p-0.5">{["7D", "30D", "3M", "6M", "1Y"].map((item) => <button key={item} type="button" onClick={() => onRangeChange(item)} className={`px-2.5 py-1 text-xs font-medium ${range === item ? "rounded-md bg-primary text-primary-foreground" : "text-muted-foreground"}`}>{item}</button>)}</div></div>
      </CardHeader>

      <CardContent>
        {trend.isLoading && <p className="flex h-[320px] items-center justify-center text-sm text-muted-foreground">Loading cost data...</p>}
        {trend.isError && <div className="flex h-[320px] flex-col items-center justify-center gap-3 text-sm text-destructive"><p>Unable to load cost data.</p><button type="button" className="rounded-lg border border-border px-3 py-1 text-foreground" onClick={() => trend.refetch()}>Try again</button></div>}
        {trend.isSuccess && costTrendData.length === 0 && <p className="flex h-[320px] items-center justify-center text-sm text-muted-foreground">No billing data available for the selected period.</p>}
        {trend.isSuccess && costTrendData.length > 0 && <ReactECharts
    key={resolvedTheme}
    option={option}
    style={{
      height: "320px",
      width: "100%",
      minHeight: "320px",
    }}
    opts={{ renderer: "canvas" }}
  />}
</CardContent>
    </Card>
  );
}
