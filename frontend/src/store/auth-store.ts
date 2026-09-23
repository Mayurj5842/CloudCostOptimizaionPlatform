"use client";

import { create } from "zustand";
import { persist } from "zustand/middleware";
import { getCurrentSession, logout as revokeSession, type AuthUser } from "@/services/auth";

type AuthState = {
  token: string | null;
  user: AuthUser | null;
  hydrated: boolean;
  validating: boolean;
  signIn: (token: string, user: AuthUser, remember: boolean) => void;
  signOut: () => Promise<void>;
  validate: () => Promise<boolean>;
  setHydrated: () => void;
};

export const useAuthStore = create<AuthState>()(persist((set, get) => ({
  token: null,
  user: null,
  hydrated: false,
  validating: false,
  signIn: (token, user, remember) => {
    set({ token, user, validating: false });
    if (!remember && typeof window !== "undefined") window.localStorage.removeItem("cloudcost-auth");
  },
  signOut: async () => {
    const token = get().token;
    try { if (token) await revokeSession(token); } finally { set({ token: null, user: null, validating: false }); }
  },
  validate: async () => {
    const token = get().token;
    if (!token) return false;
    set({ validating: true });
    try {
      const session = await getCurrentSession(token);
      set({ token: session.token, user: session.user, validating: false });
      return true;
    } catch {
      set({ token: null, user: null, validating: false });
      return false;
    }
  },
  setHydrated: () => set({ hydrated: true }),
}), {
  name: "cloudcost-auth",
  partialize: (state) => ({ token: state.token, user: state.user }),
  onRehydrateStorage: () => (state) => state?.setHydrated(),
}));
