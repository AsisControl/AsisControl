/** Valores de TipoAula del backend. */
export const CLASSROOM_TYPES = [
  "AULA_REGULAR",
  "LABORATORIO_COMPUTO",
  "LABORATORIO_CIENCIAS",
  "TALLER",
  "BIBLIOTECA",
  "SALA_USOS_MULTIPLES",
] as const;
export type ClassroomType = (typeof CLASSROOM_TYPES)[number];
/** Respuesta de /api/aulas (AulaResponse). `ubicacion` es "" si no se indicó. */
export type Classroom = {
  id: number;
  codigo: string;
  nombre: string;
  tipoAula: ClassroomType;
  capacidad: number;
  ubicacion: string;
  activo: boolean;
  version: number;
};
/** Datos editables de un aula; la versión se agrega al actualizar. */
export type ClassroomInput = {
  codigo: string;
  nombre: string;
  tipoAula: ClassroomType;
  capacidad: number;
  ubicacion: string | null;
};
/** Respuesta de /api/aulas/resumen (AulaResumenResponse). */
export type ClassroomSummary = { habilitadas: number };
export type WeekDay =
  | "LUNES"
  | "MARTES"
  | "MIERCOLES"
  | "JUEVES"
  | "VIERNES"
  | "SABADO";
/** Respuesta de /api/aulas/{id}/horarios (HorarioResponse); horas "08:00:00". */
export type ClassroomSchedule = {
  id: number;
  asignacionCursoId: number;
  cursoId: number;
  curso: string;
  seccionId: number;
  seccion: string;
  docenteId: number;
  docente: string;
  aulaId: number;
  aula: string;
  diaSemana: WeekDay;
  horaInicio: string;
  horaFin: string;
  activo: boolean;
  version: number;
};
