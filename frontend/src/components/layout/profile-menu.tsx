"use client";

import * as React from "react";
import { useRouter } from "next/navigation";

import { Avatar, AvatarFallback } from "@/components/ui/avatar";
import { getCurrentUserProfile } from "@/lib/current-user";
import { useAuthStore } from "@/store/auth-store";
import { useCloudAccountStore } from "@/store/cloud-account-store";

const items = ["Profile", "Preferences", "Settings", "Sign out"];

export function ProfileMenu() {
  const router = useRouter();
  const [open, setOpen] = React.useState(false);
  const authenticatedUser = useAuthStore((state) => state.user);
  const user = getCurrentUserProfile(authenticatedUser);
  const signOut = useAuthStore((state) => state.signOut);
  const disconnectLocalAws = useCloudAccountStore((state) => state.disconnect);

  const handleSelect = (item: string) => {
    setOpen(false);

    if (item === "Profile") {
      router.push("/profile");
    }
    if (item === "Preferences" || item === "Settings") {
      router.push("/settings");
    }
    if (item === "Sign out") {
      void signOut().finally(() => {
        disconnectLocalAws();
        router.push("/login");
      });
    }
  };

  return <div className="relative"><button aria-expanded={open} aria-haspopup="menu" aria-label="Open user menu" className="rounded-full focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring" onClick={() => setOpen((value) => !value)}><Avatar><AvatarFallback className="bg-secondary text-xs font-semibold text-secondary-foreground">{(user.name || "U").split(" ").map((part) => part[0]).slice(0, 2).join("").toUpperCase()}</AvatarFallback></Avatar></button>{open && <div className="absolute right-0 top-11 z-50 w-56 rounded-xl border border-border bg-popover p-1.5 shadow-[var(--shadow-card)]" role="menu"><div className="border-b border-border px-2.5 py-2"><p className="text-sm font-medium">{user.name || "User"}</p><p className="text-xs text-muted-foreground">{user.email || "Email unavailable"}</p></div>{items.map((item) => <button className="mt-1 w-full rounded-md px-2.5 py-2 text-left text-sm hover:bg-muted focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring" key={item} onClick={() => handleSelect(item)} role="menuitem">{item}</button>)}</div>}</div>;
}
