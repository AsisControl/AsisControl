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
  fechaRegistro: string;
  horaRegistro: string | null;
  estado: string;
  comentario: string | null;
  curso: string;
  justificacionId: number | null;
  estadoJustificacion: EstadoJustificacion | null;
  motivoJustificacion: string | null;
  observacionRevision: string | null;
};
