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
  registerSessionRenewal,
  saveSession,
  setSessionStatus,
  subscribeSession,
  synchronizeSessionInvalidation,
} from "./session-storage";
import type { AuthSession, LoginCredentials } from "./types";
const INACTIVITY_LIMIT_MS = 30 * 60 * 1000;
const REVALIDATION_INTERVAL_MS = 2 * 60 * 1000;
const REQUEST_TIMEOUT_MS = 20000;
/** Se renueva el access token un minuto antes de que venza. */
const RENEWAL_MARGIN_MS = 60 * 1000;
let pendingValidation: { session: AuthSession; promise: Promise<void> } | null =
  null;
let pendingRefresh: Promise<boolean> | null = null;
/** Aumenta en cada login o logout: una respuesta antigua no afecta a la sesión nueva. */
let generation = 0;
/** Solo cambia con login o logout: una renovación no se descarta por un /me en curso. */
let sessionEpoch = 0;
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
function connectionMessage(error: unknown) {
  return error instanceof TypeError
    ? "No se pudo conectar con el servidor. Comprueba tu conexión e inténtalo nuevamente."
    : error instanceof Error && error.name === "AbortError"
      ? "El servidor tardó demasiado. Inténtalo nuevamente."
      : message(error);
}
/** La cookie de renovación es de un solo uso: las pestañas renuevan por turnos. */
function withRefreshLock<T>(task: () => Promise<T>): Promise<T> {
  const locks = typeof navigator === "undefined" ? undefined : navigator.locks;
  return locks
    ? (locks.request("asiscontrol:session-refresh", task) as Promise<T>)
    : task();
}
/**
 * Pide un access token nuevo con la cookie HttpOnly. Devuelve true si la
 * sesión sigue. Una sola renovación a la vez: si el servidor recibe dos veces
 * la misma cookie, revoca la sesión (también ocurriría con el doble montaje
 * de React en desarrollo).
 */
function refreshSession(): Promise<boolean> {
  if (pendingRefresh) return pendingRefresh;
  const requestEpoch = sessionEpoch;
  const previous = readSession();
  // Sin un token vigente que mostrar, el shell espera en "Cargando sesión…".
  if (!previous || isExpired(previous)) setSessionStatus("checking");
  pendingRefresh = (async () => {
    try {
      const renewed = await withRefreshLock(() => httpAuthService.refresh());
      if (requestEpoch !== sessionEpoch) return readSession() !== null;
      if (!renewed) {
        // Sin sesión en esta pestaña no se avisa a las demás: quizá nunca la hubo.
        if (readSession()) clearSession();
        else setSessionStatus("ready");
        return false;
      }
      if (!previous) lastActivity = Date.now();
      lastValidation = Date.now();
      saveSession(renewed);
      return true;
    } catch (error) {
      if (requestEpoch === sessionEpoch)
        setSessionStatus("error", connectionMessage(error));
      return false;
    } finally {
      pendingRefresh = null;
    }
  })();
  return pendingRefresh;
}
registerSessionRenewal(refreshSession);
/** Vencido o por vencer: se renueva salvo que el usuario lleve 30 min inactivo. */
function renewOrEnd() {
  if (isInactive()) clearSession();
  else void refreshSession();
}
/**
 * Confirma con /api/auth/me que el token sigue vigente y actualiza permisos.
 * Sin sesión en memoria (al cargar la página), la recupera con la cookie.
 * Se usa fetch directo para capturar el token enviado y descartar respuestas
 * de una sesión anterior; el resto de llamadas usa el 401 de api().
 */
async function revalidateSession(): Promise<void> {
  synchronizeSessionInvalidation();
  const candidate = readSession();
  if (!candidate) {
    // Tras un cierre de sesión el estado queda "ready" y no se reintenta.
    if (getSessionSnapshot().status !== "ready") await refreshSession();
    return;
  }
  if (isInactive()) {
    clearSession();
    return;
  }
  if (isExpired(candidate)) {
    await refreshSession();
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
        // Otra pestaña pudo renovar la sesión; si la cookie ya no sirve, se cierra.
        await refreshSession();
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
      setSessionStatus("error", connectionMessage(error));
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
      if (isInactive()) {
        clearSession();
        return;
      }
      if (isExpired(session)) {
        void refreshSession();
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
      if (isInactive()) clearSession();
      else if (isExpired(session)) void refreshSession();
    }, 15000);
    const renewalTimer = session.expiresAt
      ? window.setTimeout(
          renewOrEnd,
          Math.max(
            0,
            new Date(session.expiresAt).getTime() -
              Date.now() -
              RENEWAL_MARGIN_MS,
          ),
        )
      : undefined;
    return () => {
      window.clearInterval(inactivityTimer);
      if (renewalTimer !== undefined) window.clearTimeout(renewalTimer);
      events.forEach((name) => window.removeEventListener(name, onActivity));
    };
  }, [state.session]);
  async function login(credentials: LoginCredentials) {
    const attempt = ++generation;
    sessionEpoch++;
    const result = await httpAuthService.login(credentials);
    if (result.ok && attempt === generation) {
      lastActivity = lastValidation = Date.now();
      saveSession(result.session);
    }
    return result;
  }
  async function logout() {
    ++generation;
    sessionEpoch++;
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
