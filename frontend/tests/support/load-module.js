import { fileURLToPath } from "node:url";
import { vi } from "vitest";

const SOURCE_ROOT = new URL("../../src/", import.meta.url);

const unexpectedFetch = async () => {
  throw new Error("HTTP no esperado");
};

/** Importa un archivo real de src/ ("core/api/client" o "src/core/api/client.ts"). */
export function importSource(path) {
  const relative = path.replace(/^@\//, "").replace(/^src\//, "");
  return import(/* @vite-ignore */ fileURLToPath(new URL(relative, SOURCE_ROOT)));
}

/**
 * Prepara un entorno aislado para cargar módulos de src/: cada llamada limpia
 * la caché de módulos (sesión, cliente HTTP) y reemplaza fetch y los globales
 * indicados. Al importar los archivos reales, la cobertura queda registrada.
 */
export function harness(fetcher = unexpectedFetch, globals = {}) {
  vi.resetModules();
  vi.stubGlobal("fetch", fetcher);
  for (const [name, value] of Object.entries(globals))
    vi.stubGlobal(name, value);
  return importSource;
}
