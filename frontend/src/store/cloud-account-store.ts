"use client";

import { create } from "zustand";
import { persist } from "zustand/middleware";

export type ConnectionStatus = "disconnected" | "restoring" | "connecting" | "connected" | "error";

export type CloudAccount = {
  provider: "AWS";
  accountAlias: string;
  accountId: string;
  arn: string;
  userId: string;
  region: string;
  lastVerified: string;
};

type CloudAccountState = {
  connectionStatus: ConnectionStatus;
  account: CloudAccount | null;
  connectionToken: string | null;
  setConnectionStatus: (status: ConnectionStatus) => void;
  connect: (account: CloudAccount, connectionToken: string) => void;
  restore: (account: CloudAccount, connectionToken?: string | null) => void;
  disconnect: () => void;
};

export const useCloudAccountStore = create<CloudAccountState>()(persist((set) => ({
  connectionStatus: "restoring",
  account: null,
  connectionToken: null,
  setConnectionStatus: (connectionStatus) => set({ connectionStatus }),
  connect: (account, connectionToken) => set({ account, connectionToken, connectionStatus: "connected" }),
  restore: (account, connectionToken) => set({ account, connectionToken: connectionToken ?? null, connectionStatus: "connected" }),
  disconnect: () => set({ account: null, connectionToken: null, connectionStatus: "disconnected" }),
}), {
  name: "cloud-account",
  partialize: (state) => ({ connectionStatus: state.connectionStatus, account: state.account, connectionToken: state.connectionToken }),
}));
