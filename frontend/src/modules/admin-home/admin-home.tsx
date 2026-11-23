"use client";
import { useEffect, useState, type ReactNode } from "react";
import { message } from "@/core/api/client";
import { NavIcon } from "@/core/navigation/nav-icons";
import { Feedback, HomeIndicator, HomeIndicators, HomeIndicatorsSkeleton, Loading, PageHeader, type HomeTone } from "@/shared/ui";
import { timeLabel } from "@/shared/lib/formatters";
import { destinoDeIndicador } from "./indicador-destinos";
import { obtenerPanelIndicadores } from "./services/panel-administrador-service";
import type { Indicador, PanelIndicadores, TonoIndicador } from "./types";
import styles from "./admin-home.module.css";

const TONO: Record<TonoIndicador, HomeTone> = {
  neutral: "primary",
  success: "success",
  warning: "warning",
  danger: "danger",
};

function fechaLarga() {
  const texto = new Date().toLocaleDateString("es-PE", { timeZone: "America/Lima", dateStyle: "full" });
  return texto.charAt(0).toUpperCase() + texto.slice(1);
}

function TarjetaIndicador({ indicador }: { indicador: Indicador }) {
  const { icono, enlace } = destinoDeIndicador(indicador.codigo);
  return (
    <HomeIndicator
      icon={<NavIcon label={icono} />}
      value={indicador.valor}
      label={indicador.etiqueta}
      tone={TONO[indicador.tono] ?? "primary"}
      href={enlace ?? undefined}
    />
  );
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
        <HomeIndicators>
          {panel.indicadores.map((indicador) => (
            <TarjetaIndicador indicador={indicador} key={indicador.codigo} />
          ))}
        </HomeIndicators>
      )}
      {/* Las acciones rápidas no dependen de los datos: siguen disponibles aunque el panel falle. */}
      {quickActions && <div className={styles.acciones}>{quickActions}</div>}
    </section>
  );
}
