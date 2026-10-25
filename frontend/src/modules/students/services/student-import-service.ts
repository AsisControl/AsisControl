import { api, apiBlob } from "@/core/api/client";
import type { ImportResult } from "../types";

const STUDENT_IMPORT_PATH = "/api/alumnos/importaciones";
const STUDENT_IMPORT_TIMEOUT_MS = 120000;

export function downloadStudentTemplate(): Promise<Blob> {
  return apiBlob(`${STUDENT_IMPORT_PATH}/plantilla`);
}

export function importStudents(file: File): Promise<ImportResult> {
  const body = new FormData();
  body.append("archivo", file);
  return api<ImportResult>(STUDENT_IMPORT_PATH, {
    method: "POST",
    body,
    timeoutMs: STUDENT_IMPORT_TIMEOUT_MS,
  });
}
