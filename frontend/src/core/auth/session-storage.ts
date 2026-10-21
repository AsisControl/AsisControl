import type { AuthSession } from "./types";
type Snapshot = { session: AuthSession | null; status: "checking" | "ready" | "error"; error: string };
const serverSnapshot: Snapshot = { session: null, status: "checking", error: "" };
let snapshot = serverSnapshot;
const listeners = new Set<() => void>();
const invalidationKey = "asiscontrol:session-invalidation";
function readInvalidation() {
  try { return typeof window === "undefined" ? null : window.localStorage.getItem(invalidationKey); }
  catch { return null; }
}
let observedInvalidation = readInvalidation();
function publish(next: Snapshot) { snapshot = next; listeners.forEach((listener) => listener()); }
export function subscribeSession(listener: () => void) {
  listeners.add(listener);
  return () => { listeners.delete(listener); };
}
export const getSessionSnapshot = () => snapshot;
export const getServerSessionSnapshot = () => serverSnapshot;
export const readSession = (): AuthSession | null => snapshot.session;
export function saveSession(session: AuthSession) {
  observedInvalidation = readInvalidation();
  publish({ session, status: "ready", error: "" });
}
export function setSessionValidation(status: Snapshot["status"], error = "") {
  publish({ session: snapshot.session, status, error });
}
export function synchronizeSessionInvalidation() {
  const value = readInvalidation();
  if (value !== observedInvalidation) {
    observedInvalidation = value;
    publish({ session: null, status: "ready", error: "" });
  }
}
export function clearSession() {
  // Persist only an invalidation marker, never tokens or user data.
  try {
    if (typeof window !== "undefined") {
      observedInvalidation = `${Date.now()}:${Math.random()}`;
      window.localStorage.setItem(invalidationKey, observedInvalidation);
    }
  } catch { /* Server validation remains available if browser storage is disabled. */ }
  publish({ session: null, status: "ready", error: "" });
  if (typeof window !== "undefined") window.dispatchEvent(new Event("asiscontrol:session-cleared"));
}
