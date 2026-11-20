import { ApiError, message } from "@/core/api/client";

/** Texto para el docente; los errores de negocio del backend ya vienen en español. */
export function evidenceError(error: unknown) {
  if (error instanceof ApiError && error.status === 413)
    return "El archivo supera el tamaño permitido (10 MiB).";
  return message(error);
}
