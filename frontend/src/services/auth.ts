import axios from "axios";

export type AuthUser = { id: string; username: string; email: string };
export type AuthResponse = { token: string; user: AuthUser };

type ApiResponse<T> = { success: boolean; message: string; data: T };
const apiUrl = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";

export async function login(identifier: string, password: string) {
  const response = await axios.post<ApiResponse<AuthResponse>>(`${apiUrl}/api/auth/login`, { identifier, password });
  return response.data.data;
}

export async function register(username: string, email: string, password: string) {
  const response = await axios.post<ApiResponse<AuthResponse>>(`${apiUrl}/api/auth/register`, { username, email, password });
  return response.data.data;
}

export async function getCurrentSession(token: string) {
  const response = await axios.get<ApiResponse<AuthResponse>>(`${apiUrl}/api/auth/me`, { headers: { Authorization: `Bearer ${token}` } });
  return response.data.data;
}

export async function logout(token: string) {
  await axios.post(`${apiUrl}/api/auth/logout`, undefined, { headers: { Authorization: `Bearer ${token}` } });
}

export function getAuthError(error: unknown, fallback: string) {
  if (axios.isAxiosError<{ message?: string; detail?: string; title?: string }>(error)) {
    return error.response?.data?.message ?? error.response?.data?.detail ?? error.response?.data?.title ?? fallback;
  }
  return error instanceof Error ? error.message : fallback;
}
