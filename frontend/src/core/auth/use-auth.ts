"use client";
import { useEffect, useState, useSyncExternalStore } from "react";
import { API_BASE_URL, message } from "@/core/api/client";
import {
  httpAuthService,
  mapAuth,
  type UserSummary,
} from "./http-auth-service";
import {
  clearSession,
  getServerSessionSnapshot,
  getSessionSnapshot,
  readSession,
  saveSession,
  setSessionStatus,
  subscribeSession,
  synchronizeSessionInvalidation,
} from "./session-storage";
import type { AuthSession, LoginCredentials } from "./types";
const INACTIVITY_LIMIT_MS = 30 * 60 * 1000;
const REVALIDATION_INTERVAL_MS = 2 * 60 * 1000;
const REQUEST_TIMEOUT_MS = 20000;
let pendingValidation: { session: AuthSession; promise: Promise<void> } | null =
  null;
/** Aumenta en cada login o logout: una respuesta antigua no afecta a la sesión nueva. */
let generation = 0;
let lastActivity = Date.now();
let lastValidation = Date.now();
function isExpired(session: AuthSession) {
  return (
    !!session.expiresAt && new Date(session.expiresAt).getTime() <= Date.now()
  );
}
function isInactive() {
  return Date.now() - lastActivity >= INACTIVITY_LIMIT_MS;
}
/**
 * Confirma con /api/auth/me que el token sigue vigente y actualiza permisos.
 * Se usa fetch directo para capturar el token enviado y descartar respuestas
 * de una sesión anterior; el resto de llamadas usa el 401 de api().
 */
async function revalidateSession(): Promise<void> {
  synchronizeSessionInvalidation();
  const candidate = readSession();
  if (!candidate) {
    setSessionStatus("ready");
    return;
  }
  if (isExpired(candidate) || isInactive()) {
    clearSession();
    return;
  }
  if (pendingValidation?.session === candidate)
    return pendingValidation.promise;
  const requestGeneration = ++generation;
  const promise = (async () => {
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), REQUEST_TIMEOUT_MS);
    try {
      const response = await fetch(`${API_BASE_URL}/api/auth/me`, {
        headers: {
          Accept: "application/json",
          Authorization: `Bearer ${candidate.accessToken}`,
        },
        cache: "no-store",
        signal: controller.signal,
      });
      const body = await response.json().catch(() => null);
      synchronizeSessionInvalidation();
      if (requestGeneration !== generation || readSession() !== candidate)
        return;
      if (response.status === 401) {
        clearSession();
        return;
      }
      if (!response.ok)
        throw new Error(body?.message || "No se pudo comprobar la sesión.");
      saveSession(
        mapAuth({
          accessToken: candidate.accessToken,
          expiresAt: candidate.expiresAt ?? "",
          user: body as UserSummary,
        }),
      );
      lastValidation = Date.now();
    } catch (error) {
      if (requestGeneration !== generation || readSession() !== candidate)
        return;
      const detail =
        error instanceof TypeError
          ? "No se pudo conectar con el servidor. Comprueba tu conexión e inténtalo nuevamente."
          : error instanceof Error && error.name === "AbortError"
            ? "El servidor tardó demasiado. Inténtalo nuevamente."
            : message(error);
      setSessionStatus("error", detail);
    } finally {
      clearTimeout(timer);
      if (pendingValidation?.session === candidate) pendingValidation = null;
    }
  })();
  pendingValidation = { session: candidate, promise };
  return promise;
}
export function useAuth() {
  const state = useSyncExternalStore(
    subscribeSession,
    getSessionSnapshot,
    getServerSessionSnapshot,
  );
  const [isEndingSession, setIsEndingSession] = useState(false);
  useEffect(() => {
    // Al volver a la pestaña o desde la caché del navegador se confirma la sesión.
    const revalidate = () => void revalidateSession();
    const onVisibilityChange = () => {
      if (document.visibilityState === "visible") revalidate();
    };
    window.addEventListener("pageshow", revalidate);
    window.addEventListener("storage", revalidate);
    document.addEventListener("visibilitychange", onVisibilityChange);
    revalidate();
    return () => {
      window.removeEventListener("pageshow", revalidate);
      window.removeEventListener("storage", revalidate);
      document.removeEventListener("visibilitychange", onVisibilityChange);
    };
  }, []);
  useEffect(() => {
    const session = state.session;
    if (!session) return;
    const onActivity = () => {
      if (isExpired(session) || isInactive()) {
        clearSession();
        return;
      }
      lastActivity = Date.now();
      if (
        lastActivity - lastValidation > REVALIDATION_INTERVAL_MS &&
        getSessionSnapshot().status === "ready"
      ) {
        lastValidation = lastActivity;
        void revalidateSession();
      }
    };
    const events = ["pointerdown", "keydown", "scroll", "pointermove"];
    events.forEach((name) =>
      window.addEventListener(name, onActivity, { passive: true }),
    );
    const inactivityTimer = window.setInterval(() => {
      if (isExpired(session) || isInactive()) clearSession();
    }, 15000);
    const expiryTimer = session.expiresAt
      ? window.setTimeout(
          clearSession,
          Math.max(0, new Date(session.expiresAt).getTime() - Date.now()),
        )
      : undefined;
    return () => {
      window.clearInterval(inactivityTimer);
      if (expiryTimer !== undefined) window.clearTimeout(expiryTimer);
      events.forEach((name) => window.removeEventListener(name, onActivity));
    };
  }, [state.session]);
  async function login(credentials: LoginCredentials) {
    const attempt = ++generation;
    const result = await httpAuthService.login(credentials);
    if (result.ok && attempt === generation) {
      lastActivity = lastValidation = Date.now();
      saveSession(result.session);
    }
    return result;
  }
  async function logout() {
    ++generation;
    setIsEndingSession(true);
    try {
      await httpAuthService.logout();
    } catch {
      clearSession();
    } finally {
      setIsEndingSession(false);
    }
  }
  const session =
    state.session && !isExpired(state.session) ? state.session : null;
  return {
    session,
    isRestoringSession: state.status === "checking" && !session,
    isEndingSession,
    sessionValidationError: state.status === "error" ? state.error : "",
    retrySession: revalidateSession,
    login,
    logout,
  };
}
