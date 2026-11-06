// asistencia alumnos
import type { StatusTone } from "@/shared/ui";
import type { AsistenciaEstudiante } from "./types";

export const MOTIVOS = ["Cita médica", "Enfermedad", "Trámite documentario", "Asunto familiar", "Otro"];
export const MAX_OBSERVACIONES = 1900;

const MESES = ["Ene", "Feb", "Mar", "Abr", "May", "Jun", "Jul", "Ago", "Sep", "Oct", "Nov", "Dic"];
const MESES_LARGOS = [
  "Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio",
  "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre",
];

/** "2026-09-08" → "08 Sep 2026". */
export function fechaCorta(iso: string) {
  const [anio, mes, dia] = iso.split("-");
  return `${dia} ${MESES[Number(mes) - 1]} ${anio}`;
}

/** "2026-09" → "Septiembre 2026". */
export function etiquetaMes(mes: string) {
  const [anio, numero] = mes.split("-");
  return `${MESES_LARGOS[Number(numero) - 1]} ${anio}`;
}

const esInasistencia = (fila: AsistenciaEstudiante) =>
  fila.estado === "INASISTENCIA" || fila.estado === "SIN_MARCACION";

const esPendiente = (fila: AsistenciaEstudiante) =>
  fila.estadoJustificacion === "ENVIADA" ||
  fila.estadoJustificacion === "EN_REVISION" ||
  fila.estadoJustificacion === "OBSERVADA";

export function presentarEstado(fila: AsistenciaEstudiante): { etiqueta: string; tono: StatusTone } {
  if (fila.estado === "PRESENTE") return { etiqueta: "Presente", tono: "success" };
  if (fila.estado === "TARDANZA") return { etiqueta: "Tardanza", tono: "warning" };
  if (!esInasistencia(fila)) return { etiqueta: "Justificada", tono: "success" };
  if (fila.estadoJustificacion === "RECHAZADA") return { etiqueta: "Rechazada", tono: "danger" };
  if (esPendiente(fila)) return { etiqueta: "Justificación pendiente", tono: "neutral" };
  return { etiqueta: "Inasistencia", tono: "danger" };
}

export function detalleFila(fila: AsistenciaEstudiante) {
  const asistio = fila.estado === "PRESENTE" || fila.estado.startsWith("TARDANZA");
  const hora = asistio ? (fila.horaRegistro?.slice(0, 5) ?? "—") : "—";
  if (fila.estado === "PRESENTE") return hora;
  if (fila.estado === "TARDANZA") return fila.comentario ? `${hora} · ${fila.comentario}` : hora;
  if (fila.estadoJustificacion === "RECHAZADA") return `${hora} · ${fila.observacionRevision ?? "Rechazada"}`;
  if (fila.estadoJustificacion && fila.estadoJustificacion !== "CANCELADA")
    return `${hora} · ${fila.motivoJustificacion}`;
  return `${hora} · Injustificada`;
}

/** Solo se pide una vez por inasistencia; una solicitud cancelada se puede volver a enviar. */
export const puedeJustificar = (fila: AsistenciaEstudiante) =>
  esInasistencia(fila) && (fila.justificacionId === null || fila.estadoJustificacion === "CANCELADA");

/** Presentes y tardanzas cuentan como asistencia; las inasistencias justificadas no. */
export function resumir(filas: AsistenciaEstudiante[]) {
  const presencias = filas.filter((fila) => fila.estado === "PRESENTE").length;
  const tardanzas = filas.filter((fila) => fila.estado.startsWith("TARDANZA")).length;
  const inasistencias = filas.length - presencias - tardanzas;
  const porcentaje = filas.length ? Math.round(((presencias + tardanzas) / filas.length) * 100) : 0;
  return { presencias, tardanzas, inasistencias, porcentaje };
}

export const componerMotivo = (motivo: string, observaciones: string) => {
  const detalle = observaciones.trim();
  if (motivo === "Otro") return detalle;
  return detalle ? `${motivo} — ${detalle}` : motivo;
};
