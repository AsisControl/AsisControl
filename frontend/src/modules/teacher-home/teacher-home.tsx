"use client";
import Link from "next/link";
import { useEffect, useState, type ReactNode } from "react";
import { message } from "@/core/api/client";
import { NavIcon } from "@/core/navigation/nav-icons";
import { Feedback, Loading, PageHeader, Skeleton, StatusPill, type StatusTone } from "@/shared/ui";
import { countLabel, timeLabel } from "@/shared/lib/formatters";
import { obtenerInicioDocente } from "./services/inicio-docente-service";
import type { ClaseHoy, InicioDocente, Pendiente } from "./types";
import styles from "./teacher-home.module.css";

const hora = (valor: string) => valor.slice(0, 5);

/** "lunes, 17 de noviembre de 2026" → "Lunes, 17 de noviembre de 2026"; usa la fecha del servidor. */
function fechaLarga(fecha?: string) {
  const dia = fecha ? new Date(`${fecha}T12:00:00`) : new Date();
  const texto = dia.toLocaleDateString("es-PE", { timeZone: "America/Lima", dateStyle: "full" });
  return texto.charAt(0).toUpperCase() + texto.slice(1);
}

function descripcion(inicio: InicioDocente | null, actualizado: string): ReactNode {
  const anio = inicio?.anioAcademico
    ? ` · Año académico ${inicio.anioAcademico}${inicio.periodo ? ` — ${inicio.periodo}` : ""}`
    : "";
  return (
    <>
      {fechaLarga(inicio?.fecha)}
      {anio}
      {actualizado && (
        <>
          <br />
          <small className={styles.actualizado}>Última actualización: {actualizado}</small>
        </>
      )}
    </>
  );
}

function estadoClase(clase: ClaseHoy): { etiqueta: string; tono: StatusTone } {
  if (clase.momento === "EN_CURSO") return { etiqueta: "En curso", tono: "info" };
  if (clase.momento === "PROXIMA") return { etiqueta: "Próxima", tono: "neutral" };
  return clase.estado === "TOMADA"
    ? { etiqueta: "Asistencia tomada", tono: "success" }
    : { etiqueta: "Asistencia pendiente", tono: "warning" };
}

/** Texto y destino de cada pendiente: lleva directo a la pantalla donde se atiende. */
function describirPendiente(pendiente: Pendiente) {
  switch (pendiente.tipo) {
    case "TAREA":
      return {
        titulo: `Revisar ${countLabel(pendiente.cantidad, "entrega", "entregas")} — ${pendiente.titulo}`,
        detalle: `Tarea · ${pendiente.detalle}`,
        href: `/tareas/${pendiente.id}/entregas`,
      };
    case "EVALUACION":
      return {
        titulo: pendiente.cantidad
          ? `Registrar ${countLabel(pendiente.cantidad, "nivel", "niveles")} — ${pendiente.titulo}`
          : `Publicar notas — ${pendiente.titulo}`,
        detalle: `Evaluación · ${pendiente.detalle}`,
        href: `/evaluaciones/registro-de-calificaciones/${pendiente.id}`,
      };
    default:
      return {
        titulo: `Revisar ${countLabel(pendiente.cantidad, "justificación", "justificaciones")} de inasistencia`,
        detalle: `Asistencia · ${pendiente.detalle}`,
        href: "/asistencia",
      };
  }
}

