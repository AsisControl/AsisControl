import { ApiError, fieldErrorsOf as apiFieldErrorsOf } from "@/core/api/client";

/** Presentación local: conserva las reglas y evita mostrar detalles internos en Tareas. */
export function message(error: unknown, fallback = "No se pudo completar la acción. Inténtalo de nuevo."): string {
  const text = error instanceof Error ? error.message : typeof error === "string" ? error : "";
  if (text.startsWith("No se pudo conectar con el servidor"))
    return "No se pudo conectar. Revisa tu conexión e inténtalo de nuevo.";
  if (text.startsWith("El servidor tardó demasiado"))
    return "La solicitud tardó demasiado. Inténtalo de nuevo.";
  if (error instanceof ApiError && error.code === "VERSION_DESACTUALIZADA")
    return "Los datos cambiaron. Actualiza la información antes de guardar.";
  if (error instanceof ApiError) {
    const unavailable: Record<string, string> = {
      TAREA_NO_ENCONTRADA: "La tarea ya no está disponible. Actualiza el listado.",
      ENTREGA_NO_ENCONTRADA: "La entrega ya no está disponible. Actualiza el seguimiento.",
      ASIGNACION_CURSO_NO_ENCONTRADA: "El curso y la sección ya no están disponibles. Actualiza tus cursos.",
      ARCHIVO_NO_ENCONTRADO: "El archivo ya no está disponible.",
    };
    if (unavailable[error.code]) return unavailable[error.code];
  }
  if (!text || /backend|servidor|contrato|persistencia|desvincul|versi[oó]n|\bids?\b|[A-Z][A-Z0-9]*(?:_[A-Z0-9]+)+|exception|\bsql\b|jdbc|https?:\/\/|\/api\/|stack\s*trace|must (?:not|be)|size must/i.test(text))
    return fallback;
  return text;
}

export function fieldErrorsOf(error: unknown): Record<string, string> {
  return Object.fromEntries(Object.entries(apiFieldErrorsOf(error)).map(([field, text]) =>
    [field, message(text, "Revisa este campo antes de continuar.")],
  ));
}
