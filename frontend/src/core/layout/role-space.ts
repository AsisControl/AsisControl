import type { UserRole } from "@/core/auth/types";

type RoleSpace = {
  title: string;
  description: string;
  icon: "panel" | "book" | "graduation" | "people";
  shortcuts: string[];
};

/** Identidad del espacio; rutas y autorización siguen perteneciendo a navegación. */
export const roleSpaces: Record<UserRole, RoleSpace> = {
  ADMINISTRADOR: {
    title: "Panel de administración",
    description: "Organiza la gestión académica y administrativa del colegio.",
    icon: "panel",
    shortcuts: ["Matrícula", "Académico", "Administración"],
  },
  DOCENTE: {
    title: "Aula docente",
    description: "Tus cursos, evaluaciones y seguimiento académico.",
    icon: "book",
    shortcuts: ["Mis cursos", "Evaluaciones", "Progreso"],
  },
  ESTUDIANTE: {
    title: "Portal del estudiante",
    description: "Consulta tus tareas, resultados y horario de clases.",
    icon: "graduation",
    shortcuts: ["Tareas", "Progreso", "Horario"],
  },
  APODERADO: {
    title: "Portal del apoderado",
    description: "Acompaña la matrícula, los pagos y la asistencia de tus estudiantes.",
    icon: "people",
    shortcuts: ["Matrícula", "Pagos", "Asistencia"],
  },
};
