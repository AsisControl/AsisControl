import type { AuthSession } from "./types";
// El access token vive solo en memoria; al recargar se recupera con la cookie
// HttpOnly de renovación (POST /api/auth/refresh). No se persisten tokens.
type SessionStatus = "checking" | "ready" | "error";
export type SessionSnapshot = {
  session: AuthSession | null;
  status: SessionStatus;
  error: string;
};
const serverSnapshot: SessionSnapshot = {
  session: null,
  status: "checking",
  error: "",
};
const invalidationKey = "asiscontrol:session-invalidation";
const listeners = new Set<() => void>();
let snapshot = serverSnapshot;
/** Solo se guarda una marca de cierre en localStorage, nunca tokens ni datos del usuario. */
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
export function saveSession(session: AuthSession) {
  observedInvalidation = readInvalidation();
  publish({ session, status: "ready", error: "" });
}
export function setSessionStatus(status: SessionStatus, error = "") {
  publish({ session: snapshot.session, status, error });
}
/** Cierra la sesión de esta pestaña si otra pestaña cerró sesión. */
export function synchronizeSessionInvalidation() {
  const value = readInvalidation();
  if (value === observedInvalidation) return;
  observedInvalidation = value;
  publish({ session: null, status: "ready", error: "" });
}
export function clearSession() {
  try {
    if (typeof window !== "undefined") {
      observedInvalidation = `${Date.now()}:${Math.random()}`;
      window.localStorage.setItem(invalidationKey, observedInvalidation);
    }
  } catch {
    // Sin almacenamiento del navegador, el backend sigue rechazando el token revocado.
  }
  publish({ session: null, status: "ready", error: "" });
  if (typeof window !== "undefined")
    window.dispatchEvent(new Event("asiscontrol:session-cleared"));
}
