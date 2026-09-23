import DashboardLayout from "@/components/layout/dashboard-layout";
import { AuthGuard } from "@/components/auth/auth-guard";

export default function Layout({
  children,
}: {
  children: React.ReactNode;
}) {
  return <AuthGuard><DashboardLayout>{children}</DashboardLayout></AuthGuard>;
}