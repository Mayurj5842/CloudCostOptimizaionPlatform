"use client";

import { LucideIcon } from "lucide-react";

import {
  Card,
  CardContent,
} from "@/components/ui/card";

interface StatCardProps {
  title: string;
  value: string;
  change: string;
  description: string;
  icon: LucideIcon;
  interactive?: boolean;
  onClick?: () => void;
  onKeyDown?: (event: React.KeyboardEvent<HTMLDivElement>) => void;
}

export default function StatCard({
  title,
  value,
  change,
  description,
  icon: Icon,
  interactive = false,
  onClick,
  onKeyDown,
}: StatCardProps) {
  return (
    <Card
      className={interactive ? "cursor-pointer border-border transition-all duration-200 hover:border-[#86EFAC] hover:bg-[#F0FDF4] hover:shadow-[0_8px_24px_rgba(134,239,172,0.22)] focus-visible:border-[#86EFAC] focus-visible:bg-[#F0FDF4] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#86EFAC]/60" : "transition-colors hover:border-primary/40"}
      role={interactive ? "button" : undefined}
      tabIndex={interactive ? 0 : undefined}
      onClick={onClick}
      onKeyDown={onKeyDown}
    >
      <CardContent className="p-6">
        <div className="flex items-center justify-between">
          <Icon className="h-5 w-5 text-primary" />

          <span className="text-xs font-medium text-success">
            {change}
          </span>
        </div>

        <h3 className="mt-6 text-sm font-medium text-muted-foreground">
          {title}
        </h3>

        <p className="mt-2 text-3xl font-semibold tracking-tight text-foreground">
          {value}
        </p>

        <p className="mt-2 text-xs text-muted-foreground">
          {description}
        </p>
      </CardContent>
    </Card>
  );
}
