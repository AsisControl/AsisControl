// asistencia alumnos
import type { StatusTone } from "@/shared/ui";
import type { AsistenciaEstudiante, BloqueHorario } from "./types";

export const MOTIVOS = ["Cita médica", "Enfermedad", "Trámite documentario", "Asunto familiar", "Otro"];
export const MAX_OBSERVACIONES = 1900;

const MESES = ["Ene", "Feb", "Mar", "Abr", "May", "Jun", "Jul", "Ago", "Sep", "Oct", "Nov", "Dic"];
const DIAS = ["Dom", "Lun", "Mar", "Mié", "Jue", "Vie", "Sáb"];

/** "2026-09-08" → "08 Sep 2026". */
export function fechaCorta(iso: string) {
  const [anio, mes, dia] = iso.split("-");
  return `${dia} ${MESES[Number(mes) - 1]} ${anio}`;
}

/** Deja solo dígitos y los agrupa como dd/mm/aaaa mientras se escribe ("0809" → "08/09"). */
export function enmascararFecha(texto: string) {
  const digitos = texto.replace(/\D/g, "").slice(0, 8);
  return [digitos.slice(0, 2), digitos.slice(2, 4), digitos.slice(4)].filter(Boolean).join("/");
}

/** "08/09/2026" → "2026-09-08"; null si el texto no es una fecha de calendario completa y real. */
export function parsearFecha(texto: string) {
  const coincide = /^(\d{2})\/(\d{2})\/(\d{4})$/.exec(texto);
  if (!coincide) return null;
  const [dia, mes, anio] = coincide.slice(1).map(Number);
  const fecha = new Date(Date.UTC(anio, mes - 1, dia));
  const real = fecha.getUTCFullYear() === anio && fecha.getUTCMonth() === mes - 1 && fecha.getUTCDate() === dia;
  return real && anio >= 1900 ? `${coincide[3]}-${coincide[2]}-${coincide[1]}` : null;
}

/** "2026-11-06" → "Vie, 06 Nov 2026". */
export function fechaConDia(iso: string) {
  const [anio, mes, dia] = iso.split("-").map(Number);
  return `${DIAS[new Date(Date.UTC(anio, mes - 1, dia)).getUTCDay()]}, ${fechaCorta(iso)}`;
}

/** Hora de inicio de la clase, la misma que ve el docente; "—" si el registro no tiene sesión. */
/** Deja solo dígitos y los agrupa como dd/mm/aaaa mientras se escribe ("0809" → "08/09"). */
export function enmascararFecha(texto: string) {
  const digitos = texto.replace(/\D/g, "").slice(0, 8);
  return [digitos.slice(0, 2), digitos.slice(2, 4), digitos.slice(4)].filter(Boolean).join("/");
}

/** "08/09/2026" → "2026-09-08"; null si el texto no es una fecha de calendario completa y real. */
export function parsearFecha(texto: string) {
  const coincide = /^(\d{2})\/(\d{2})\/(\d{4})$/.exec(texto);
  if (!coincide) return null;
  const [dia, mes, anio] = coincide.slice(1).map(Number);
  const fecha = new Date(Date.UTC(anio, mes - 1, dia));
  const real = fecha.getUTCFullYear() === anio && fecha.getUTCMonth() === mes - 1 && fecha.getUTCDate() === dia;
  return real && anio >= 1900 ? `${coincide[3]}-${coincide[2]}-${coincide[1]}` : null;
}

export const horaDe = (fila: AsistenciaEstudiante) => fila.horaSesion?.slice(0, 5) ?? "—";

export type EstadoBase = "PRESENTE" | "TARDANZA" | "INASISTENCIA" | "SIN_MARCAR";

export const ESTADOS: { valor: EstadoBase; etiqueta: string; plural: string; tono: StatusTone }[] = [
  { valor: "PRESENTE", etiqueta: "Presente", plural: "Presentes", tono: "success" },
  { valor: "TARDANZA", etiqueta: "Tardanza", plural: "Tardanzas", tono: "warning" },
  { valor: "INASISTENCIA", etiqueta: "Inasistencia", plural: "Inasistencias", tono: "danger" },
  { valor: "SIN_MARCAR", etiqueta: "Sin marcar", plural: "Sin marcar", tono: "neutral" },
];