/** Espera de los cuatro indicadores, con las mismas tarjetas que los reales. */
function IndicadoresSkeleton() {
  return (
    <div className={styles.indicadores}>
      {["Clases", "Asistencias", "Revisar", "Bloques"].map((clave) => (
        <article className={styles.indicador} key={clave}>
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

/** Espera de «Clases de hoy» y «Pendientes académicos»; el lector ya oye «Cargando tu inicio…». */
function TarjetasSkeleton() {
  return (
    <>
      <article className={styles.tarjeta} aria-hidden="true">
        <Skeleton width={120} height={18} />
        {[0, 1, 2].map((clase) => (
          <div className={styles.clase} key={clase}>
            <div className={styles.claseFila}>
              <div className={styles.esqueletoTexto}>
                <Skeleton width={110} height={14} />
                <Skeleton width={150} height={12} />
              </div>
              <div className={styles.claseHora}>
                <Skeleton width={92} height={12} />
                <Skeleton width={70} height={20} radius={999} />
              </div>
            </div>
          </div>
        ))}
      </article>
      <article className={styles.tarjeta} aria-hidden="true">
        <Skeleton width={170} height={18} />
        <ul className={styles.pendientes}>
          {["78%", "64%", "84%", "58%"].map((ancho) => (
            <li key={ancho}>
              <div className={styles.esqueletoTexto}>
                <Skeleton width={ancho} height={12} />
                <Skeleton width="40%" height={10} />
              </div>
              <Skeleton width={48} height={20} radius={999} />
            </li>
          ))}
        </ul>
      </article>
    </>
  );
}

/** Inicio del docente: clases de hoy, indicadores y pendientes académicos con datos reales. */
export function TeacherHome({ fullName, quickActions }: { fullName: string; quickActions?: ReactNode }) {
  const [inicio, setInicio] = useState<InicioDocente | null>(null);
  const [error, setError] = useState("");
  const [cargando, setCargando] = useState(true);
  const [actualizado, setActualizado] = useState("");
  const [tick, setTick] = useState(0);

  useEffect(() => {
    let activo = true;
    obtenerInicioDocente()
      .then((datos) => {
        if (!activo) return;
        setInicio(datos);
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
  }, [tick]);

  function actualizar() {
    setCargando(true);
    setTick((valor) => valor + 1);
  }

  const indicadores = inicio
    ? [
        { valor: inicio.clasesHoy.length, etiqueta: "Clases del día", icono: "Mis cursos", tono: styles.primario },
        { valor: inicio.asistenciasPendientes, etiqueta: "Asistencias pendientes", icono: "Asistencia", tono: styles.advertencia },
        { valor: inicio.porRevisar, etiqueta: "Evaluaciones y tareas por revisar", icono: "Evaluaciones", tono: styles.advertencia },
        { valor: inicio.bloquesSemanales, etiqueta: "Bloques en mi horario", icono: "Horario", tono: styles.info },
      ]
    : [];

  return (
    <section>
      <PageHeader
        title={`Bienvenido/a, ${fullName}`}
        description={descripcion(inicio, actualizado)}
        actions={
          <>
            <button type="button" onClick={actualizar} disabled={cargando}>
              {cargando && inicio ? "Actualizando…" : "Actualizar"}
            </button>
            <Link className={`primary-action ${styles.enlace}`} href="/tareas">
              Nueva tarea
            </Link>
          </>
        }
      />
      <Feedback error={error} />
      {!inicio && !error && <Loading skeleton={<IndicadoresSkeleton />}>Cargando tu inicio…</Loading>}
      {inicio && (
        <div className={styles.indicadores}>
          {indicadores.map(({ valor, etiqueta, icono, tono }) => (
            <article className={styles.indicador} key={etiqueta}>
              <span className={`${styles.indicadorIcono} ${tono}`} aria-hidden="true">
                <NavIcon label={icono} />
              </span>
              <div>
                <strong className={tono}>{valor}</strong>
                <span>{etiqueta}</span>
              </div>
            </article>
          ))}
        </div>
      )}
      {/* Las acciones rápidas no dependen de los datos: siguen disponibles aunque el inicio falle. */}
      <div className={styles.columnas}>
        {!inicio && !error && <TarjetasSkeleton />}
        {inicio && (
          <>
            <article className={styles.tarjeta}>
              <h3>Clases de hoy</h3>
              {!inicio.clasesHoy.length && <p className={styles.vacio}>No tienes clases programadas para hoy.</p>}
              {inicio.clasesHoy.map((clase) => {
                const estado = estadoClase(clase);
                const sesion = `/mis-cursos/${clase.asignacionCursoId}/asistencia/${clase.sesionId}`;
                return (
                  <div className={clase.momento === "EN_CURSO" ? styles.claseActual : styles.clase} key={clase.sesionId}>
                    <div className={styles.claseFila}>
                      <div>
                        <strong>{clase.curso}</strong>
                        <p>{clase.seccion} · {clase.aula}</p>
                      </div>
                      <div className={styles.claseHora}>
                        <time>{hora(clase.horaInicio)} – {hora(clase.horaFin)}</time>
                        <StatusPill state={clase.momento} label={estado.etiqueta} tone={estado.tono} />
                      </div>
                    </div>
                    {clase.momento !== "PROXIMA" && (
                      <div className={styles.claseAcciones}>
                        {clase.puedeTomar ? (
                          <Link className={`primary-action ${styles.enlace}`} href={sesion}>
                            Tomar asistencia
                          </Link>
                        ) : (
                          <Link className={styles.secundario} href={sesion}>
                            Ver asistencia
                          </Link>
                        )}
                        <Link className={styles.secundario} href={`/mis-cursos/${clase.asignacionCursoId}/evaluaciones`}>
                          Evaluar
                        </Link>
                      </div>
                    )}
                  </div>
                );
              })}
            </article>
            <article className={styles.tarjeta}>
              <h3>Pendientes académicos</h3>
              <p className={styles.resumen}>{countLabel(inicio.pendientes.length, "pendiente", "pendientes")}</p>
              {!inicio.pendientes.length ? (
                <p className={styles.vacio}>No tienes pendientes académicos.</p>
              ) : (
                <ul className={styles.pendientes}>
                  {inicio.pendientes.map((pendiente) => {
                    const { titulo, detalle, href } = describirPendiente(pendiente);
                    return (
                      <li key={`${pendiente.tipo}-${pendiente.id ?? "grupo"}`}>
                        <Link href={href}>
                          <span>{titulo}</span>
                          <small>{detalle}</small>
                        </Link>
                        <StatusPill
                          state={pendiente.prioridad}
                          label={pendiente.prioridad === "ALTA" ? "Alta" : "Media"}
                          tone={pendiente.prioridad === "ALTA" ? "danger" : "warning"}
                        />
                      </li>
                    );
                  })}
                </ul>
              )}
            </article>
          </>
        )}
        {quickActions}
      </div>
    </section>
  );
}
