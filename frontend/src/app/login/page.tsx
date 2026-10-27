"use client";

import Link from "next/link";
import { Eye, EyeOff, LoaderCircle, LockKeyhole, Mail, ShieldCheck, UserRound } from "lucide-react";
import { FormEvent, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { getAuthError, login, register } from "@/services/auth";
import { useAuthStore } from "@/store/auth-store";

export default function LoginPage() {
  const router = useRouter();
  const { token, hydrated, signIn } = useAuthStore();
  const [mode, setMode] = useState<"login" | "register">("login");
  const [identifier, setIdentifier] = useState("");
  const [username, setUsername] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [remember, setRemember] = useState(true);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  useEffect(() => { if (hydrated && token) router.replace("/dashboard"); }, [hydrated, router, token]);

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    setError("");
    if (mode === "login" && !identifier.trim()) return setError("Enter your username or email.");
    if (mode === "register" && (!username.trim() || !email.trim())) return setError("Complete your username and email.");
    if (password.length < 8) return setError("Password must be at least 8 characters.");
    setBusy(true);
    try {
      const result = mode === "login" ? await login(identifier, password) : await register(username, email, password);
      signIn(result.token, result.user, remember);
      router.replace("/dashboard");
    } catch (submitError) {
      setError(getAuthError(submitError, "We could not reach CloudCost AI. Check your connection and try again."));
    } finally { setBusy(false); }
  };

  return <main data-login-page className="min-h-screen bg-[radial-gradient(circle_at_top_right,_#d8f5f1,_transparent_42%),linear-gradient(135deg,#f8fbfa_0%,#edf4f4_100%)] px-4 py-8 text-slate-900 sm:px-8 lg:px-16">
    <div className="mx-auto grid min-h-[calc(100vh-4rem)] max-w-6xl items-center gap-12 lg:grid-cols-[1fr_430px]">
      <section className="hidden lg:block"><Link href="/" className="text-sm font-semibold tracking-[0.2em] text-teal-800">CLOUDCOST AI</Link><h1 className="mt-8 max-w-xl text-6xl font-semibold leading-[1.02] tracking-tight text-slate-950">Make every cloud dollar explain itself.</h1><p className="mt-6 max-w-lg text-lg leading-8 text-slate-600">A focused workspace for AWS spend, billing intelligence, and decisions your team can act on.</p><div className="mt-10 flex gap-6 text-sm text-slate-600"><span className="flex items-center gap-2"><ShieldCheck className="size-4 text-teal-700" /> Private by design</span><span className="flex items-center gap-2"><LockKeyhole className="size-4 text-teal-700" /> BCrypt protected</span></div></section>
      <section className="rounded-2xl border border-slate-200 bg-white p-6 shadow-xl shadow-slate-200/60 sm:p-9">
        <div className="mb-8 lg:hidden"><Link href="/" className="text-sm font-semibold tracking-[0.2em] text-teal-800">CLOUDCOST AI</Link></div>
        <div className="mb-7"><p className="text-sm font-medium text-teal-700">{mode === "login" ? "Welcome back" : "Get started"}</p><h2 className="mt-2 text-3xl font-semibold tracking-tight">{mode === "login" ? "Sign in to your workspace" : "Create your workspace"}</h2><p className="mt-2 text-sm text-slate-500">{mode === "login" ? "Continue where your cloud decisions left off." : "Use at least 8 characters for your password."}</p></div>
        <div className="mb-6 grid grid-cols-2 rounded-lg bg-slate-100 p-1 text-sm font-medium"><button type="button" onClick={() => { setMode("login"); setError(""); }} className={`rounded-md py-2 ${mode === "login" ? "bg-white text-slate-900 shadow-sm" : "text-slate-600"}`}>Sign in</button><button type="button" onClick={() => { setMode("register"); setError(""); }} className={`rounded-md py-2 ${mode === "register" ? "bg-white text-slate-900 shadow-sm" : "text-slate-600"}`}>Register</button></div>
        <form className="space-y-4" onSubmit={submit} noValidate>
          {mode === "register" && <><label className="block text-sm font-medium">Username<div className="relative mt-2"><UserRound className="absolute left-3 top-3 size-4 text-slate-400" /><input value={username} onChange={(event) => setUsername(event.target.value)} className="h-11 w-full rounded-lg border border-slate-300 bg-white pl-10 pr-3 outline-none ring-teal-600 transition focus:ring-2" placeholder="maya" autoComplete="username" /></div></label><label className="block text-sm font-medium">Email<div className="relative mt-2"><Mail className="absolute left-3 top-3 size-4 text-slate-400" /><input type="email" value={email} onChange={(event) => setEmail(event.target.value)} className="h-11 w-full rounded-lg border border-slate-300 bg-white pl-10 pr-3 outline-none ring-teal-600 transition focus:ring-2" placeholder="you@company.com" autoComplete="email" /></div></label></>}
          {mode === "login" && <label className="block text-sm font-medium">Email or username<div className="relative mt-2"><UserRound className="absolute left-3 top-3 size-4 text-slate-400" /><input value={identifier} onChange={(event) => setIdentifier(event.target.value)} className="h-11 w-full rounded-lg border border-slate-300 bg-white pl-10 pr-3 outline-none ring-teal-600 transition focus:ring-2" placeholder="you@company.com" autoComplete="username" /></div></label>}
          <label className="block text-sm font-medium">Password<div className="relative mt-2"><LockKeyhole className="absolute left-3 top-3 size-4 text-slate-400" /><input type={showPassword ? "text" : "password"} value={password} onChange={(event) => setPassword(event.target.value)} className="h-11 w-full rounded-lg border border-slate-300 bg-white pl-10 pr-11 outline-none ring-teal-600 transition focus:ring-2" placeholder="Your password" autoComplete={mode === "login" ? "current-password" : "new-password"} /><button type="button" aria-label={showPassword ? "Hide password" : "Show password"} onClick={() => setShowPassword((value) => !value)} className="absolute right-3 top-3 text-slate-400 hover:text-slate-700">{showPassword ? <EyeOff className="size-4" /> : <Eye className="size-4" />}</button></div></label>
          {mode === "login" && <div className="flex items-center justify-between text-sm"><label className="flex items-center gap-2 text-slate-600"><input type="checkbox" checked={remember} onChange={(event) => setRemember(event.target.checked)} className="size-4 accent-teal-700" /> Remember me</label><button type="button" onClick={() => setError("Password reset is not configured yet. Contact your workspace administrator.")} className="font-medium text-teal-700 hover:text-teal-900">Forgot password?</button></div>}
          {error && <p role="alert" className="rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">{error}</p>}
          <button type="submit" disabled={busy} className="flex h-11 w-full items-center justify-center gap-2 rounded-lg bg-teal-700 font-semibold text-white transition hover:bg-teal-800 disabled:cursor-not-allowed disabled:opacity-60">{busy && <LoaderCircle className="size-4 animate-spin" />}{busy ? "Please wait..." : mode === "login" ? "Sign in" : "Create account"}</button>
        </form>
        <p className="mt-6 text-center text-xs text-slate-400">Your AWS connection is kept separate from this application login.</p>
      </section>
    </div>
  </main>;
}
