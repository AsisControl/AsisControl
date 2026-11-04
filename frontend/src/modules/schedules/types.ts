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
/** Filtro de estado de /api/asignaciones-cursos (EstadoAsignacion). */
export type AssignmentState = "ACTIVAS" | "RETIRADAS" | "TODAS";
/** Respuesta de /api/asignaciones-cursos (AsignacionCursoResponse). */
export type TeacherAssignment = {
  id: number;
  cursoId: number;
  curso: string;
  seccionId: number;
  seccion: string;
  anioAcademico: number;
  grado: number;
  docenteId: number;
  docente: string;
  activo: boolean;
  version: number;
  /** Bloques de horario activos; mientras haya alguno no se puede retirar (PLA-06). */
  horariosActivos: number;
};
/** Datos de alta o edición; la versión se agrega al actualizar. */
export type TeacherAssignmentInput = {
  cursoId: number;
  seccionId: number;
  docenteId: number;
};
/** Bloque que impide retirar al docente (detalle del 409 ASIGNACION_CON_HORARIOS_FUTUROS). */
export type BlockingSchedule = {
  horarioId: number;
  diaSemana: WeekDay;
  horaInicio: string;
  horaFin: string;
  aulaId: number;
  aula: string;
};
/** Opciones de los desplegables de la asignación (subconjunto de cada respuesta). */
export type SectionOption = { id: number; nombre: string; grado: number; anioAcademico: number };
export type CourseOption = { id: number; codigo: string; nombre: string };
export type TeacherOption = {
  id: number;
  codigoDocente: string;
  persona: { nombreCompleto: string };
};
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
/** Bloque de /api/horarios: mismo contrato que los horarios de un aula. */
export type ScheduleBlock = ClassroomSchedule;
/** Cuerpo de POST /api/horarios; horas "08:00". La versión se agrega al actualizar. */
export type ScheduleBlockInput = {
  asignacionCursoId: number;
  aulaId: number;
  diaSemana: WeekDay;
  horaInicio: string;
  horaFin: string;
};
/** Campos de /api/secciones (SeccionResponse) que usa el horario. */
export type ScheduleSection = {
  id: number;
  nombre: string;
  grado: number;
  anioAcademico: number;
  capacidadMaxima: number;
  activo: boolean;
};
/** Respuesta de /api/asignaciones-cursos (AsignacionCursoResponse): curso y docente de una sección. */
export type CourseAssignment = {
  id: number;
  cursoId: number;
  curso: string;
  seccionId: number;
  seccion: string;
  anioAcademico: number;
  docenteId: number;
  docente: string;
  activo: boolean;
  version: number;
};
