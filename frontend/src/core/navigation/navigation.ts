import type { UserRole } from "@/core/auth/types";
type RolePresentation = { initials: string; title: string; nav: string[] };
export const HOME_NAV = "Inicio";
export const CHANGE_PASSWORD_PATH = "/cambiar-contrasena";
/** Segmento de URL de una opción: "Mis cursos" → "mis-cursos". */
export const navSlug = (label: string) =>
  label
    .normalize("NFD")
    .replace(/[̀-ͯ]/g, "")
    .toLowerCase()
    .trim()
    .replace(/\s+/g, "-");
export const navPath = (label: string) =>
  label === HOME_NAV ? "/" : `/${navSlug(label)}`;
/** Opción del menú que corresponde al primer segmento de la URL. */
export function navFromSegment(
  role: UserRole,
  segment: string | undefined,
): string | null {
  if (!segment) return HOME_NAV;
  return (
    roleDetails[role].nav.find((item) => navSlug(item) === segment) ?? null
  );
}
export const roleLabels: Record<UserRole, string> = {
  ADMINISTRADOR: "Administrador",
  DOCENTE: "Docente",
  ESTUDIANTE: "Estudiante",
  APODERADO: "Apoderado",
};
/** Menú principal por rol. Las pantallas de cada opción están en module-outlet.tsx. */
export const roleDetails: Record<UserRole, RolePresentation> = {
  ADMINISTRADOR: {
    initials: "AR",
    title: "Panel de administración",
    nav: [
      "Inicio",
      "Matrícula",
      "Asistencia",
      "Académico",
      "Horarios",
      "Finanzas",
      "Administración",
    ],
  },
  DOCENTE: {
    initials: "PM",
    title: "Bienvenida, Prof. Patricia Morales",
    nav: [
      "Inicio",
      "Mis cursos",
      "Asistencia",
      "Evaluaciones",
      "Tareas",
      "Horario",
    ],
  },
  ESTUDIANTE: {
    initials: "LG",
    title: "Bienvenido, Luis García",
    nav: ["Inicio", "Tareas", "Progreso", "Asistencia", "Horario"],
  },
  APODERADO: {
    initials: "MR",
    title: "Panel del Apoderado",
    nav: ["Inicio", "Matrícula", "Pagos", "Progreso", "Asistencia"],
  },
};
