// inscripcion cursos
/** Refleja MisCursosDtos.CursoInscrito: docente, horario y aula son null mientras no se asignen. */
export type CursoInscrito = {
  cursoId: number;
  curso: string;
  docente: string | null;
  horario: string | null;
  aula: string | null;
};

/** anioAcademico y seccion son null cuando el estudiante no tiene una matrícula activa. */
export type MisCursos = {
  anioAcademico: number | null;
  seccion: string | null;
  cursos: CursoInscrito[];
};
