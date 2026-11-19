"use client";
import Link from "next/link";
import { useEffect, useState, type ReactNode } from "react";
import { message } from "@/core/api/client";
import { NavIcon } from "@/core/navigation/nav-icons";
import { Feedback, Loading, PageHeader, Skeleton } from "@/shared/ui";
import { timeLabel } from "@/shared/lib/formatters";
import { destinoDeIndicador } from "./indicador-destinos";
import { obtenerPanelIndicadores } from "./services/panel-administrador-service";
import type { Indicador, PanelIndicadores, TonoIndicador } from "./types";
import styles from "./admin-home.module.css";

const CLASE_TONO: Record<TonoIndicador, string> = {
  neutral: styles.neutral,
  success: styles.exito,
  warning: styles.advertencia,
  danger: styles.peligro,
};

const SKELETONS = [0, 1, 2, 3, 4, 5];

function fechaLarga() {
  const texto = new Date().toLocaleDateString("es-PE", { timeZone: "America/Lima", dateStyle: "full" });
  return texto.charAt(0).toUpperCase() + texto.slice(1);
}

function IndicadoresSkeleton() {
  return (
    <div className={styles.indicadores} aria-hidden="true">
      {SKELETONS.map((posicion) => (
        <article className={styles.indicador} key={posicion}>
          <Skeleton width={40} height={40} radius={8} />
          <div className={styles.esqueletoTexto}>
            <Skeleton width={36} height={24} />
            <Skeleton width={120} height={12} />
          </div>
        </article>
      ))}
    </div>
  );
}

function TarjetaIndicador({ indicador }: { indicador: Indicador }) {
  const { icono, enlace } = destinoDeIndicador(indicador.codigo);
  const tono = CLASE_TONO[indicador.tono] ?? styles.neutral;
  const contenido = (
    <>
      <span className={`${styles.indicadorIcono} ${tono}`} aria-hidden="true">
        <NavIcon label={icono} />
      </span>
      <div>
        <strong className={tono}>{indicador.valor}</strong>
        <span>{indicador.etiqueta}</span>
      </div>
    </>
  );
  if (!enlace) return <article className={styles.indicador}>{contenido}</article>;
  return (
    <Link className={`${styles.indicador} ${styles.indicadorEnlace}`} href={enlace}>
      {contenido}
    </Link>
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
      {!panel && !error && <Loading skeleton={<IndicadoresSkeleton />}>Cargando el panel…</Loading>}
      {panel && !panel.indicadores.length && <p className={styles.vacio}>No hay indicadores disponibles.</p>}
      {panel && panel.indicadores.length > 0 && (
        <div className={styles.indicadores}>
          {panel.indicadores.map((indicador) => (
            <TarjetaIndicador indicador={indicador} key={indicador.codigo} />
          ))}
        </div>
      )}
      {/* Las acciones rápidas no dependen de los datos: siguen disponibles aunque el panel falle. */}
      {quickActions && <div className={styles.acciones}>{quickActions}</div>}
    </section>
  );
}
