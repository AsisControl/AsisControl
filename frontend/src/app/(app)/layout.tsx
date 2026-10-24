import type { ReactNode } from "react";
import { AppShell } from "@/core/layout/app-shell";
/** El shell (sesión y menú) se conserva al navegar entre pantallas. */
export default function AppLayout({ children }: { children: ReactNode }) {
  return <AppShell>{children}</AppShell>;
}
