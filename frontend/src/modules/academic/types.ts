/** Respuesta de /api/cursos (CursoResponse). */
export type Course = {
  id: number;
  codigo: string;
  nombre: string;
  descripcion: string | null;
  horasSemanales: number;
  activo: boolean;
  version: number;
  /** Contexto académico; null en cursos registrados antes de exigirlo. */
  gradoAcademicoId: number | null;
  anioAcademico: number | null;
  grado: number | null;
};
/** Datos editables de un curso; la versión se agrega al actualizar. */
export type CourseInput = {
  codigo: string;
  nombre: string;
  descripcion: string | null;
  horasSemanales: number;
  gradoAcademicoId: number | null;
};
/** Respuesta de /api/cursos-competencias (CursoCompetenciaResponse). */
export type CourseCompetency = {
  id: number;
  cursoId: number;
  cursoCodigo: string;
  cursoNombre: string;
  competenciaId: number;
  competenciaCodigo: string;
  competenciaNombre: string;
  orden: number;
  activo: boolean;
  version: number;
};
/** Respuesta de /api/competencias (CompetenciaResponse). */
export type Competency = {
  id: number;
  codigo: string;
  nombre: string;
  descripcion: string | null;
  activo: boolean;
  version: number;
};
/** Datos editables de una competencia; la versión se agrega al actualizar. */
export type CompetencyInput = {
  codigo: string;
  nombre: string;
  descripcion: string | null;
};
/** Curso, competencia y posición de la competencia dentro del curso. */
export type AssociationInput = {
  cursoId: number;
  competenciaId: number;
  orden: number;
};

export type PlanCompetency = { id: number; codigo: string; nombre: string; activa: boolean };

export type PlanCourse = Pick<Course, "id" | "codigo" | "nombre" | "horasSemanales"> & {
  competencias?: PlanCompetency[];
};

/** OBS-01: sección y curso cuyo horario supera las horas del plan. */
export type ExceededSection = {
  seccionId: number;
  seccion: string;
  cursoId: number;
  curso: string;
  horasPlan: number;
  minutosProgramados: number;
};

export type StudyPlan = {
  anio: number;
  grado: number;
  estadoAnio: "BORRADOR" | "ACTIVO" | "CERRADO";
  definido: boolean;
  editable: boolean;
  version: number;
  cursos: PlanCourse[];
  seccionesExcedidas?: ExceededSection[];
};

export type StudyPlanName = { nombre: string; version: number };
export type PreviousStudyPlan = { anioOrigen: number; cursos: PlanCourse[] };

export type StudyPlanInput = {
  version: number;
  cursos: (Pick<PlanCourse, "id" | "nombre" | "horasSemanales"> & { competenciasIds?: number[] })[];
  motivo?: string;
};
