import { useMemo, useSyncExternalStore } from "react";

/**
 * Reemplazo de next/navigation para las pruebas: la URL vive en memoria y las
 * pantallas se vuelven a renderizar al navegar, como con el router real.
 * Se usa con `vi.mock("next/navigation", () => import(".../navigation.js"))`.
 */
const ORIGIN = "http://frontend.test";
const listeners = new Set();
let entries = [`${ORIGIN}/`];

const current = () => entries.at(-1);
const publish = () => listeners.forEach((listener) => listener());
const subscribe = (listener) => {
  listeners.add(listener);
  return () => listeners.delete(listener);
};

/** Fija la URL inicial de la prueba y olvida el historial anterior. */
export function setUrl(path) {
  entries = [new URL(path, ORIGIN).href];
  publish();
}

/** Ruta y parámetros actuales: "/administracion/usuarios?ver=1". */
export const currentUrl = () => {
  const url = new URL(current());
  return `${url.pathname}${url.search}`;
};

const router = {
  push(path) {
    entries = [...entries, new URL(path, current()).href];
    publish();
  },
  replace(path) {
    entries = [...entries.slice(0, -1), new URL(path, current()).href];
    publish();
  },
  back() {
    if (entries.length > 1) entries = entries.slice(0, -1);
    publish();
  },
  refresh() {},
  prefetch() {},
};

export const useRouter = () => router;

export function usePathname() {
  return new URL(useSyncExternalStore(subscribe, current, current)).pathname;
}

export function useSearchParams() {
  const href = useSyncExternalStore(subscribe, current, current);
  return useMemo(() => new URL(href).searchParams, [href]);
}

export function useParams() {
  const pathname = usePathname();
  return { ruta: pathname.split("/").filter(Boolean) };
}

export function redirect(path) {
  router.replace(path);
}
