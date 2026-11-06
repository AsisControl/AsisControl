import { ApiError, message } from "@/core/api/client";

const CHECK_IN_ERRORS: Record<string, string> = {
  QR_EXPIRADO:
    "El código QR ya venció. Escanea el código que se muestra ahora en el punto de marcación.",
  QR_NO_VIGENTE: "Este código QR ya fue usado o reemplazado. Escanea el código actual.",
  QR_NO_ENCONTRADO: "El código escaneado no es un QR de asistencia válido.",
};

/** Mensaje para el docente cuando el backend rechaza la marcación. */
export function checkInErrorMessage(error: unknown) {
  if (error instanceof ApiError) {
    if (CHECK_IN_ERRORS[error.code]) return CHECK_IN_ERRORS[error.code];
    if (error.status === 401) return "Tu sesión terminó. Vuelve a iniciar sesión para marcar.";
    if (error.status === 403)
      return "Tu perfil docente no está habilitado para marcar asistencia.";
  }
  return message(error);
}

/** Mensaje para el punto de marcación (emitir o renovar el código). */
export function qrErrorMessage(error: unknown) {
  if (error instanceof ApiError && error.status === 403)
    return "No tienes permiso para emitir códigos de asistencia.";
  return message(error);
}
