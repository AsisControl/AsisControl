import type { CodigoQR, EstadoAsistencia } from "../types";

export const ESTADO_LABEL: Record<EstadoAsistencia, string> = {
  PRESENTE: "Presente",
  TARDANZA: "Tardanza",
  SIN_MARCACION: "Sin marcación",
  INASISTENCIA: "Inasistencia",
  INASISTENCIA_JUSTIFICADA: "Inasistencia justificada",
  TARDANZA_JUSTIFICADA: "Tardanza justificada",
};

/** "2026-11-06T07:52:13" → "07:52". Se muestra la hora tal como la registró el servidor. */
export function timeOf(dateTime: string | null) {
  const match = /T(\d{2}):(\d{2})/.exec(dateTime ?? "");
  return match ? `${match[1]}:${match[2]}` : "—";
}

/** "2026-11-06" → "06/11/2026". */
export function dateOf(date: string) {
  const match = /^(\d{4})-(\d{2})-(\d{2})/.exec(date);
  return match ? `${match[3]}/${match[2]}/${match[1]}` : date;
}

function parseServerDateTime(value: string) {
  const match =
    /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})(?::(\d{2})(?:\.(\d+))?)?/.exec(value);
  if (!match) return Number.NaN;
  const [, y, mo, d, h, mi, s = "0", fraction = "0"] = match;
  return Date.UTC(+y, +mo - 1, +d, +h, +mi, +s, +fraction.slice(0, 3).padEnd(3, "0"));
}

/**
 * Duración del código según el servidor (expiración − emisión). Así la cuenta
 * regresiva no depende de que el reloj del equipo esté bien configurado.
 */
export function qrLifetimeMs(code: CodigoQR, fallbackSeconds: number) {
  const ms =
    parseServerDateTime(code.fechaHoraExpiracion) -
    parseServerDateTime(code.fechaHoraEmision);
  return Number.isFinite(ms) && ms > 0 ? ms : fallbackSeconds * 1000;
}

/** Fecha de hoy en Lima (AAAA-MM-DD), solo para ubicar la marcación del día en el historial. */
export function todayInLima() {
  return new Intl.DateTimeFormat("en-CA", { timeZone: "America/Lima" }).format(new Date());
}
