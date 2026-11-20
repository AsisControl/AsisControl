import type { AuthSession } from "./types";
// El access token vive solo en memoria; al recargar se recupera con la cookie
// HttpOnly de renovación (POST /api/auth/refresh). No se persisten tokens.
type SessionStatus = "checking" | "ready" | "error";
export type SessionSnapshot = {
  session: AuthSession | null;
  status: SessionStatus;
  error: string;
  contextSuspended: boolean;
};
const serverSnapshot: SessionSnapshot = {
  session: null,
  status: "checking",
  error: "",
  contextSuspended: false,
};
const invalidationKey = "asiscontrol:session-invalidation";
export const roleContextEventKey = "asiscontrol:role-context-changed";
const listeners = new Set<() => void>();
let snapshot = serverSnapshot;
let contextRevision = 0;
/** Las marcas entre pestañas nunca incluyen tokens ni información de la cuenta. */
function readInvalidation(): string | null {
  try {
    return typeof window === "undefined"
      ? null
      : window.localStorage.getItem(invalidationKey);
  } catch {
    return null;
  }
}
let observedInvalidation = readInvalidation();
function publish(next: SessionSnapshot) {
  snapshot = next;
  listeners.forEach((listener) => listener());
}
export function subscribeSession(listener: () => void) {
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
}
export const getSessionSnapshot = () => snapshot;
export const getServerSessionSnapshot = () => serverSnapshot;
/** Renueva el access token; la registra use-auth y la usa api() ante un 401. */
let renewal: (() => Promise<boolean>) | null = null;
export function registerSessionRenewal(renew: () => Promise<boolean>) {
  renewal = renew;
}
/** true si se obtuvo un token nuevo; false si no hay renovación registrada o falló. */
export const renewSession = () =>
  renewal ? renewal() : Promise.resolve(false);
export function readSession(): AuthSession | null {
  return snapshot.session;
}
export function sessionContextKey(session: AuthSession | null) {
  if (!session) return null;
  const user = session.user;
  return JSON.stringify([
    user?.id, user?.sessionId ?? user?.id, user?.contextVersion ?? 0, user?.role,
  ]);
}
export function readContextRevision() {
  return contextRevision;
}
export function suspendRoleContext() {
  contextRevision++;
  publish({ ...snapshot, contextSuspended: true, status: "checking", error: "" });
}
export function announceRoleContextChange() {
  try {
    window.localStorage.setItem(roleContextEventKey, newContextMarker());
  } catch {
    // El contexto se revalida con el servidor al volver a la pestaña.
  }
}
function newContextMarker() {
  return String(Date.now()) + ":" + Math.random();
}
export function saveSession(session: AuthSession) {
  observedInvalidation = readInvalidation();
  if (sessionContextKey(snapshot.session) !== sessionContextKey(session))
    contextRevision++;
  publish({ session, status: "ready", error: "", contextSuspended: false });
}
export function setSessionStatus(status: SessionStatus, error = "") {
  publish({ ...snapshot, status, error });
}
/** Cierra la sesión de esta pestaña si otra pestaña cerró sesión. */
export function synchronizeSessionInvalidation() {
  const value = readInvalidation();
  if (value === observedInvalidation) return false;
  observedInvalidation = value;
  contextRevision++;
  publish({ session: null, status: "ready", error: "", contextSuspended: false });
  return true;
}
export function clearSession() {
  try {
    if (typeof window !== "undefined") {
      observedInvalidation = newContextMarker();
      window.localStorage.setItem(invalidationKey, observedInvalidation);
    }
  } catch {
    // Sin almacenamiento del navegador, el backend sigue rechazando el token revocado.
  }
  contextRevision++;
  publish({ session: null, status: "ready", error: "", contextSuspended: false });
  if (typeof window !== "undefined")
    window.dispatchEvent(new Event("asiscontrol:session-cleared"));
}
