/** Respuesta de /api/cursos (CursoResponse). */
export type Course = {
  id: number;
  codigo: string;
  nombre: string;
  descripcion: string | null;
  horasSemanales: number;
  activo: boolean;
  version: number;
};
/** Datos editables de un curso; la versión se agrega al actualizar. */
export type CourseInput = {
  codigo: string;
  nombre: string;
  descripcion: string | null;
  horasSemanales: number;
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
