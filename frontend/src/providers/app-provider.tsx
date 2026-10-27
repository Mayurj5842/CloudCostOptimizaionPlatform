"use client";

import * as React from "react";
import { useQuery } from "@tanstack/react-query";
import { QueryProvider } from "./query-provider";

import { TooltipProvider } from "@/components/ui/tooltip";
import { AwsUnauthorizedHandler } from "./aws-unauthorized-handler";
import { getAwsConnection } from "@/services/aws-connection";
import { useCloudAccountStore } from "@/store/cloud-account-store";

interface AppProviderProps {
  children: React.ReactNode;
}

export function AppProvider({ children }: AppProviderProps) {
  return (
    <QueryProvider>
      <AwsConnectionRestorer>
        <AwsUnauthorizedHandler>
          <TooltipProvider>{children}</TooltipProvider>
        </AwsUnauthorizedHandler>
      </AwsConnectionRestorer>
    </QueryProvider>
  );
}

function AwsConnectionRestorer({ children }: AppProviderProps) {
  const restore = useCloudAccountStore((state) => state.restore);
  const disconnect = useCloudAccountStore((state) => state.disconnect);
  const setConnectionStatus = useCloudAccountStore((state) => state.setConnectionStatus);
  const connectionToken = useCloudAccountStore((state) => state.connectionToken);
  const query = useQuery({ queryKey: ["aws-connection", connectionToken], queryFn: () => getAwsConnection(connectionToken as string), enabled: Boolean(connectionToken), retry: 1 });

  React.useEffect(() => {
    if (query.isSuccess) {
      if (query.data) {
        restore({ provider: "AWS", accountAlias: query.data.accountAlias, accountId: query.data.accountId, arn: query.data.arn, userId: query.data.userId, region: query.data.region, lastVerified: "Just now" }, query.data.connectionToken);
      } else {
        disconnect();
      }
    } else if (query.isError) {
      setConnectionStatus("error");
    }
  }, [disconnect, query.data, query.isError, query.isSuccess, restore, setConnectionStatus]);

  return children;
}
