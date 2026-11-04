export type TaskFields = {
  nombre: string;
  descripcion: string;
  fechaPublicacion: string;
  fechaLimite: string;
  puntajeMaximo: string;
  maxArchivosEntrega: string;
};

export const FILE_ACCEPT = ".pdf,.jpg,.jpeg,.png,.doc,.docx,.xls,.xlsx,.ppt,.pptx";

export function fileError(file: File): string {
  if (!file.size) return "El archivo está vacío.";
  if (file.size > 10485760) return "Cada archivo admite hasta 10 MiB con la configuración actual.";
  if (!/\.(pdf|jpe?g|png|docx?|xlsx?|pptx?)$/i.test(file.name))
    return "Selecciona PDF, JPEG, PNG, Word, Excel o PowerPoint.";
  return "";
}

export function validateTask(fields: TaskFields, count: number, now = new Date()) {
  const errors: Record<string, string> = {};
  if (!fields.nombre.trim() || fields.nombre.length > 150) errors.nombre = "Escribe un nombre de hasta 150 caracteres.";
  if (!fields.descripcion.trim() || fields.descripcion.length > 3000) errors.descripcion = "Escribe una descripción de hasta 3000 caracteres.";
  const limit = new Date(fields.fechaLimite);
  const reference = fields.fechaPublicacion ? new Date(fields.fechaPublicacion) : now;
  if (fields.fechaPublicacion && Number.isNaN(reference.getTime()))
    errors.fechaPublicacion = "Indica una fecha de publicación válida.";
  if (!fields.fechaLimite || Number.isNaN(limit.getTime())) errors.fechaLimite = "Indica una fecha límite válida.";
  else if (!errors.fechaPublicacion && limit <= reference)
    errors.fechaLimite = fields.fechaPublicacion
      ? "La fecha límite debe ser posterior a la fecha de publicación."
      : "La fecha límite debe ser posterior al momento actual.";
  for (const [key, min, max] of [["puntajeMaximo", 1, 1000], ["maxArchivosEntrega", 0, 20]] as const) {
    const value = Number(fields[key]);
    if (!fields[key].trim() || !Number.isInteger(value) || value < min || value > max)
      errors[key] = `Introduce un entero entre ${min} y ${max}.`;
  }
  if (count > 20) errors.archivoIds = "La tarea admite hasta 20 adjuntos.";
  return errors;
}
