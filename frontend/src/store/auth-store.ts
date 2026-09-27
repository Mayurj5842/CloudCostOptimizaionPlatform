"use client";

import { create } from "zustand";
import { createJSONStorage, persist } from "zustand/middleware";
import { getCurrentSession, logout as revokeSession, type AuthUser } from "@/services/auth";

let persistToLocalStorage = true;
let validationToken: string | null = null;
let validationPromise: Promise<boolean> | null = null;

type AuthState = {
  token: string | null;
  user: AuthUser | null;
  hydrated: boolean;
  validating: boolean;
  needsValidation: boolean;
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
  needsValidation: false,
  signIn: (token, user, remember) => {
    persistToLocalStorage = remember;
    set({ token, user, validating: false, needsValidation: false });
  },
  signOut: async () => {
    const token = get().token;
    try { if (token) await revokeSession(token); } finally {
      set({ token: null, user: null, validating: false, needsValidation: false });
      if (typeof window !== "undefined") {
        window.localStorage.removeItem("cloudcost-auth");
        window.sessionStorage.removeItem("cloudcost-auth");
      }
      persistToLocalStorage = true;
    }
  },
  validate: () => {
    const token = get().token;
    if (!token) {
      set({ validating: false, needsValidation: false });
      return Promise.resolve(false);
    }
    if (validationPromise && validationToken === token) return validationPromise;
    set({ validating: true });
    const request = getCurrentSession(token).then((session) => {
      if (get().token !== token) return false;
      set({ token: session.token, user: session.user, validating: false, needsValidation: false });
      return true;
    }).catch(() => {
      if (get().token === token) set({ token: null, user: null, validating: false, needsValidation: false });
      return false;
    });
    const pending = request.finally(() => {
      if (validationPromise === pending) {
        validationPromise = null;
        validationToken = null;
      }
    });
    validationToken = token;
    validationPromise = pending;
    return pending;
  },
  setHydrated: () => set((state) => ({ hydrated: true, needsValidation: Boolean(state.token) })),
}), {
  name: "cloudcost-auth",
  storage: createJSONStorage(() => ({
    getItem: (name) => {
      const persistentValue = window.localStorage.getItem(name);
      if (persistentValue !== null) {
        persistToLocalStorage = true;
        return persistentValue;
      }
      persistToLocalStorage = false;
      return window.sessionStorage.getItem(name);
    },
    setItem: (name, value) => {
      if (persistToLocalStorage) {
        window.localStorage.setItem(name, value);
        window.sessionStorage.removeItem(name);
      } else {
        window.sessionStorage.setItem(name, value);
        window.localStorage.removeItem(name);
      }
    },
    removeItem: (name) => {
      window.localStorage.removeItem(name);
      window.sessionStorage.removeItem(name);
    },
  })),
  partialize: (state) => ({ token: state.token, user: state.user }),
  onRehydrateStorage: () => (state) => state?.setHydrated(),
}));
