import type { AuthSession } from "./types";
// La sesión dura mientras la pestaña permanece abierta; no se persisten tokens.
let current: AuthSession | null = null;
export function readSession(): AuthSession | null {
  return current;
}
export function saveSession(session: AuthSession) {
  current = session;
}
export function clearSession() {
  current = null;
  if (typeof window !== "undefined")
    window.dispatchEvent(new Event("asiscontrol:session-cleared"));
}
