import { ApiError, message } from "@/core/api/client";
import type { Homonym, StudentErrors } from "./types";

export function homonyms(error: unknown): Homonym[] | null {
  if (
    !(error instanceof ApiError) ||
    error.status !== 409 ||
    error.code !== "HOMONIMIA_DETECTADA"
  )
    return null;
  const details = error.details as { coincidencias?: unknown } | null;
  const values = details?.coincidencias;
  return Array.isArray(values)
    ? values.filter(
        (row): row is Homonym =>
          !!row &&
          typeof row.id === "number" &&
          typeof row.codigoAlumno === "string" &&
          typeof row.nombreCompleto === "string" &&
          typeof row.fechaNacimiento === "string",
      )
    : [];
}

export function studentFieldErrors(error: unknown): StudentErrors {
  if (!(error instanceof ApiError)) return {};
  const allowed = new Set([
    "nombres",
    "apellidoPaterno",
    "apellidoMaterno",
    "tipoDocumento",
    "numeroDocumento",
    "fechaNacimiento",
    "sexo",
    "correo",
    "telefono",
    "direccion",
    "motivo",
  ]);
  const result: StudentErrors = {};
  for (const item of error.fieldErrors) {
    const fieldName = item.field.split(".").at(-1) as keyof StudentErrors;
    if (allowed.has(fieldName) && !result[fieldName])
      result[fieldName] = item.message;
  }
  if (error.code === "DOCUMENTO_DUPLICADO")
    result.numeroDocumento =
      "Este documento ya está registrado. Revisa la ficha existente.";
  if (error.code === "CORREO_DUPLICADO")
    result.correo =
      "Este correo ya está asociado a otra persona. Comprueba el correo de contacto.";
  return result;
}

export function studentErrorMessage(error: unknown): string {
  if (!(error instanceof ApiError)) return message(error);
  // Los mensajes de conflictos no interpolan el documento ni el cuerpo enviado.
  if (error.code === "DOCUMENTO_DUPLICADO")
    return "El documento ya está registrado. Revisa la ficha existente; este duplicado no se puede confirmar.";
  if (error.code === "HOMONIMIA_DETECTADA")
    return "Hay una posible coincidencia. Revisa las fichas antes de confirmar que es otra persona.";
  if (error.code === "VERSION_DESACTUALIZADA")
    return "Otra persona actualizó la ficha. Vuelve a consultarla antes de guardar tus cambios.";
  if (error.status === 401)
    return "Tu sesión terminó. Inicia sesión nuevamente.";
  if (error.status === 403)
    return "No tienes permiso para realizar esta operación.";
  if (error.status === 404)
    return "La ficha ya no está disponible. Actualiza el listado.";
  if (
    error.fieldErrors.some(
      (item) =>
        item.field === "codigoAlumno" &&
        /obligatorio|required/i.test(item.message),
    )
  )
    return (
      "El backend todavía exige un código institucional manual. " +
      "Instala la actualización del backend para que lo genere automáticamente."
    );
  if (Object.keys(studentFieldErrors(error)).length)
    return "Corrige los campos resaltados. Debajo de cada uno se indica cómo hacerlo.";
  if (error.status === 400)
    return error.message &&
      !/^(Datos inv[aá]lidos|Validaci[oó]n|La solicitud contiene datos inv[aá]lidos)$/i.test(
        error.message,
      )
      ? error.message
      : "El servidor no indicó qué dato debe corregirse. " +
          "Comprueba que el backend use el contrato actualizado de fichas de estudiantes.";
  if (error.status === 409 || error.status === 422) return error.message;
  return "No se pudo completar la operación. Inténtalo nuevamente.";
}
