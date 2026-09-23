"use client";

import { useMemo } from "react";

export type MonthSelectorProps = {
  value: string;
  onChange: (month: string) => void;
  className?: string;
};

export function MonthSelector({ value, onChange, className = "" }: MonthSelectorProps) {
  const options = useMemo(() => {
    const now = new Date();
    const months: string[] = [];
    const start = new Date(now.getFullYear() - 1, 0, 1);
    for (let index = 0; index < 24; index += 1) {
      const date = new Date(start.getFullYear(), start.getMonth() + index, 1);
      const monthKey = `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}`;
      months.push(monthKey);
    }
    return months.map((monthKey) => {
      const [year, month] = monthKey.split("-").map(Number);
      return {
        value: monthKey,
        label: new Intl.DateTimeFormat("en-US", { month: "long", year: "numeric" }).format(new Date(year, month - 1, 1)),
      };
    });
  }, []);

  return (
    <label className={`flex items-center gap-2 text-sm font-medium text-foreground ${className}`.trim()}>
      <span>Select Month:</span>
      <select
        aria-label="Select month"
        value={value}
        onChange={(event) => onChange(event.target.value)}
        className="h-10 rounded-lg border border-input bg-background px-3 text-sm text-foreground shadow-sm focus:outline-none focus:ring-2 focus:ring-ring"
      >
        {options.map((option) => (
          <option key={option.value} value={option.value}>
            {option.label}
          </option>
        ))}
      </select>
    </label>
  );
}
