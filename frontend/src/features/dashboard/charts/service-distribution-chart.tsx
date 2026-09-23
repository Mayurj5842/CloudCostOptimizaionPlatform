"use client";

import ReactECharts from "echarts-for-react";
import { useTheme } from "@/providers/theme-provider";
import { useQuery } from "@tanstack/react-query";

import { Card, CardContent } from "@/components/ui/card";

import { getChartTheme } from "@/lib/chart-theme";
import { fetchAwsServiceDistribution } from "@/services/aws-connection";
import { useCloudAccountStore } from "@/store/cloud-account-store";

export default function ServiceDistributionChart({ range, month }: { range: string; month: string }) {
  const { resolvedTheme } = useTheme();
  const colors = typeof document === "undefined" ? null : getChartTheme();
  const connectionToken = useCloudAccountStore((state) => state.connectionToken);
  const distribution = useQuery({ queryKey: ["aws-service-distribution", connectionToken, range, month], queryFn: () => fetchAwsServiceDistribution(connectionToken as string, range, month), enabled: Boolean(connectionToken), retry: false, placeholderData: (previous) => previous });
  const serviceDistribution = distribution.data?.services ?? [];
  const currency = distribution.data?.currency ?? "USD";
  const totalCost = serviceDistribution.reduce((total, item) => total + item.cost, 0);
  const formatCost = (value: number) => new Intl.NumberFormat("en-US", {
    style: "currency",
    currency,
    minimumFractionDigits: 2,
    maximumFractionDigits: 6,
  }).format(value);
  const formattedTotalCost = formatCost(totalCost);
  const option = {
    tooltip: {
      trigger: "item",
      backgroundColor: colors?.surface,
      borderColor: colors?.grid,
      textStyle: { color: colors?.tooltip },
      formatter: (params: { name: string; value: number }) => {
        const percentage = totalCost > 0 ? (params.value / totalCost) * 100 : 0;
        return `${params.name}<br/>Cost: ${formatCost(params.value)}<br/>Percentage: ${percentage.toFixed(1)}%`;
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
        data: serviceDistribution.map((item) => ({ name: item.service, value: item.cost, percentage: item.percentage })),
      },
    ],
    graphic: {
      type: "group",
      left: "center",
      top: "middle",
      children: [
        {
          type: "text",
          left: "center",
          top: -14,
          style: {
            text: "Total Cost",
            fill: colors?.axis,
            fontSize: 13,
            fontWeight: 500,
            textAlign: "center",
          },
        },
        {
          type: "text",
          left: "center",
          top: 8,
          style: {
            text: formattedTotalCost,
            fill: colors?.tooltip,
            fontSize: 18,
            fontWeight: 700,
            textAlign: "center",
          },
        },
      ],
    },
  };

  return (
    <Card>
      <CardContent className="p-4">
        <h2 className="text-sm font-semibold">Service Distribution</h2>
        <div className="mt-3">
          {distribution.isLoading && <p className="flex h-[320px] items-center justify-center text-sm text-muted-foreground">Loading cost data...</p>}
          {distribution.isError && !distribution.data && <div className="flex h-[320px] flex-col items-center justify-center gap-3 text-sm text-destructive"><p>{distribution.error instanceof Error ? distribution.error.message : "Unable to load cost data."}</p><button type="button" className="rounded-lg border border-border px-3 py-1 text-foreground" onClick={() => distribution.refetch()}>Try again</button></div>}
          {distribution.data?.dataStatus === "NO_BILLING_DATA" && <p className="flex h-[320px] items-center justify-center text-sm text-muted-foreground">No CUR billing data available for the selected period.</p>}
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
        </div>
      </CardContent>
    </Card>
  );
}
