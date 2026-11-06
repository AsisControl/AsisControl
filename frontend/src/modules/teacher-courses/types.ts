export type PeriodoCurso = "VIGENTES" | "PASADOS" | "TODOS";

/** Refleja SesionClaseDtos.AsignacionAsistenciaResponse. */
export type CursoAsignado = {
  id: number;
  curso: string;
  seccion: string;
  anioAcademico: number;
  vigente: boolean;
  estudiantes: number;
  horario: string | null;
  aula: string | null;
  periodo: string | null;
};
