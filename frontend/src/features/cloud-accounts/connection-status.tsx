"use client";

import { CheckCircle2, CircleAlert, LoaderCircle, Unplug } from "lucide-react";

import type { ConnectionStatus } from "@/store/cloud-account-store";
import { cn } from "@/lib/utils";

const statusConfig = {
  disconnected: { label: "AWS Not Connected", icon: Unplug, className: "text-muted-foreground" },
  restoring: { label: "Restoring AWS connection", icon: LoaderCircle, className: "text-info" },
  connecting: { label: "Connecting to AWS", icon: LoaderCircle, className: "text-info" },
  connected: { label: "Connected", icon: CheckCircle2, className: "text-success" },
  error: { label: "Connection failed", icon: CircleAlert, className: "text-destructive" },
} as const;

export function ConnectionStatusBadge({ status, detail, compact = false, iconOnly = false }: { status: ConnectionStatus; detail?: string; compact?: boolean; iconOnly?: boolean }) {
  const { label, icon: Icon, className } = statusConfig[status];
  return <div aria-label={label} aria-live="polite" className={cn("flex items-center gap-2", className)}><Icon className={cn("size-4 shrink-0", status === "connecting" && "animate-spin")} />{!iconOnly && <span className={cn("font-medium", compact ? "text-xs" : "text-sm")}>{label}</span>}{detail && !iconOnly && <span className="text-xs text-muted-foreground">{detail}</span>}</div>;
}
