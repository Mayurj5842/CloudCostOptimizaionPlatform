import axios from "axios";

const apiUrl = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";

export type NotificationPreferences = {
  awsAccountId: string;
  emailAddress: string;
  emailNotificationsEnabled: boolean;
  budgetAlertEnabled: boolean;
  budgetAlertThreshold: number;
  budgetExceededEnabled: boolean;
  costAnomalyEnabled: boolean;
  monthlySummaryEnabled: boolean;
};

export type NotificationHistory = {
  id: string;
  awsAccountId: string;
  notificationType: string;
  recipient: string;
  subject: string;
  status: "PENDING" | "SENT" | "FAILED";
  errorMessage: string | null;
  createdAt: string;
  sentAt: string | null;
};

export type HeaderNotification = {
  id: string;
  type: "BUDGET_THRESHOLD" | "BUDGET_EXCEEDED" | "COST_ANOMALY" | "MONTHLY_SUMMARY";
  title: string;
  message: string;
  severity: "INFO" | "WARNING" | "CRITICAL";
  createdAt: string;
  read: boolean;
};

export type NotificationFeed = {
  notifications: HeaderNotification[];
  unreadCount: number;
};

type ApiResponse<T> = { success: boolean; message: string; data: T };

function authHeaders(token: string) {
  return { Authorization: `Bearer ${token}` };
}

export async function fetchNotificationPreferences(token: string, awsAccountId: string) {
  const response = await axios.get<ApiResponse<NotificationPreferences>>(`${apiUrl}/api/notifications/preferences`, {
    headers: authHeaders(token),
    params: { awsAccountId },
  });
  return response.data.data;
}

export async function saveNotificationPreferences(token: string, preferences: NotificationPreferences) {
  const response = await axios.put<ApiResponse<NotificationPreferences>>(`${apiUrl}/api/notifications/preferences`, preferences, {
    headers: authHeaders(token),
  });
  return response.data.data;
}

export async function sendNotificationTest(token: string, awsAccountId: string) {
  const response = await axios.post<ApiResponse<null>>(`${apiUrl}/api/notifications/test`, undefined, {
    headers: authHeaders(token),
    params: { awsAccountId },
  });
  return response.data.message;
}

export async function fetchNotificationHistory(token: string, awsAccountId: string) {
  const response = await axios.get<ApiResponse<NotificationHistory[]>>(`${apiUrl}/api/notifications/history`, {
    headers: authHeaders(token),
    params: { awsAccountId },
  });
  return response.data.data;
}

export async function fetchHeaderNotifications(token: string, awsAccountId: string) {
  const response = await axios.get<ApiResponse<NotificationFeed>>(`${apiUrl}/api/notifications`, {
    headers: authHeaders(token),
    params: { awsAccountId },
  });
  return response.data.data;
}

export async function markHeaderNotificationRead(token: string, awsAccountId: string, notificationId: string) {
  await axios.patch<ApiResponse<null>>(`${apiUrl}/api/notifications/${notificationId}/read`, undefined, {
    headers: authHeaders(token),
    params: { awsAccountId },
  });
}

export async function markAllHeaderNotificationsRead(token: string, awsAccountId: string) {
  await axios.patch<ApiResponse<number>>(`${apiUrl}/api/notifications/read-all`, undefined, {
    headers: authHeaders(token),
    params: { awsAccountId },
  });
}