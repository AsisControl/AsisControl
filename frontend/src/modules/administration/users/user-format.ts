import styles from "./users.module.css";
export const accessStates = [
  "ACTIVO",
  "PENDIENTE",
  "BLOQUEADO_TEMPORALMENTE",
  "DESACTIVADO",
] as const;
export const stateLabels: Record<string, string> = {
  ACTIVO: "Activo",
  PENDIENTE: "Pendiente",
  BLOQUEADO_TEMPORALMENTE: "Bloqueado",
  DESACTIVADO: "Inactivo",
};
const statePills: Record<string, string> = {
  ACTIVO: styles.pillGreen,
  PENDIENTE: styles.pillAmber,
  BLOQUEADO_TEMPORALMENTE: styles.pillRed,
  DESACTIVADO: styles.pillGray,
};
const rolePills: Record<string, string> = {
  ADMINISTRADOR: styles.pillBlue,
  DOCENTE: styles.pillGreen,
  ALUMNO: styles.pillSky,
  ESTUDIANTE: styles.pillSky,
  APODERADO: styles.pillAmber,
};
export const statePillClass = (state: string) =>
  `${styles.pill} ${statePills[state] ?? styles.pillGray}`;
export const rolePillClass = (role: string) =>
  `${styles.pill} ${rolePills[role] ?? styles.pillGray}`;
export const initialsOf = (fullName: string) =>
  fullName
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((word) => word.charAt(0))
    .join("")
    .toUpperCase();
/** Fecha y hora en formato dd/mm/aaaa hh:mm de Lima. */
export const dateTimeLabel = (date: string | null) =>
  date
    ? new Date(date)
        .toLocaleString("es-PE", {
          timeZone: "America/Lima",
          day: "2-digit",
          month: "2-digit",
          year: "numeric",
          hour: "2-digit",
          minute: "2-digit",
          hour12: false,
        })
        .replace(",", "")
    : "Sin registro";
