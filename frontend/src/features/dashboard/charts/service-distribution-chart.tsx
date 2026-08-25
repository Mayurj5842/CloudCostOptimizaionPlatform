"use client";

import ReactECharts from "echarts-for-react";
import { useTheme } from "next-themes";
import { useQuery } from "@tanstack/react-query";

import {
  Card,
  CardContent,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";

import { getChartTheme } from "@/lib/chart-theme";
import { fetchAwsServiceDistribution } from "@/services/aws-connection";
import { useCloudAccountStore } from "@/store/cloud-account-store";

export default function ServiceDistributionChart({ range }: { range: string }) {
  const { resolvedTheme } = useTheme();
  const colors = typeof document === "undefined" ? null : getChartTheme();
  const connectionToken = useCloudAccountStore((state) => state.connectionToken);
  const distribution = useQuery({ queryKey: ["aws-service-distribution", connectionToken, range], queryFn: () => fetchAwsServiceDistribution(connectionToken as string, range), enabled: Boolean(connectionToken), retry: false });
  const serviceDistribution = distribution.data?.services ?? [];
  const currency = distribution.data?.currency ?? "USD";
  const option = {
    tooltip: {
      trigger: "item",
      backgroundColor: colors?.surface,
      borderColor: colors?.grid,
      textStyle: { color: colors?.tooltip },
      formatter: (params: { name: string; value: number; data: { percentage: number } }) => {
        const item = params;
        return `${item.name}<br/>Cost: ${new Intl.NumberFormat("en-US", { style: "currency", currency }).format(item.value)}<br/>Percentage: ${item.data.percentage.toFixed(1)}%`;
      },
    },

    legend: {
      bottom: 0,
      textStyle: {
        color: colors?.axis,
      },
      formatter: (name: string) => {
        const item = serviceDistribution.find((entry) => entry.service === name);
        return item ? `${name}  ${new Intl.NumberFormat("en-US", { style: "currency", currency }).format(item.cost)}  ${item.percentage.toFixed(1)}%` : name;
      },
    },

    series: [
      {
        type: "pie",
        radius: ["45%", "70%"],
        avoidLabelOverlap: false,
        itemStyle: {
          borderRadius: 8,
          borderColor: colors?.surface,
          borderWidth: 2,
        },
        color: colors ? [colors.primary, colors.secondary, colors.positive, colors.neutral, colors.negative] : undefined,
        label: {
          show: false,
        },
        emphasis: {
          label: {
            show: true,
            fontSize: 16,
            fontWeight: "bold",
          },
        },
        data: serviceDistribution.map((item) => ({ name: item.service, value: item.cost, percentage: item.percentage })),
      },
    ],
  };

  return (
    <Card>
      <CardHeader>
        <CardTitle>Service Distribution</CardTitle>
      </CardHeader>

      <CardContent>
        {distribution.isLoading && <p className="flex h-[320px] items-center justify-center text-sm text-muted-foreground">Loading cost data...</p>}
        {distribution.isError && <div className="flex h-[320px] flex-col items-center justify-center gap-3 text-sm text-destructive"><p>Unable to load cost data.</p><button type="button" className="rounded-lg border border-border px-3 py-1 text-foreground" onClick={() => distribution.refetch()}>Try again</button></div>}
        {distribution.isSuccess && serviceDistribution.length === 0 && <p className="flex h-[320px] items-center justify-center text-sm text-muted-foreground">No billing data available for the selected period.</p>}
        {distribution.isSuccess && serviceDistribution.length > 0 && <ReactECharts
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
