import { normalizeDestination } from "./navigation-routes";

const key = "asiscontrol:pending-destination";

export function clearPendingDestination() {
  try { window.sessionStorage.removeItem(key); } catch { /* Optional navigation convenience. */ }
}

export function rememberDestination(value: string) {
  const destination = normalizeDestination(value);
  clearPendingDestination();
  if (!destination) return;
  try { window.sessionStorage.setItem(key, destination); } catch { /* Login falls back to /. */ }
}

export function consumeDestination(): string {
  try {
    const value = window.sessionStorage.getItem(key);
    // Removal must succeed before a destination is used.
    window.sessionStorage.removeItem(key);
    return value ? normalizeDestination(value) ?? "/" : "/";
  } catch {
    return "/";
  }
}
