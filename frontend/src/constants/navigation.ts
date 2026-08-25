import {
  BarChart3,
  BellRing,
  Bot,
  CircleHelp,
  CloudCog,
  FileBarChart,
  Gauge,
  LayoutDashboard,
  Settings,
  Server,
  Sparkles,
  WalletCards,
} from "lucide-react";

export const navigation = [
  { title: "Main", items: [{ title: "Overview", href: "/dashboard", icon: LayoutDashboard }] },
  {
    title: "Analytics",
    items: [
      { title: "Cost Analytics", href: "/billing", icon: BarChart3 },
      { title: "Resources", href: "/resources", icon: Server },
      // { title: "Forecast", href: "/forecast", icon: Gauge },
    ],
  },
  {
    title: "Optimization",
    items: [
      { title: "AI Insights", href: "/insights", icon: Bot },
      { title: "Recommendations", href: "/recommendations", icon: Sparkles },
      { title: "Budgets & Alerts", href: "/budgets", icon: WalletCards },
    ],
  },
  { title: "Integrations", items: [{ title: "Cloud Accounts", href: "/integrations", icon: CloudCog }] },
  { title: "Reporting", items: [{ title: "Reports", href: "/reports", icon: FileBarChart }] },
  {
    title: "Other",
    items: [
      { title: "Settings", href: "/settings", icon: Settings },
      { title: "Help & Documentation", href: "/help", icon: CircleHelp },
    ],
  },
] as const;

export const sidebarFooter = { label: "AWS Connected", detail: "Last synced 4 minutes ago", icon: BellRing };
