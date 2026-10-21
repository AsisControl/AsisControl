import type { Persona } from "@/shared/types/person";
/** Valores del enum Especialidad del backend. */
export const teacherSpecialties = [
  "MATEMATICA",
  "COMUNICACION",
  "CIENCIA_Y_TECNOLOGIA",
  "CIENCIAS_SOCIALES",
  "EDUCACION_FISICA",
  "INGLES",
  "ARTE_Y_CULTURA",
  "TUTORIA",
] as const;
export type TeacherSpecialty = (typeof teacherSpecialties)[number];
/** Respuesta de GET /api/docentes y /api/docentes/{id} (DocenteResponse). */
export type Teacher = {
  id: number;
  codigoDocente: string;
  fechaIngreso: string;
  especialidad: TeacherSpecialty;
  persona: Persona;
  activo: boolean;
};
/**
 * Cuerpo de POST /api/docentes y PUT /api/docentes/{id}
 * (DocenteCreateRequest / DocenteUpdateRequest). Confirmar con BE 4.
 */
export type TeacherInput = {
  persona: Omit<Persona, "id" | "nombreCompleto">;
  codigoDocente: string;
  fechaIngreso: string;
  especialidad: TeacherSpecialty;
};
