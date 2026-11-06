import type { EstadoJustificacion, EstadoMarca } from "./types";

const DIAS = ["Dom", "Lun", "Mar", "Mié", "Jue", "Vie", "Sáb"];

/** "2026-08-17" → "17-08-2026"; la fecha llega del backend y no se ajusta por zona horaria. */
export const fechaCorta = (iso: string) => iso.slice(0, 10).split("-").reverse().join("-");

/** "2026-08-17" → "Lun, 17-08-2026". */
export function fechaSesion(iso: string) {
  const [anio, mes, dia] = iso.split("-").map(Number);
  return `${DIAS[new Date(Date.UTC(anio, mes - 1, dia)).getUTCDay()]}, ${fechaCorta(iso)}`;
}

/** "13:30:00" → "01:30 PM". */
export function horaSesion(hora: string) {
  const [h, m] = hora.split(":").map(Number);
  return `${String(h % 12 || 12).padStart(2, "0")}:${String(m).padStart(2, "0")} ${h < 12 ? "AM" : "PM"}`;
}

/** "2026-08-17T09:40:00" → "17-08-2026 a las 09:40". */
export const fechaHora = (iso: string) => `${fechaCorta(iso)} a las ${iso.slice(11, 16)}`;

export const ESTADOS_MARCA: { estado: EstadoMarca; etiqueta: string }[] = [
  { estado: "PRESENTE", etiqueta: "Presente" },
  { estado: "TARDANZA", etiqueta: "Tardanza" },
  { estado: "INASISTENCIA", etiqueta: "Inasistencia" },
];

export const etiquetaMarca = (estado: string) =>
  ESTADOS_MARCA.find((item) => estado.startsWith(item.estado))?.etiqueta ?? estado;

export type Indicador = { etiqueta: string; tono: "info" | "neutral" | "danger" };

/** Lectura de la justificación, separada del estado de asistencia. */
export function indicadorJustificacion(estado: EstadoJustificacion | null): Indicador {
  if (estado === "APROBADA") return { etiqueta: "Justificada", tono: "info" };
  if (estado === "RECHAZADA") return { etiqueta: "Rechazada", tono: "danger" };
  if (estado === null || estado === "CANCELADA")
    return { etiqueta: "Sin justificación", tono: "neutral" };
  return { etiqueta: "Justificación pendiente", tono: "neutral" };
}

const normalizar = (texto: string) =>
  texto
    .normalize("NFD")
    .replace(/[̀-ͯ]/g, "")
    .toLowerCase();

export const coincideBusqueda = (nombre: string, busqueda: string) =>
  normalizar(nombre).includes(normalizar(busqueda.trim()));
