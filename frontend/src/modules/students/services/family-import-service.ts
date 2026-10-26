import { api, apiBlob } from "@/core/api/client";
import type {
  FamilyImportResult,
  FamilyImportSlot,
  ImportResult,
} from "../types";

const GUARDIAN_IMPORT_PATH = "/api/apoderados/importaciones";
const RELATIONSHIP_IMPORT_PATH = "/api/apoderados/parentescos/importaciones";
const FAMILY_IMPORT_TIMEOUT_MS = 120000;

function upload(path: string, file: File): Promise<ImportResult> {
  const body = new FormData();
  body.append("archivo", file);
  return api<ImportResult>(path, { method: "POST", body, timeoutMs: FAMILY_IMPORT_TIMEOUT_MS });
}

export function downloadGuardianTemplate(): Promise<Blob> {
  return apiBlob(`${GUARDIAN_IMPORT_PATH}/plantilla`);
}

export function downloadRelationshipTemplate(): Promise<Blob> {
  return apiBlob(`${RELATIONSHIP_IMPORT_PATH}/plantilla`);
}

/** Una fila por apoderado; si su documento ya está registrado, el backend reutiliza a la persona. */
export function importGuardians(file: File): Promise<ImportResult> {
  return upload(GUARDIAN_IMPORT_PATH, file);
}

/** Une el código del estudiante con el documento del apoderado, ambos ya registrados. */
export function importRelationships(file: File): Promise<ImportResult> {
  return upload(RELATIONSHIP_IMPORT_PATH, file);
}

const FAMILY_BULK_PATH = "/api/alumnos/importaciones/familias";

/**
 * Carga masiva todo o nada. Con `confirm` en falso solo valida; en verdadero guarda los tres
 * archivos en una sola transacción, o nada si alguno tiene errores.
 */
export function importFamilies(
  files: Partial<Record<FamilyImportSlot, File>>,
  confirm: boolean,
): Promise<FamilyImportResult> {
  const body = new FormData();
  for (const [slot, file] of Object.entries(files)) if (file) body.append(slot, file);
  return api<FamilyImportResult>(`${FAMILY_BULK_PATH}?confirmar=${confirm}`, {
    method: "POST",
    body,
    timeoutMs: FAMILY_IMPORT_TIMEOUT_MS,
  });
}
