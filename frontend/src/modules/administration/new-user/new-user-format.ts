/** MATEMATICA → "Matemática"; los códigos del backend no llevan tildes. */
const SPECIALTY_LABELS: Record<string, string> = {
  MATEMATICA: "Matemática",
  COMUNICACION: "Comunicación",
  CIENCIA_Y_TECNOLOGIA: "Ciencia y Tecnología",
  CIENCIAS_SOCIALES: "Ciencias Sociales",
  EDUCACION_FISICA: "Educación Física",
  INGLES: "Inglés",
  ARTE_Y_CULTURA: "Arte y Cultura",
  TUTORIA: "Tutoría",
};
export const specialtyLabel = (specialty: string) =>
  SPECIALTY_LABELS[specialty] ?? specialty.replaceAll("_", " ");
