"use client";
import { useEffect, useState, useSyncExternalStore } from "react";
import { API_BASE_URL, ApiError, message } from "@/core/api/client";
import {
  httpAuthService,
  mapAuth,
  type UserSummary,
} from "./http-auth-service";
import {
  announceRoleContextChange,
  clearSession,
  getServerSessionSnapshot,
  getSessionSnapshot,
  readContextRevision,
  readSession,
  registerSessionRenewal,
  roleContextEventKey,
  saveSession,
  setSessionStatus,
  sessionContextKey,
  subscribeSession,
  suspendRoleContext,
  synchronizeSessionInvalidation,
} from "./session-storage";
import type { AuthSession, LoginCredentials, UserRole } from "./types";
import { scheduleRenewal } from "./renewal-timer";
const INACTIVITY_LIMIT_MS = 30 * 60 * 1000;
const REVALIDATION_INTERVAL_MS = 2 * 60 * 1000;
const REQUEST_TIMEOUT_MS = 20000;
/** Se renueva el access token un minuto antes de que venza. */
const RENEWAL_MARGIN_MS = 60 * 1000;
let pendingValidation: { session: AuthSession; promise: Promise<void> } | null =
  null;
let pendingRefresh: Promise<boolean> | null = null;
/** Invalida comprobaciones que empezaron en un contexto anterior. */
let generation = 0;
/** Una renovación no se descarta por un /me en curso de la misma sesión. */
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
async function synchronizeRoleContext() {
  if (synchronizeSessionInvalidation()) {
    ++sessionEpoch;
    ++generation;
    return;
  }
  if (!readSession()) return;
  const epoch = ++sessionEpoch;
  ++generation;
  suspendRoleContext();
  if (pendingRefresh) await pendingRefresh;
  if (epoch === sessionEpoch && readSession()) await refreshSession();
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
  const revision = readContextRevision();
  const previous = readSession();
  // Sin un token vigente que mostrar, el shell espera en "Cargando sesión…".
  if (!previous || isExpired(previous)) setSessionStatus("checking");
  pendingRefresh = (async () => {
    try {
      const renewed = await withRefreshLock(() => httpAuthService.refresh());
      if (requestEpoch !== sessionEpoch || revision !== readContextRevision())
        return readSession() !== null;
      if (!renewed) {
        // Sin sesión en esta pestaña no se avisa a las demás: quizá nunca la hubo.
        if (readSession()) clearSession();
        else setSessionStatus("ready");
        return false;
      }
      if (!previous) lastActivity = Date.now();
      lastValidation = Date.now();
      const previousContext = readSession();
      const receivedNotification = getSessionSnapshot().contextSuspended;
      saveSession(renewed);
      if (
        previousContext &&
        !receivedNotification &&
        sessionContextKey(previousContext) !== sessionContextKey(renewed)
      ) announceRoleContextChange();
      return true;
    } catch (error) {
      if (requestEpoch === sessionEpoch && revision === readContextRevision())
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
  if (synchronizeSessionInvalidation()) {
    ++sessionEpoch;
    ++generation;
    return;
  }
  if (getSessionSnapshot().contextSuspended) {
    await refreshSession();
    return;
  }
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
      const updated = mapAuth({
        accessToken: candidate.accessToken,
        expiresAt: candidate.expiresAt ?? "",
        user: body as UserSummary,
      });
      if (sessionContextKey(updated) !== sessionContextKey(candidate)) {
        await synchronizeRoleContext();
        return;
      }
      saveSession(updated);
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
    const onStorage = (event: StorageEvent) => {
      if (event.key === roleContextEventKey) void synchronizeRoleContext();
      else revalidate();
    };
    const onVisibilityChange = () => {
      if (document.visibilityState === "visible") revalidate();
    };
    window.addEventListener("pageshow", revalidate);
    window.addEventListener("storage", onStorage);
    document.addEventListener("visibilitychange", onVisibilityChange);
    revalidate();
    return () => {
      window.removeEventListener("pageshow", revalidate);
      window.removeEventListener("storage", onStorage);
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
    const cancelRenewal = session.expiresAt
      ? scheduleRenewal(
          new Date(session.expiresAt).getTime() - RENEWAL_MARGIN_MS,
          renewOrEnd,
        )
      : undefined;
    return () => {
      window.clearInterval(inactivityTimer);
      cancelRenewal?.();
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
  async function switchRole(role: UserRole) {
    const previousContext = sessionContextKey(readSession());
    const epoch = sessionEpoch;
    await withRefreshLock(async () => {
      if (!previousContext || sessionContextKey(readSession()) !== previousContext)
        throw new Error("La sesión cambió. Revisa tus roles disponibles.");
      try {
        const updated = await httpAuthService.switchRole(role);
        if (epoch !== sessionEpoch || sessionContextKey(readSession()) !== previousContext)
          return;
        ++generation;
        saveSession(updated);
        if (sessionContextKey(updated) !== previousContext) announceRoleContextChange();
      } catch (error) {
        if (error instanceof ApiError && error.status === 401)
          void synchronizeRoleContext();
        throw error;
      }
    });
  }
  const session =
    state.session && !isExpired(state.session) ? state.session : null;
  return {
    session,
    isRestoringSession: state.status === "checking" && !session,
    contextSuspended: state.contextSuspended,
    isEndingSession,
    sessionValidationError: state.status === "error" ? state.error : "",
    retrySession: revalidateSession,
    login,
    logout,
    switchRole,
  };
}
