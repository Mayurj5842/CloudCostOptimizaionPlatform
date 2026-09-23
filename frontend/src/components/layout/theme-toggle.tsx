"use client";

import * as React from "react";
import { Laptop, Moon, Sun } from "lucide-react";
import { useTheme } from "@/providers/theme-provider";

import { Button } from "@/components/ui/button";
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip";

const modes = [
  { value: "light", label: "Light", icon: Sun },
  { value: "dark", label: "Dark", icon: Moon },
  { value: "system", label: "System", icon: Laptop },
] as const;

export function ThemeToggle() {
  const [mounted, setMounted] = React.useState(false);
  const { theme, setTheme } = useTheme();

  React.useEffect(() => {
    const frame = window.requestAnimationFrame(() => setMounted(true));
    return () => window.cancelAnimationFrame(frame);
  }, []);

  if (!mounted) {
    return <div aria-hidden="true" className="hidden h-9 w-[98px] sm:flex" />;
  }

  return (
    <div aria-label="Theme preference" className="hidden items-center rounded-lg border border-border bg-surface-muted p-0.5 sm:flex">
      {modes.map(({ value, label, icon: Icon }) => (
        <Tooltip key={value}><TooltipTrigger asChild><Button aria-label={`${label} theme`} aria-pressed={theme === value} className="size-7 rounded-md data-[active=true]:bg-card data-[active=true]:text-foreground data-[active=true]:shadow-sm" data-active={theme === value} onClick={() => setTheme(value)} size="icon-sm" variant="ghost"><Icon className="size-3.5" /></Button></TooltipTrigger><TooltipContent sideOffset={6}>{label}</TooltipContent></Tooltip>
      ))}
    </div>
  );
}
