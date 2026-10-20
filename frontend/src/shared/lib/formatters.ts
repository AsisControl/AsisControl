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
