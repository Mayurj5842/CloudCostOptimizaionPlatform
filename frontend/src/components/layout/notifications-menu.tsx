"use client";

import * as React from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import axios from "axios";
import { AlertTriangle, Bell, Check, CircleAlert, Info } from "lucide-react";

import { Button } from "@/components/ui/button";
import { getAuthError } from "@/services/auth";
import {
  fetchHeaderNotifications,
  markAllHeaderNotificationsRead,
  markHeaderNotificationRead,
  type NotificationFeed,
} from "@/services/notifications";
import { useAuthStore } from "@/store/auth-store";
import { useCloudAccountStore } from "@/store/cloud-account-store";

export function NotificationsMenu() {
  const [open, setOpen] = React.useState(false);
  const [actionError, setActionError] = React.useState<string | null>(null);
  const token = useAuthStore((state) => state.token);
  const userId = useAuthStore((state) => state.user?.id);
  const validateSession = useAuthStore((state) => state.validate);
  const accountId = useCloudAccountStore((state) => state.account?.accountId);
  const queryClient = useQueryClient();
  const queryKey = ["header-notifications", userId, accountId, token] as const;

  const notificationsQuery = useQuery({
    queryKey,
    queryFn: () => fetchHeaderNotifications(token as string, accountId as string),
    enabled: Boolean(token && accountId),
    staleTime: 30_000,
    refetchInterval: 45_000,
    refetchOnWindowFocus: true,
    retry: false,
  });

  React.useEffect(() => {
    if (axios.isAxiosError(notificationsQuery.error) && notificationsQuery.error.response?.status === 401) {
      void validateSession();
    }
  }, [notificationsQuery.error, validateSession]);

  const markReadMutation = useMutation({
    mutationFn: (notificationId: string) => markHeaderNotificationRead(token as string, accountId as string, notificationId),
    onMutate: async (notificationId) => {
      await queryClient.cancelQueries({ queryKey });
      const previous = queryClient.getQueryData<NotificationFeed>(queryKey);
      if (previous) {
        const changed = previous.notifications.some((notification) => notification.id === notificationId && !notification.read);
        queryClient.setQueryData<NotificationFeed>(queryKey, {
          notifications: previous.notifications.map((notification) => notification.id === notificationId
            ? { ...notification, read: true }
            : notification),
          unreadCount: Math.max(0, previous.unreadCount - (changed ? 1 : 0)),
        });
      }
      setActionError(null);
      return { previous };
    },
    onError: (error, _id, context) => {
      if (context?.previous) queryClient.setQueryData(queryKey, context.previous);
      setActionError(getAuthError(error, "Unable to update notification"));
    },
    onSettled: () => queryClient.invalidateQueries({ queryKey }),
  });

  const markAllReadMutation = useMutation({
    mutationFn: () => markAllHeaderNotificationsRead(token as string, accountId as string),
    onMutate: async () => {
      await queryClient.cancelQueries({ queryKey });
      const previous = queryClient.getQueryData<NotificationFeed>(queryKey);
      if (previous) {
        queryClient.setQueryData<NotificationFeed>(queryKey, {
          notifications: previous.notifications.map((notification) => ({ ...notification, read: true })),
          unreadCount: 0,
        });
      }
      setActionError(null);
      return { previous };
    },
    onError: (error, _variables, context) => {
      if (context?.previous) queryClient.setQueryData(queryKey, context.previous);
      setActionError(getAuthError(error, "Unable to update notifications"));
    },
    onSettled: () => queryClient.invalidateQueries({ queryKey }),
  });

  const feed = notificationsQuery.data;
  const unreadCount = feed?.unreadCount ?? 0;

  return <div className="relative">
    <Button aria-expanded={open} aria-haspopup="dialog"
      aria-label={`Notifications, ${unreadCount} unread`}
      className="relative" onClick={() => setOpen((value) => !value)} size="icon" variant="ghost">
      <Bell className="size-4" />
      {unreadCount > 0 && <span className="absolute -right-1 -top-1 flex min-h-4 min-w-4 items-center justify-center rounded-full bg-destructive px-1 text-[10px] font-semibold leading-4 text-destructive-foreground ring-2 ring-background">{unreadCount}</span>}
    </Button>
    {open && <div aria-label="Notifications" className="absolute right-0 top-11 z-50 w-[min(24rem,calc(100vw-2rem))] rounded-lg border border-border bg-popover p-2 text-popover-foreground shadow-[var(--shadow-card)]" role="dialog">
      <div className="flex items-center justify-between gap-3 px-2 py-2">
        <div><p className="text-sm font-semibold">Notifications</p><p className="text-xs text-muted-foreground">{unreadCount} unread</p></div>
        {unreadCount > 0 && <Button disabled={markAllReadMutation.isPending} onClick={() => markAllReadMutation.mutate()} size="sm" variant="ghost">Mark all as read</Button>}
      </div>
      {actionError && <p className="px-2 py-1 text-xs text-destructive">{actionError}</p>}
      {!accountId && <p className="px-3 py-6 text-center text-sm text-muted-foreground">Connect an AWS account to view notifications.</p>}
      {accountId && notificationsQuery.isLoading && <p className="px-3 py-6 text-center text-sm text-muted-foreground">Loading notifications...</p>}
      {accountId && notificationsQuery.isError && <div className="flex items-center justify-center gap-2 px-3 py-6 text-sm text-destructive"><AlertTriangle className="size-4" />Unable to load notifications</div>}
      {accountId && !notificationsQuery.isLoading && !notificationsQuery.isError && feed?.notifications.length === 0 && <p className="px-3 py-6 text-center text-sm text-muted-foreground">No new notifications</p>}
      {accountId && feed && <div className="max-h-[min(28rem,70vh)] space-y-1 overflow-y-auto">
        {feed.notifications.map((notification) => <button key={notification.id}
          aria-label={`${notification.read ? "Read" : "Unread"}: ${notification.title}`}
          className={`flex w-full items-start gap-3 rounded-md p-3 text-left transition-colors hover:bg-muted focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring ${notification.read ? "opacity-75" : "bg-primary/5"}`}
          onClick={() => { if (!notification.read) markReadMutation.mutate(notification.id); }}>
          <SeverityIcon severity={notification.severity} />
          <span className="min-w-0 flex-1">
            <span className="flex items-start justify-between gap-2">
              <span className="block truncate text-sm font-medium">{notification.title}</span>
              {!notification.read && <span aria-label="Unread" className="mt-1 size-2 shrink-0 rounded-full bg-primary" />}
            </span>
            <span className="mt-1 block text-xs leading-5 text-muted-foreground">{notification.message}</span>
            <span className="mt-1.5 flex items-center gap-2 text-[11px] text-muted-foreground">
              <span>{formatNotificationTime(notification.createdAt)}</span><span aria-hidden="true">·</span><span>{formatType(notification.type)}</span>
            </span>
          </span>
          {notification.read && <Check aria-label="Read" className="mt-0.5 size-3.5 shrink-0 text-muted-foreground" />}
        </button>)}
      </div>}
    </div>}
  </div>;
}

function SeverityIcon({ severity }: { severity: "INFO" | "WARNING" | "CRITICAL" }) {
  const classes = severity === "CRITICAL" ? "text-destructive" : severity === "WARNING" ? "text-warning" : "text-primary";
  const Icon = severity === "INFO" ? Info : CircleAlert;
  return <Icon aria-label={`${severity.toLowerCase()} severity`} className={`mt-0.5 size-4 shrink-0 ${classes}`} />;
}

function formatType(type: string) {
  return type.toLowerCase().split("_").map((part) => part[0].toUpperCase() + part.slice(1)).join(" ");
}

function formatNotificationTime(value: string) {
  const date = new Date(value);
  const elapsedMinutes = Math.max(0, Math.floor((Date.now() - date.getTime()) / 60_000));
  if (Number.isNaN(elapsedMinutes)) return value;
  if (elapsedMinutes < 1) return "Just now";
  if (elapsedMinutes < 60) return `${elapsedMinutes} min ago`;
  const hours = Math.floor(elapsedMinutes / 60);
  if (hours < 24) return `${hours} hr ago`;
  const days = Math.floor(hours / 24);
  if (days < 7) return `${days} days ago`;
  return date.toLocaleDateString();
}
