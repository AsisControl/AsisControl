import { api, apiBlob } from "@/core/api/client";
import type { ImportResult } from "@/modules/students";

const TEACHER_IMPORT_PATH = "/api/docentes/importaciones";
const TEACHER_IMPORT_TIMEOUT_MS = 120000;

export function descargarPlantillaDocentes(): Promise<Blob> {
  return apiBlob(`${TEACHER_IMPORT_PATH}/plantilla`);
}

/** Con `confirmar` en falso solo analiza el archivo; en verdadero crea las filas válidas. */
export function importarDocentes(archivo: File, confirmar: boolean): Promise<ImportResult> {
  const body = new FormData();
  body.append("archivo", archivo);
  return api<ImportResult>(`${TEACHER_IMPORT_PATH}?confirmar=${confirmar}`, {
    method: "POST",
    body,
    timeoutMs: TEACHER_IMPORT_TIMEOUT_MS,
  });
}
