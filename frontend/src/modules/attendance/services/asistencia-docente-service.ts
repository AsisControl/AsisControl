import { api } from "@/core/api/client";
import { normalizePage } from "@/shared/lib/pagination";
import type { Page } from "@/shared/types/api";
import type { AsistenciaDocente, CodigoQR, DocenteOption } from "../types";

export const ASISTENCIA_DOCENTES_PATH = "/api/asistencias/docentes";
/** Vigencia de cada QR; el backend acepta de 30 a 900 segundos. */
export const QR_VIGENCIA_SEGUNDOS = 60;

export function emitirQr(docenteId: number, vigenciaSegundos = QR_VIGENCIA_SEGUNDOS) {
  return api<CodigoQR>(`${ASISTENCIA_DOCENTES_PATH}/qr`, {
    method: "POST",
    body: JSON.stringify({ docenteId, vigenciaSegundos }),
  });
}

/** Invalida el código anterior y devuelve uno nuevo para el mismo docente. */
export function renovarQr(id: number, vigenciaSegundos = QR_VIGENCIA_SEGUNDOS) {
  return api<CodigoQR>(
    `${ASISTENCIA_DOCENTES_PATH}/qr/${id}/renovar?vigenciaSegundos=${vigenciaSegundos}`,
    { method: "POST" },
  );
}

export function consultarQr(id: number) {
  return api<CodigoQR>(`${ASISTENCIA_DOCENTES_PATH}/qr/${id}`);
}

export function cancelarQr(id: number) {
  return api<null>(`${ASISTENCIA_DOCENTES_PATH}/qr/${id}/cancelar`, { method: "POST" });
}

/**
 * Solo se envía el token leído. La hora y el estado (Presente/Tardanza) los
 * decide el servidor: nunca se manda la hora del dispositivo.
 */
export function marcarAsistencia(token: string) {
  return api<AsistenciaDocente>(`${ASISTENCIA_DOCENTES_PATH}/marcar`, {
    method: "POST",
    body: JSON.stringify({ token }),
  });
}

/** Para un docente, el backend devuelve solo sus propias marcaciones. */
export async function listarMisMarcaciones() {
  const page = await api<Page<AsistenciaDocente>>(
    `${ASISTENCIA_DOCENTES_PATH}?page=0&size=10&sort=fechaJornada,desc`,
  );
  return normalizePage(page).content;
}

export async function listarDocentes(): Promise<DocenteOption[]> {
  const page = await api<Page<Record<string, unknown>>>("/api/docentes?page=0&size=200");
  return normalizePage(page)
    .content.flatMap(toDocenteOption)
    .sort((a, b) => a.label.localeCompare(b.label, "es"));
}

const text = (value: unknown) => (typeof value === "string" ? value.trim() : "");

/** Nombre visible del docente; admite datos planos o anidados en `persona`. */
export function toDocenteOption(raw: Record<string, unknown>): DocenteOption[] {
  const id = Number(raw.id);
  if (!Number.isSafeInteger(id) || id <= 0) return [];
  const persona =
    raw.persona && typeof raw.persona === "object"
      ? (raw.persona as Record<string, unknown>)
      : raw;
  const name =
    text(raw.nombreCompleto) ||
    text(persona.nombreCompleto) ||
    [persona.nombres, persona.apellidoPaterno, persona.apellidoMaterno]
      .map(text)
      .filter(Boolean)
      .join(" ");
  const document = text(persona.numeroDocumento) || text(raw.numeroDocumento);
  return [{ id, label: `${name || `Docente ${id}`}${document ? ` · ${document}` : ""}` }];
}