export const NOTA_SIN_MARCAR = "El docente no registró esta asistencia";

/** La marca justificada conserva su estado base: la justificación se muestra en su propia columna. */
export const estadoBase = (fila: AsistenciaEstudiante): EstadoBase =>
  fila.estado === "PRESENTE"
    ? "PRESENTE"
    : fila.estado.startsWith("TARDANZA")
      ? "TARDANZA"
      : fila.estado === "SIN_MARCACION"
        ? "SIN_MARCAR"
        : "INASISTENCIA";

export const presentarEstado = (fila: AsistenciaEstudiante) =>
  ESTADOS.find((item) => item.valor === estadoBase(fila))!;

const esPendiente = (fila: AsistenciaEstudiante) =>
  fila.estadoJustificacion === "ENVIADA" ||
  fila.estadoJustificacion === "EN_REVISION" ||
  fila.estadoJustificacion === "OBSERVADA";

/** null cuando no hay nada que justificar: asistió, o llegó tarde sin solicitud. */
export function presentarJustificacion(
  fila: AsistenciaEstudiante,
): { etiqueta: string; tono: StatusTone; detalle: string | null } | null {
  const base = estadoBase(fila);
  if (base === "PRESENTE" || base === "SIN_MARCAR") return null;
  if (fila.estado.endsWith("_JUSTIFICADA") || fila.estadoJustificacion === "APROBADA")
    return { etiqueta: "Justificada", tono: "info", detalle: fila.motivoJustificacion };
  if (esPendiente(fila)) return { etiqueta: "Pendiente", tono: "warning", detalle: fila.motivoJustificacion };
  if (fila.estadoJustificacion === "RECHAZADA")
    return { etiqueta: "Rechazada", tono: "danger", detalle: fila.observacionRevision };
  if (base === "INASISTENCIA") return { etiqueta: "Sin justificar", tono: "neutral", detalle: null };
  return null;
}

/** Solo se pide una vez por inasistencia; una solicitud cancelada se puede volver a enviar. */
export const puedeJustificar = (fila: AsistenciaEstudiante) =>
  fila.estado === "INASISTENCIA" && (fila.justificacionId === null || fila.estadoJustificacion === "CANCELADA");

export const sinJustificar = (filas: AsistenciaEstudiante[]) =>
  filas.filter((fila) => presentarJustificacion(fila)?.etiqueta === "Sin justificar").length;

/**
 * Presentes y tardanzas cuentan como asistencia; las inasistencias justificadas no. "Sin marcar"
 * queda fuera del cálculo: no se sabe si asistió.
 */
export function resumir(filas: AsistenciaEstudiante[]) {
  const contar = (estado: EstadoBase) => filas.filter((fila) => estadoBase(fila) === estado).length;
  const totales: Record<EstadoBase, number> = {
    PRESENTE: contar("PRESENTE"),
    TARDANZA: contar("TARDANZA"),
    INASISTENCIA: contar("INASISTENCIA"),
    SIN_MARCAR: contar("SIN_MARCAR"),
  };
  const evaluadas = filas.length - totales.SIN_MARCAR;
  const porcentaje = evaluadas ? Math.round(((totales.PRESENTE + totales.TARDANZA) / evaluadas) * 100) : 0;
  return { totales, porcentaje };
}

/** Año académico del registro; sin el dato del backend se usa el de la fecha. */
export const anioDe = (fila: AsistenciaEstudiante) => fila.anioAcademico ?? Number(fila.fechaRegistro.slice(0, 4));

/** "YYYY-MM" del mes actual o de los anteriores, según el dispositivo. */
export function mesRelativo(atras: number, hoy = new Date()) {
  const fecha = new Date(hoy.getFullYear(), hoy.getMonth() - atras, 1);
  return `${fecha.getFullYear()}-${String(fecha.getMonth() + 1).padStart(2, "0")}`;
}

