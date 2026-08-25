"use client";

import { Bell, Search } from "lucide-react";

import { Avatar, AvatarFallback } from "@/components/ui/avatar";

export default function Navbar() {
  return (
    <header className="flex h-16 items-center justify-between border-b border-border bg-surface px-4 sm:px-6 lg:px-8">
      {/* Left */}
      <div>
        <h1 className="text-xl font-semibold tracking-tight text-foreground">
          Dashboard
        </h1>

        <p className="text-sm text-muted-foreground">
          Monitor your cloud infrastructure.
        </p>
      </div>

      {/* Right */}
      <div className="flex items-center gap-4">
        <button aria-label="Search" className="rounded-lg border border-border p-2 transition-colors hover:bg-muted focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring">
          <
            Search className="h-5 w-5 text-muted-foreground" />
        </button>

        <button aria-label="Notifications" className="rounded-lg border border-border p-2 transition-colors hover:bg-muted focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring">
          <Bell className="h-5 w-5 text-muted-foreground" />
        </button>

        <Avatar>
          <AvatarFallback>AJ</AvatarFallback>
        </Avatar>
      </div>
    </header>
  );
}
