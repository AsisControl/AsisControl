"use client";
import { useEffect, useState } from "react";
import { api } from "@/core/api/client";
import { httpAuthService } from "./http-auth-service";
import { clearSession, readSession, saveSession } from "./session-storage";
import type { AuthSession, LoginCredentials } from "./types";
export function useAuth() {
  const [session, setSession] = useState<AuthSession | null>(null);
  const [isRestoringSession, setIsRestoringSession] = useState(true);
  useEffect(() => {
    const timer = window.setTimeout(() => {
      setSession(readSession());
      setIsRestoringSession(false);
    }, 0);
    const listener = () => setSession(null);
    window.addEventListener("asiscontrol:session-cleared", listener);
    return () => {
      window.clearTimeout(timer);
      window.removeEventListener("asiscontrol:session-cleared", listener);
    };
  }, []);
  useEffect(() => {
    if (!session?.expiresAt) return;
    const ms = new Date(session.expiresAt).getTime() - Date.now();
    const timer = window.setTimeout(clearSession, Math.max(0, ms));
    return () => window.clearTimeout(timer);
  }, [session]);
  useEffect(() => {
    if (!session) return;
    let lastActivity = Date.now();
    let lastPing = Date.now();
    const activity = () => {
      const now = Date.now();
      if (now - lastActivity >= 30 * 60 * 1000) {
        clearSession();
        return;
      }
      lastActivity = now;
      if (now - lastPing > 2 * 60 * 1000) {
        lastPing = now;
        void api("/api/auth/me").catch(() => {});
      }
    };
    const events = ["pointerdown", "keydown", "scroll", "pointermove"];
    events.forEach((name) =>
      window.addEventListener(name, activity, { passive: true }),
    );
    const timer = window.setInterval(() => {
      if (Date.now() - lastActivity >= 30 * 60 * 1000) clearSession();
    }, 15000);
    return () => {
      window.clearInterval(timer);
      events.forEach((name) => window.removeEventListener(name, activity));
    };
  }, [session]);
  async function login(credentials: LoginCredentials) {
    const result = await httpAuthService.login(credentials);
    if (result.ok) {
      saveSession(result.session);
      setSession(result.session);
    }
    return result;
  }
  async function logout() {
    try {
      await httpAuthService.logout();
    } catch {
      clearSession();
    }
    setSession(null);
  }
  return { session, isRestoringSession, login, logout };
}
