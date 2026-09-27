"use client";

import { ArrowLeft, Mail, UserCircle2, BadgeCheck } from "lucide-react";
import { useRouter } from "next/navigation";

import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { getCurrentUserProfile } from "@/lib/current-user";
import { AuthGuard } from "@/components/auth/auth-guard";
import { useAuthStore } from "@/store/auth-store";

export default function ProfilePage() {
  const router = useRouter();
  const authenticatedUser = useAuthStore((state) => state.user);
  const user = getCurrentUserProfile(authenticatedUser);

  const handleGoBack = () => {
    if (typeof window !== "undefined" && window.history.length > 1) {
      router.back();
      return;
    }

    router.push("/dashboard");
  };

  return <AuthGuard><div>
    <div className="mx-auto max-w-3xl space-y-6 px-4 py-8 sm:px-6 lg:px-8">
      <div className="flex items-center justify-between gap-3">
        <Button variant="ghost" size="sm" onClick={handleGoBack} className="gap-2">
          <ArrowLeft className="size-4" />
          Go Back
        </Button>
      </div>

      <Card>
        <CardHeader className="pb-4">
          <div className="flex items-center gap-3">
            <div className="flex size-12 items-center justify-center rounded-full bg-secondary text-lg font-semibold text-secondary-foreground">
              {user.name?.split(" ").map((part) => part[0]).slice(0, 2).join("").toUpperCase() || "U"}
            </div>
            <div>
              <CardTitle className="text-2xl font-semibold">{user.name || "User"}</CardTitle>
              <p className="text-sm text-muted-foreground">{user.role || "Member"}</p>
            </div>
          </div>
        </CardHeader>
        <CardContent className="space-y-5">
          <div className="grid gap-4 sm:grid-cols-2">
            <div className="rounded-lg border border-border bg-muted/30 p-4">
              <div className="mb-2 flex items-center gap-2 text-sm text-muted-foreground">
                <UserCircle2 className="size-4" />
                Name
              </div>
              <p className="text-base font-medium">{user.name || "Not available"}</p>
            </div>
            <div className="rounded-lg border border-border bg-muted/30 p-4">
              <div className="mb-2 flex items-center gap-2 text-sm text-muted-foreground">
                <Mail className="size-4" />
                Email
              </div>
              <p className="text-base font-medium">{user.email || "Not available"}</p>
            </div>
          </div>

          <div className="grid gap-4 sm:grid-cols-2">
            <div className="rounded-lg border border-border bg-muted/30 p-4">
              <div className="mb-2 flex items-center gap-2 text-sm text-muted-foreground">
                <UserCircle2 className="size-4" />
                Username
              </div>
              <p className="text-base font-medium">{user.username || "Not available"}</p>
            </div>
            <div className="rounded-lg border border-border bg-muted/30 p-4">
              <div className="mb-2 flex items-center gap-2 text-sm text-muted-foreground">
                <BadgeCheck className="size-4" />
                Account
              </div>
              <p className="text-base font-medium">{user.account || "Not available"}</p>
            </div>
          </div>
        </CardContent>
      </Card>
    </div>
  </div></AuthGuard>;
}
