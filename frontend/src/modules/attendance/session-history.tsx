"use client";
import Link from "next/link";
import { useEffect, useState } from "react";
import { message } from "@/core/api/client";
import { countLabel } from "@/shared/lib/formatters";
import type { Page } from "@/shared/types/api";
import { Feedback, Pager, StatusPill } from "@/shared/ui";
import { fechaSesion, horaSesion } from "./lib";
import { listarCursosDocente, listarSesiones } from "./services/asistencia-service";
import { AlertIcon, Breadcrumb, CalendarIcon, ChevronIcon } from "./session-ui";
import styles from "./attendance.module.css";
import type { CursoSesiones, SesionClase } from "./types";

const POR_PAGINA = 20;
const esSinRegistrar = (sesion: SesionClase) =>
  sesion.estado === "PENDIENTE" && sesion.puedeVer && !sesion.puedeTomar;

function EstadoSesion({ sesion }: { sesion: SesionClase }) {
  if (esSinRegistrar(sesion))
    return (
      <span className={styles.sinRegistrar}>
        <AlertIcon /> Sin registrar
      </span>
    );
  if (sesion.estado === "TOMADA") return <StatusPill state="TOMADA" label="Tomada" tone="success" />;
  if (sesion.estado === "BORRADOR") return <StatusPill state="BORRADOR" label="Borrador" tone="warning" />;
  return <StatusPill state="PENDIENTE" label="Pendiente" tone="neutral" />;
}

function AccionSesion({ sesion, href }: { sesion: SesionClase; href: string }) {
  const etiqueta =
    sesion.estado === "TOMADA"
      ? "Ver asistencia"
      : sesion.puedeTomar
        ? sesion.estado === "BORRADOR"
          ? "Continuar"
          : "Tomar asistencia"
        : sesion.puedeVer
          ? "Ver sesión"
          : null;
  if (!etiqueta) return <span aria-label="Sin acción disponible">—</span>;
  return (
    <Link className={sesion.puedeTomar ? styles.accionPrimaria : styles.enlaceAccion} href={href}>
      {etiqueta} <ChevronIcon />
    </Link>
  );
}

/** Sesiones del curso con su estado; la única entrada a cada una es el botón de acción. */
export function SessionHistory({ asignacionId }: { asignacionId: number }) {
  const [datos, setDatos] = useState<{ sesiones: SesionClase[]; curso?: CursoSesiones } | null>(null);
  const [error, setError] = useState("");
  const [intento, setIntento] = useState(0);
  const [pagina, setPagina] = useState<number | null>(null);
  useEffect(() => {
    let activo = true;
    Promise.all([listarSesiones(asignacionId), listarCursosDocente()])
      .then(([sesiones, cursos]) => {
        if (activo) setDatos({ sesiones, curso: cursos.find((curso) => curso.id === asignacionId) });
      })
      .catch((e) => {
        if (activo) setError(message(e));
      });
    return () => {
      activo = false;
    };
  }, [asignacionId, intento]);
  const nombre = datos?.curso ? `${datos.curso.curso} - ${datos.curso.seccion}` : "Curso";
  const sesiones = datos?.sesiones ?? [];
  const contar = (prueba: (sesion: SesionClase) => boolean) => sesiones.filter(prueba).length;
  const sinRegistrar = contar(esSinRegistrar);
  const tomadas = contar((sesion) => sesion.estado === "TOMADA");
  const borradores = contar((sesion) => sesion.estado === "BORRADOR");
  const indiceHoy = sesiones.findIndex((sesion) => sesion.puedeTomar);
  const referencia = indiceHoy >= 0 ? indiceHoy : sesiones.findLastIndex((sesion) => sesion.puedeVer);
  const totalPaginas = Math.max(1, Math.ceil(sesiones.length / POR_PAGINA));
  const actual = pagina ?? Math.floor(Math.max(referencia, 0) / POR_PAGINA);
  const inicio = actual * POR_PAGINA;
  return (
    <section className={styles.pagina}>
      <Breadcrumb items={[{ etiqueta: "Mis cursos", href: "/mis-cursos" }, { etiqueta: nombre }]} />
      <div className={styles.titulo}>
        <h2>Asistencia: {nombre}</h2>
      </div>
      <p className={styles.subtitulo}>Selecciona una sesión para abrir su asistencia</p>
      {error && (
        <div className={styles.aviso}>
          <Feedback error={error} />
          <button
            type="button"
            onClick={() => {
              setError("");
              setIntento((valor) => valor + 1);
            }}
          >
            Reintentar
          </button>
        </div>
      )}
      {!error && !datos && <p role="status">Cargando sesiones…</p>}
      {!error && datos && sesiones.length === 0 && (
        <p className={styles.vacio}>Este curso todavía no tiene sesiones programadas.</p>
      )}
      {!error && sesiones.length > 0 && (
        <div className={styles.historial}>
          <div className={styles.resumen}>
            <strong>{countLabel(sesiones.length, "sesión", "sesiones")}</strong>
            <span className={tomadas ? styles.tomadas : undefined}>{countLabel(tomadas, "tomada", "tomadas")}</span>
            <span className={borradores ? styles.borradores : undefined}>{countLabel(borradores, "borrador", "borradores")}</span>
            <span>{countLabel(contar((s) => s.estado === "PENDIENTE") - sinRegistrar, "pendiente", "pendientes")}</span>
            <span className={sinRegistrar ? styles.alertas : undefined}>{sinRegistrar} sin registrar</span>
          </div>
          <table className={styles.tabla}>
            <thead>
              <tr>
                <th scope="col">N°</th>
                <th scope="col">Fecha de la sesión</th>
                <th scope="col">Hora</th>
                <th scope="col">Estado</th>
                <th scope="col" className={styles.columnaAccion}>Acción</th>
              </tr>
            </thead>
            <tbody>
              {sesiones.slice(inicio, inicio + POR_PAGINA).map((sesion, indice) => (
                <tr key={sesion.id} className={sesion.puedeTomar ? styles.filaHoy : undefined}>
                  <td>{inicio + indice + 1}</td>
                  <td className={!sesion.puedeVer ? styles.futura : undefined}>
                    <span className={styles.fecha}>
                      <CalendarIcon /> {fechaSesion(sesion.fecha)}
                      {sesion.puedeTomar && <span className={styles.hoy}>Hoy</span>}
                    </span>
                  </td>
                  <td>{horaSesion(sesion.horaInicio)}</td>
                  <td><EstadoSesion sesion={sesion} /></td>
                  <td className={styles.columnaAccion}>
                    <AccionSesion sesion={sesion} href={`/mis-cursos/${asignacionId}/asistencia/${sesion.id}`} />
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          {totalPaginas > 1 && (
            <Pager
              data={{
                content: [],
                totalElements: sesiones.length,
                totalPages: totalPaginas,
                page: actual,
                size: POR_PAGINA,
                first: actual === 0,
                last: actual === totalPaginas - 1,
              } as Page<unknown>}
              loading={false}
              setPage={setPagina}
            />
          )}
        </div>
      )}
    </section>
  );
}
