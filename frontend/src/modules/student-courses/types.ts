// inscripcion cursos
export type PeriodoCurso = "VIGENTES" | "PASADOS" | "TODOS";

/**
 * Refleja MisCursosDtos.CursoInscrito: docente, horario y aula son null mientras no se asignen.
 * vigente es true para el año activo y false para un año cerrado.
 */
export type CursoInscrito = {
  cursoId: number;
  curso: string;
  docente: string | null;
  horario: string | null;
  aula: string | null;
  anioAcademico: number;
  seccion: string;
  vigente: boolean;
};

export type MisCursos = {
  cursos: CursoInscrito[];
};
