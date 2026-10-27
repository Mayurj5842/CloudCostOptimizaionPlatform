"use client";

import { LoaderCircle } from "lucide-react";
import { usePathname, useRouter } from "next/navigation";
import { useEffect } from "react";
import { useQuery } from "@tanstack/react-query";
import { getAwsConnection } from "@/services/aws-connection";
import { useAuthStore } from "@/store/auth-store";
import { useCloudAccountStore } from "@/store/cloud-account-store";

const awsProtectedRoutes = [
  "/dashboard",
  "/billing",
  "/cost-analytics",
  "/resources",
  "/insights",
  "/ai-insights",
  "/budgets",
  "/recommendations",
  "/optimization",
  "/reports",
  "/health-center",
];

function isAwsProtectedRoute(pathname: string) {
  return awsProtectedRoutes.some((route) => pathname === route || pathname.startsWith(`${route}/`));
}

export function AuthGuard({ children }: { children: React.ReactNode }) {
  const router = useRouter();
  const pathname = usePathname();
  const { hydrated, token, user, validating, needsValidation, validate } = useAuthStore();
  const { account, connectionStatus, connectionToken, setConnectionStatus } = useCloudAccountStore();
  const awsProtected = isAwsProtectedRoute(pathname);
  const awsConnection = useQuery({
    queryKey: ["aws-connection", connectionToken],
    queryFn: () => getAwsConnection(connectionToken as string),
    enabled: awsProtected && Boolean(connectionToken),
    retry: 1,
  });
  useEffect(() => {
    if (hydrated && token && needsValidation && !validating) void validate();
  }, [hydrated, token, needsValidation, validating, validate]);

  useEffect(() => {
    if (hydrated && !token) router.replace(`/login?next=${encodeURIComponent(pathname)}`);
  }, [hydrated, pathname, router, token]);

  useEffect(() => {
    if (connectionStatus === "restoring" && !connectionToken) {
      setConnectionStatus("disconnected");
    }
  }, [connectionStatus, connectionToken, setConnectionStatus]);

  useEffect(() => {
    if (!hydrated || !token || !awsProtected) return;
    if (!connectionToken && connectionStatus !== "restoring") router.replace("/integrations");
    if (connectionToken && (awsConnection.isError || (awsConnection.isSuccess && !awsConnection.data))) {
      router.replace("/integrations");
    }
  }, [awsConnection.data, awsConnection.isError, awsConnection.isSuccess, awsProtected, connectionStatus, connectionToken, hydrated, router, token]);

  const verifyingAwsConnection = awsProtected && Boolean(connectionToken) && (
    (!awsConnection.isSuccess && !awsConnection.isError)
    || awsConnection.isFetching
    || (Boolean(awsConnection.data) && (!account || connectionStatus !== "connected"))
  );
  const awsConnectionVerified = Boolean(
    connectionToken && account && connectionStatus === "connected" && awsConnection.isSuccess
      && !awsConnection.isFetching && awsConnection.data,
  );

  if (!hydrated || validating || needsValidation || !token || !user || verifyingAwsConnection || (awsProtected && !awsConnectionVerified)) {
    return <div className="flex min-h-screen items-center justify-center bg-background"><LoaderCircle className="size-6 animate-spin text-primary" /></div>;
  }
  return children;
}
