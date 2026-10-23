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
