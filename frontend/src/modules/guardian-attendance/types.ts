// asistencia alumnos
export type EstudianteFamilia = { id: number; codigoAlumno: string; nombreCompleto: string };

export type EstadoJustificacion =
  | "ENVIADA"
  | "EN_REVISION"
  | "OBSERVADA"
  | "APROBADA"
  | "RECHAZADA"
  | "CANCELADA";

/** Refleja AsistenciaDtos.AsistenciaAlumnoResponse. */
export type AsistenciaEstudiante = {
  id: number;
  asignacionCursoId: number;
  fechaRegistro: string;
  horaRegistro: string | null;
  estado: string;
  comentario: string | null;
  curso: string;
  justificacionId: number | null;
  estadoJustificacion: EstadoJustificacion | null;
  motivoJustificacion: string | null;
  observacionRevision: string | null;
  /** Hora de inicio de la clase; horaRegistro es cuándo se guardó la asistencia. */
  horaSesion: string | null;
  anioAcademico: number | null;
  periodo: string | null;
};

/** Bloque del horario del alumno (HorarioResponse); horas "07:30:00". */
export type BloqueHorario = {
  asignacionCursoId: number;
  curso: string;
  diaSemana: string;
  horaInicio: string;
  horaFin: string;
};
