"use client";

import { useEffect, useState, type ReactNode } from "react";
import { obtenerPanelIndicadores, type PanelIndicadores } from "@/core/api/dashboard";
import type { AuthenticatedUser } from "@/core/auth/types";
import { canAccess } from "@/core/navigation/access-policy";
import { navPath } from "@/core/navigation/navigation";
import { Feedback, Loading, PageHeader } from "@/shared/ui";
import { DashboardIndicators, IndicadoresSkeleton, type DestinoIndicador } from "@/shared/ui/dashboard-indicators";

const DESTINOS: Record<string, { icono: string; opcion?: string }> = {
  ESTUDIANTES_VINCULADOS: { icono: "Matrícula", opcion: "Matrícula" },
  TAREAS_PENDIENTES: { icono: "Tareas" },
  PAGOS_PENDIENTES: { icono: "Pagos", opcion: "Pagos" },
  ALERTAS_ACTIVAS: { icono: "Progreso" },
  NOTIFICACIONES_NO_LEIDAS: { icono: "Inicio" },
};

export function GuardianHome({ user, quickActions }: { user: AuthenticatedUser; quickActions: ReactNode }) {
  const [panel, setPanel] = useState<PanelIndicadores | null>(null);
  const [error, setError] = useState("");
  const [intento, setIntento] = useState(0);
  const cargando = !panel && !error;

  useEffect(() => {
    let vigente = true;
    obtenerPanelIndicadores()
      .then((datos) => { if (vigente) setPanel(datos); })
      .catch(() => { if (vigente) setError("No pudimos cargar el resumen de tus estudiantes."); });
    return () => { vigente = false; };
  }, [intento]);

  function actualizar() {
    setPanel(null);
    setError("");
    setIntento((anterior) => anterior + 1);
  }

  function destino(codigo: string): DestinoIndicador {
    const { icono, opcion } = DESTINOS[codigo] ?? { icono: "Inicio" };
    return { icono, enlace: opcion && canAccess(user, opcion) ? navPath(opcion) : undefined };
  }

  return (
    <section>
      <PageHeader title="Panel del Apoderado"
        description={`Bienvenido/a, ${user.fullName}. Consulta la matrícula, los pagos y el progreso de tus estudiantes.`}
        actions={<button type="button" disabled={cargando} onClick={actualizar}>{error ? "Reintentar" : "Actualizar"}</button>} />
      <Feedback error={error} />
      {cargando && <Loading skeleton={<IndicadoresSkeleton cantidad={5} />}>Cargando el resumen familiar…</Loading>}
      {panel?.indicadores.length === 0 && <p>No hay indicadores disponibles.</p>}
      {panel && panel.indicadores.length > 0 && <DashboardIndicators indicadores={panel.indicadores} destino={destino} />}
      {quickActions}
    </section>
  );
}
