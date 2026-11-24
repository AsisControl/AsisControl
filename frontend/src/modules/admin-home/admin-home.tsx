"use client";
import { useEffect, useState, type ReactNode } from "react";
import { message } from "@/core/api/client";
import { Feedback, HomeIndicatorsSkeleton, Loading, PageHeader } from "@/shared/ui";
import { timeLabel } from "@/shared/lib/formatters";
import { destinoDeIndicador } from "./indicador-destinos";
import { obtenerPanelIndicadores, type PanelIndicadores } from "@/core/api/dashboard";
import { DashboardIndicators } from "@/shared/ui/dashboard-indicators";
import styles from "./admin-home.module.css";

function fechaLarga() {
  const texto = new Date().toLocaleDateString("es-PE", { timeZone: "America/Lima", dateStyle: "full" });
  return texto.charAt(0).toUpperCase() + texto.slice(1);
}

/** Inicio del administrador: indicadores institucionales con datos reales de GET /api/dashboard. */
export function AdminHome({ fullName, quickActions }: { fullName: string; quickActions?: ReactNode }) {
  const [panel, setPanel] = useState<PanelIndicadores | null>(null);
  const [error, setError] = useState("");
  const [cargando, setCargando] = useState(true);
  const [actualizado, setActualizado] = useState("");
  const [intento, setIntento] = useState(0);

  useEffect(() => {
    let activo = true;
    obtenerPanelIndicadores()
      .then((datos) => {
        if (!activo) return;
        setPanel(datos);
        setError("");
        setActualizado(timeLabel(new Date()));
      })
      .catch((causa) => {
        if (activo) setError(message(causa));
      })
      .finally(() => {
        if (activo) setCargando(false);
      });
    return () => {
      activo = false;
    };
  }, [intento]);

  function actualizar() {
    setCargando(true);
    setIntento((valor) => valor + 1);
  }

  return (
    <section>
      <PageHeader
        title="Panel de administración"
        description={
          <>
            Bienvenido/a, {fullName} · {fechaLarga()}
            {actualizado && <span className={styles.actualizado}> · Actualizado a las {actualizado}</span>}
          </>
        }
        actions={
          <button type="button" onClick={actualizar} disabled={cargando}>
            {cargando && panel ? "Actualizando…" : "Actualizar"}
          </button>
        }
      />
      <Feedback error={error} />
      {!panel && !error && <Loading skeleton={<HomeIndicatorsSkeleton count={6} />}>Cargando el panel…</Loading>}
      {panel && !panel.indicadores.length && <p className={styles.vacio}>No hay indicadores disponibles.</p>}
      {panel && panel.indicadores.length > 0 && (
        <DashboardIndicators indicadores={panel.indicadores} destino={destinoDeIndicador} />
      )}
      {/* Las acciones rápidas no dependen de los datos: siguen disponibles aunque el panel falle. */}
      {quickActions && <div className={styles.acciones}>{quickActions}</div>}
    </section>
  );
}
