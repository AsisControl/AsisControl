import { defineConfig, globalIgnores } from "eslint/config";
import nextVitals from "eslint-config-next/core-web-vitals";
import nextTs from "eslint-config-next/typescript";

const eslintConfig = defineConfig([
  ...nextVitals,
  ...nextTs,
  // Override default ignores of eslint-config-next.
  globalIgnores([
    // Default ignores of eslint-config-next:
    ".next/**",
    "out/**",
    "build/**",
    "next-env.d.ts",
    // Reporte HTML generado por npm run test:coverage.
    "coverage/**",
  ]),
  // Límites entre módulos: ver docs/ESTRUCTURA_FRONTEND.md.
  {
    files: ["src/**/*.{ts,tsx}"],
    rules: {
      "no-restricted-imports": [
        "error",
        {
          patterns: [
            {
              group: ["@/modules/*/*"],
              message:
                "Importa otro módulo solo desde su API pública: @/modules/<modulo> (index.ts).",
            },
          ],
        },
      ],
    },
  },
]);

export default eslintConfig;
