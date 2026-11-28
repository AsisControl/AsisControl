const weightFormat = new Intl.NumberFormat("es-ES", { maximumFractionDigits: 2 });
export const formatWeight = (value: number) => `${weightFormat.format(value)} %`;

export const stateLabel = (state: string) =>
  state
    .replaceAll("_", " ")
    .toLowerCase()
    .replace(/^./, (s) => s.toUpperCase());
export const dateLabel = (date: string | null) =>
  date
    ? new Date(date).toLocaleString("es-PE", {
        timeZone: "America/Lima",
        day: "2-digit",
        month: "2-digit",
        year: "numeric",
        hour: "2-digit",
        minute: "2-digit",
        second: "2-digit",
        hour12: false,
      }).replace(",", "")
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
        month: "2-digit",
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
export const SPECIALTY_LABELS: Record<string, string> = {
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

/** OBS-01: minutos de horario en horas legibles, como el backend: "4 h 30 min", "30 min", "4 h". */
export function hoursLabel(minutes: number) {
  const hours = Math.floor(minutes / 60);
  const rest = minutes % 60;
  if (!rest) return `${hours} h`;
  return hours ? `${hours} h ${rest} min` : `${rest} min`;
} 
