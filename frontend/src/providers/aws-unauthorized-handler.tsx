"use client";

import * as React from "react";
import axios from "axios";
import { useRouter } from "next/navigation";
import { useCloudAccountStore } from "@/store/cloud-account-store";

const awsProtectedPrefixes = ["/api/aws/", "/api/budgets", "/api/alerts", "/api/billing", "/api/recommendations", "/api/system/diagnostics"];
const awsConnectionExemptRoutes = ["/api/aws/connect", "/api/aws/connection", "/api/aws/disconnect"];

export function AwsUnauthorizedHandler({ children }: { children: React.ReactNode }) {
  const router = useRouter();

  React.useEffect(() => {
    const interceptor = axios.interceptors.response.use(
      (response) => response,
      (error) => {
        if (axios.isAxiosError(error) && error.response?.status === 401) {
          const url = error.config?.url ?? "";
          const message = typeof error.response.data?.message === "string" ? error.response.data.message : "";
          const isProtected = awsProtectedPrefixes.some((prefix) => url.includes(prefix));
          const isExempt = awsConnectionExemptRoutes.some((route) => url.includes(route));
          if (isProtected && !isExempt && message.includes("AWS connection")) {
            useCloudAccountStore.getState().disconnect();
            if (window.location.pathname !== "/integrations") {
              router.replace("/integrations");
            }
          }
        }
        return Promise.reject(error);
      }
    );
    return () => axios.interceptors.response.eject(interceptor);
  }, [router]);

  return children;
}
