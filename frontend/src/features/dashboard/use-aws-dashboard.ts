"use client";

import { useQuery } from "@tanstack/react-query";

import { fetchAwsDashboard } from "@/services/aws-connection";
import { useCloudAccountStore } from "@/store/cloud-account-store";

export function useAwsDashboard(month?: string) {
  const connectionToken = useCloudAccountStore((state) => state.connectionToken);
  return useQuery({
    queryKey: ["aws-dashboard", connectionToken, month],
    queryFn: () => fetchAwsDashboard(connectionToken as string, month),
    enabled: Boolean(connectionToken),
    staleTime: 5 * 60 * 1000,
    retry: false,
  });
}