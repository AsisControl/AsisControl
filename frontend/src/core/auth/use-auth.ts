"use client";
import { useEffect, useSyncExternalStore } from "react";
import { message } from "@/core/api/client";
import { httpAuthService, mapAuth, type UserSummary } from "./http-auth-service";
import { clearSession, readSession, saveSession, subscribeSession, getSessionSnapshot, getServerSessionSnapshot, setSessionValidation, synchronizeSessionInvalidation } from "./session-storage";
import type { AuthSession, LoginCredentials } from "./types";

let validation: { session: AuthSession; promise: Promise<void> } | null = null;
let generation = 0;
let loggingOut = false;
// Changes only for a new login, not for refreshed /me user objects.
let sessionEpoch = 0;
let lastActivity = Date.now();
let lastPing = Date.now();
function expired(session: AuthSession) {
  return !!session.expiresAt && !(new Date(session.expiresAt).getTime() > Date.now());
}
async function revalidateSession() {
  synchronizeSessionInvalidation();
  const candidate = readSession();
  if (!candidate) { setSessionValidation("ready"); return; }
  if (loggingOut) return;
  if (expired(candidate) || Date.now() - lastActivity >= 30 * 60 * 1000) { clearSession(); return; }
  if (validation?.session === candidate) return validation.promise;
  const requestGeneration = ++generation;
  setSessionValidation("checking");
  const promise = (async () => {
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), 20000);
    try {
      // Capture the token and check the generation before handling a 401, so an
      // old response cannot clear a newer login. Other API calls keep api()'s 401 handling.
      const base = (process.env.NEXT_PUBLIC_API_BASE_URL || "http://localhost:8080").replace(/\/$/, "");
      const response = await fetch(`${base}/api/auth/me`, {
        headers: { Accept: "application/json", Authorization: `Bearer ${candidate.accessToken}` },
        cache: "no-store", signal: controller.signal,
      });
      const body = await response.json().catch(() => null);
      synchronizeSessionInvalidation();
      if (requestGeneration !== generation || readSession() !== candidate) return;
      if (response.status === 401) { clearSession(); return; }
      if (!response.ok) throw new Error(body?.message || "No se pudo comprobar la sesión.");
      if (expired(candidate)) { clearSession(); return; }
      const user = body as UserSummary;
      saveSession(mapAuth({ accessToken: candidate.accessToken, expiresAt: candidate.expiresAt ?? "", user }));
      lastPing = Date.now();
    } catch (error) {
      if (requestGeneration === generation && readSession() === candidate) {
        const detail = error instanceof TypeError
          ? "No se pudo conectar con el servidor. Comprueba tu conexión e inténtalo nuevamente."
          : error instanceof Error && error.name === "AbortError"
            ? "El servidor tardó demasiado. Inténtalo nuevamente." : message(error);
        setSessionValidation("error", detail);
      }
    } finally {
      clearTimeout(timer);
      if (validation?.session === candidate) validation = null;
    }
  })();
  validation = { session: candidate, promise };
  return promise;
}
export function useAuth() {
  const state = useSyncExternalStore(subscribeSession, getSessionSnapshot, getServerSessionSnapshot);
  useEffect(() => {
    const restore = () => { void revalidateSession(); };
    const hide = () => { if (readSession()) setSessionValidation("checking"); };
    const visibility = () => { if (document.visibilityState === "visible") restore(); else hide(); };
    window.addEventListener("pageshow", restore);
    window.addEventListener("popstate", restore);
    window.addEventListener("pagehide", hide);
    window.addEventListener("storage", restore);
    document.addEventListener("visibilitychange", visibility);
    restore();
    return () => {
      window.removeEventListener("pageshow", restore);
      window.removeEventListener("popstate", restore);
      window.removeEventListener("pagehide", hide);
      window.removeEventListener("storage", restore);
      document.removeEventListener("visibilitychange", visibility);
    };
  }, []);
  useEffect(() => {
    if (!state.session) return;
    const session = state.session;
    const activity = () => {
      const now = Date.now();
      if (expired(session) || now - lastActivity >= 30 * 60 * 1000) { clearSession(); return; }
      lastActivity = now;
      if (now - lastPing > 2 * 60 * 1000 && getSessionSnapshot().status === "ready") {
        lastPing = now;
        void revalidateSession();
      }
    };
    const events = ["pointerdown", "keydown", "scroll", "pointermove"];
    events.forEach((name) => window.addEventListener(name, activity, { passive: true }));
    const timer = window.setInterval(() => {
      if (expired(session) || Date.now() - lastActivity >= 30 * 60 * 1000) clearSession();
    }, 15000);
    const expiryTimer = session.expiresAt
      ? window.setTimeout(clearSession, Math.max(0, new Date(session.expiresAt).getTime() - Date.now())) : undefined;
    return () => {
      window.clearInterval(timer);
      if (expiryTimer !== undefined) window.clearTimeout(expiryTimer);
      events.forEach((name) => window.removeEventListener(name, activity));
    };
  }, [state.session]);
  async function login(credentials: LoginCredentials) {
    const attempt = ++generation;
    const result = await httpAuthService.login(credentials);
    if (result.ok && attempt === generation) {
      ++sessionEpoch;
      lastActivity = lastPing = Date.now();
      saveSession(result.session);
    }
    return result;
  }
  async function logout() {
    ++generation;
    loggingOut = true;
    setSessionValidation("checking");
    try { await httpAuthService.logout(); }
    catch { clearSession(); }
    finally { loggingOut = false; clearSession(); }
  }
  const session = state.session && !expired(state.session) ? state.session : null;
  return {
    session,
    sessionKey: session ? `${sessionEpoch}:${session.user.id}` : null,
    isRestoringSession: state.status === "checking" && !session,
    isRevalidatingSession: state.status === "checking" && !!session,
    isEndingSession: loggingOut,
    sessionValidationError: state.status === "error" ? state.error : "",
    retrySession: revalidateSession,
    login, logout,
  };
}
