import type { Audit } from "../types";
import styles from "./audit.module.css";
/** Identificadores que no corresponden a una persona. */
const SPECIAL_ACTORS: Record<string, string> = {
  anonymousUser: "Usuario no autenticado",
  SISTEMA: "Sistema",
};
/** Nombre para mostrar: la persona del acceso, o una etiqueta legible. */
export const actorDisplayName = (audit: Audit) =>
  audit.actorNombre ??
  SPECIAL_ACTORS[audit.actorIdentificador] ??
  audit.actorIdentificador;
/** Palabras que el backend guarda sin tilde (los códigos son ASCII). */
const SPANISH_WORDS: Record<string, string> = {
  SESION: "sesión",
  CONTRASENA: "contraseña",
  RECUPERACION: "recuperación",
  MATRICULA: "matrícula",
  SECCION: "sección",
  ANIO: "año",
  PERIODO: "período",
  ACADEMICO: "académico",
  ACADEMICA: "académica",
  CONFIGURACION: "configuración",
  ALUMNO: "estudiante",
};
const toWords = (code: string) =>
  code
    .split("_")
    .filter(Boolean)
    .map((word) => SPANISH_WORDS[word] ?? word.toLowerCase())
    .join(" ");
const capitalize = (text: string) =>
  text.charAt(0).toUpperCase() + text.slice(1);
/** CREAR_USUARIO → "Crear usuario", CAMBIAR_CONTRASENA → "Cambiar contraseña". */
export const actionLabel = (action: string) => capitalize(toWords(action));
export const moduleLabel = (module: string) => capitalize(toWords(module));
/** AlumnoApoderado → "Estudiante apoderado" para el registro afectado. */
export const entityLabel = (entity: string) =>
  actionLabel(entity.replace(/([a-z])([A-Z])/g, "$1_$2").toUpperCase());
/** Color de cada módulo según su área; uno nuevo usa el gris. */
const modulePills: Record<string, string> = {
  SEGURIDAD: styles.moduleSecurity,
  PERSONAS: styles.modulePeople,
  MATRICULA: styles.moduleEnrollment,
  CONFIGURACION_ACADEMICA: styles.moduleAcademic,
  ACADEMICO: styles.moduleAcademic,
  ASISTENCIA: styles.moduleAttendance,
  HORARIOS: styles.moduleSchedule,
  FINANZAS: styles.moduleFinance,
};
export const modulePillClass = (module: string) =>
  `${styles.pill} ${modulePills[module] ?? styles.moduleOther}`;
export const resultLabels: Record<string, string> = {
  EXITOSO: "Exitoso",
  RECHAZADO: "Rechazado",
  FALLIDO: "Fallido",
};
