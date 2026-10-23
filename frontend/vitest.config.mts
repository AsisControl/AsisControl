import { fileURLToPath } from "node:url";
import { defineConfig } from "vitest/config";

/**
 * Pruebas y cobertura del frontend (equivalente a JaCoCo en el backend).
 * `npm test` ejecuta las pruebas; `npm run test:coverage` además mide la
 * cobertura de src/ y falla si baja de los umbrales.
 */
export default defineConfig({
  resolve: {
    alias: { "@": fileURLToPath(new URL("./src", import.meta.url)) },
  },
  esbuild: { jsx: "automatic" },
  test: {
    include: ["tests/**/*.test.{js,jsx}", "src/**/tests/**/*.test.{js,jsx}"],
    // Las pantallas se prueban en un DOM simulado; la lógica pura declara
    // `@vitest-environment node` en su archivo.
    environment: "jsdom",
    environmentOptions: { jsdom: { url: "http://frontend.test" } },
    setupFiles: ["tests/support/setup.js"],
    env: { NEXT_PUBLIC_API_BASE_URL: "http://api.test" },
    css: { modules: { classNameStrategy: "non-scoped" } },
    restoreMocks: true,
    unstubGlobals: true,
    coverage: {
      provider: "v8",
      include: ["src/**/*.{ts,tsx}"],
      // Los tipos no generan código y el layout raíz solo arma <html> para Next.
      exclude: [
        "src/**/tests/**",
        "src/**/types.ts",
        "src/shared/types/**",
        "src/**/index.ts",
        "src/app/layout.tsx",
      ],
      reporter: ["text-summary", "text", "html", "lcov", "json-summary"],
      reportsDirectory: "coverage",
      // Piso que impide retrocesos (oct. 2026: ~99 % de líneas). Toda pantalla
      // nueva llega con sus pruebas en <modulo>/tests/.
      thresholds: {
        statements: 95,
        lines: 95,
        branches: 90,
        functions: 90,
      },
    },
  },
});
