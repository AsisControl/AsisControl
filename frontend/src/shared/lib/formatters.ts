export const stateLabel = (state: string) =>
  state
    .replaceAll("_", " ")
    .toLowerCase()
    .replace(/^./, (s) => s.toUpperCase());
export const dateLabel = (date: string | null) =>
  date
    ? new Date(date).toLocaleString("es-PE", { timeZone: "America/Lima" })
    : "—";
export const roleLabel = (r: string) =>
  ({
    ADMINISTRADOR: "Administrador",
    DOCENTE: "Docente",
    ALUMNO: "Estudiante",
    ESTUDIANTE: "Estudiante",
    APODERADO: "Apoderado",
  })[r] || r;
/** Fecha corta de Lima: "02 set. 2025"; "—" si no hay fecha. */
export const shortDateLabel = (date: string | null | undefined) =>
  date
    ? new Date(date).toLocaleDateString("es-PE", {
        timeZone: "America/Lima",
        day: "2-digit",
        month: "short",
        year: "numeric",
      })
    : "—";
/** Hora de Lima en formato 24 h: "16:05". */
export const timeLabel = (date: Date) =>
  date.toLocaleTimeString("es-PE", {
    timeZone: "America/Lima",
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
  });
/** "1 curso", "3 cursos". */
export const countLabel = (count: number, singular: string, plural: string) =>
  `${count} ${count === 1 ? singular : plural}`;
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
