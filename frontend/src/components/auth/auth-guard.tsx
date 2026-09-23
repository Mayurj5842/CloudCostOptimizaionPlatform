"use client";

import { LoaderCircle } from "lucide-react";
import { usePathname, useRouter } from "next/navigation";
import { useEffect } from "react";
import { useAuthStore } from "@/store/auth-store";

export function AuthGuard({ children }: { children: React.ReactNode }) {
  const router = useRouter();
  const pathname = usePathname();
  const { hydrated, token, user, validating, validate } = useAuthStore();

  useEffect(() => {
    if (hydrated && token && !user && !validating) void validate();
  }, [hydrated, token, user, validating, validate]);

  useEffect(() => {
    if (hydrated && !token) router.replace(`/login?next=${encodeURIComponent(pathname)}`);
  }, [hydrated, pathname, router, token]);

  if (!hydrated || validating || !token || !user) {
    return <div className="flex min-h-screen items-center justify-center bg-background"><LoaderCircle className="size-6 animate-spin text-primary" /></div>;
  }
  return children;
}
