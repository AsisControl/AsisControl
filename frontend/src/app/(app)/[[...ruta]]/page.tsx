import { Suspense } from "react";
import { ModuleOutlet } from "@/core/navigation/module-outlet";
/** Todas las pantallas internas: la URL decide el módulo (ver core/navigation). */
export default function Page() {
  return (
    <Suspense fallback={null}>
      <ModuleOutlet />
    </Suspense>
  );
}