export type FiltroPeriodo = "TODO" | "MES" | "MES_ANTERIOR" | "PERSONALIZADO" | `P:${string}`;

export function enPeriodo(fila: AsistenciaEstudiante, periodo: FiltroPeriodo, desde: string, hasta: string) {
  const fecha = fila.fechaRegistro;
  if (periodo === "MES") return fecha.startsWith(mesRelativo(0));
  if (periodo === "MES_ANTERIOR") return fecha.startsWith(mesRelativo(1));
  if (periodo === "PERSONALIZADO") return (!desde || fecha >= desde) && (!hasta || fecha <= hasta);
  if (periodo.startsWith("P:")) return fila.periodo === periodo.slice(2);
  return true;
}

export function etiquetaPeriodo(periodo: FiltroPeriodo, desde: string, hasta: string) {
  if (periodo === "MES") return "Este mes";
  if (periodo === "MES_ANTERIOR") return "Mes anterior";
  if (periodo.startsWith("P:")) return periodo.slice(2);
  if (periodo === "PERSONALIZADO") {
    if (desde && hasta) return `Del ${fechaCorta(desde)} al ${fechaCorta(hasta)}`;
    if (desde) return `Desde el ${fechaCorta(desde)}`;
    if (hasta) return `Hasta el ${fechaCorta(hasta)}`;
    return "Personalizado";
  }
  return "Todo el año";
}

const DIAS_SEMANA = ["DOMINGO", "LUNES", "MARTES", "MIERCOLES", "JUEVES", "VIERNES", "SABADO"];

/** "YYYY-MM-DD" del día en el dispositivo. */
export function fechaLocal(hoy = new Date()) {
  return `${hoy.getFullYear()}-${String(hoy.getMonth() + 1).padStart(2, "0")}-${String(hoy.getDate()).padStart(2, "0")}`;
}

export type ClaseDelDia = { clave: string; curso: string; hora: string; registro: AsistenciaEstudiante | null };

/**
 * Clases de hoy según el horario, con su asistencia si el docente ya la tomó. Los bloques
 * seguidos del mismo curso son una sola sesión, igual que en la vista del docente.
 */
export function clasesDelDia(bloques: BloqueHorario[], asistencias: AsistenciaEstudiante[], hoy = new Date()) {
  const fecha = fechaLocal(hoy);
  const encuentros: BloqueHorario[] = [];
  bloques
    .filter((bloque) => bloque.diaSemana === DIAS_SEMANA[hoy.getDay()])
    .sort((a, b) => a.asignacionCursoId - b.asignacionCursoId || a.horaInicio.localeCompare(b.horaInicio))
    .forEach((bloque) => {
      const anterior = encuentros.at(-1);
      if (anterior?.asignacionCursoId === bloque.asignacionCursoId && anterior.horaFin === bloque.horaInicio)
        anterior.horaFin = bloque.horaFin;
      else encuentros.push({ ...bloque });
    });
  const registros = asistencias.filter((fila) => fila.fechaRegistro === fecha);
  const usados = new Set<number>();
  const clases: ClaseDelDia[] = encuentros.map((encuentro) => {
    const hora = encuentro.horaInicio.slice(0, 5);
    const registro = registros.find(
      (fila) => fila.asignacionCursoId === encuentro.asignacionCursoId && fila.horaSesion?.slice(0, 5) === hora,
    );
    if (registro) usados.add(registro.id);
    return { clave: `${encuentro.asignacionCursoId}-${hora}`, curso: encuentro.curso, hora, registro: registro ?? null };
  });
  registros
    .filter((fila) => !usados.has(fila.id))
    .forEach((fila) => clases.push({ clave: `r${fila.id}`, curso: fila.curso, hora: horaDe(fila), registro: fila }));
  return clases.sort((a, b) => a.hora.localeCompare(b.hora));
}

export const componerMotivo = (motivo: string, observaciones: string) => {
  const detalle = observaciones.trim();
  if (motivo === "Otro") return detalle;
  return detalle ? `${motivo} — ${detalle}` : motivo;
};
