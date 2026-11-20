export type CursoSesiones = { id: number; curso: string; seccion: string };

export type EstadoSesion = "PENDIENTE" | "BORRADOR" | "TOMADA";
export type EstadoMarca = "PRESENTE" | "TARDANZA" | "INASISTENCIA";
/** SIN_MARCACION: el plazo venció con el borrador y el docente no marcó a ese estudiante. */
export type EstadoRegistrado = EstadoMarca | "SIN_MARCACION";
export type EstadoJustificacion =
  | "ENVIADA"
  | "EN_REVISION"
  | "OBSERVADA"
  | "APROBADA"
  | "RECHAZADA"
  | "CANCELADA";

/** Refleja SesionClaseDtos.SesionClaseResponse. */
export type SesionClase = {
  id: number;
  asignacionCursoId: number;
  fecha: string;
  horaInicio: string;
  horaFin: string;
  estado: EstadoSesion;
  puedeTomar: boolean;
  puedeVer: boolean;
  puedeEditar: boolean;
  version: number;
  /** Fin del plazo para tomar o corregir: siguiente clase del curso ese día o fin del día. */
  limiteEdicion?: string;
};

export type AlumnoSesion = {
  alumnoId: number;
  codigoAlumno: string;
  nombreCompleto: string;
  estado: EstadoRegistrado | null;
  asistenciaId: number | null;
  justificacionId: number | null;
  estadoJustificacion: EstadoJustificacion | null;
};

/** Refleja AsistenciaSesionDtos.SesionAsistenciaResponse. */
export type SesionAsistencia = {
  id: number;
  asignacionCursoId: number;
  curso: string;
  seccion: string;
  fecha: string;
  horaInicio: string;
  horaFin: string;
  estado: EstadoSesion;
  puedeTomar: boolean;
  puedeVer: boolean;
  puedeEditar: boolean;
  pendientes: number;
  alumnos: AlumnoSesion[];
  registradoPor: string | null;
  registradoEn: string | null;
  version: number;
  limiteEdicion?: string;
};

export type Marca = { alumnoId: number; estado: EstadoMarca };

export type CorreccionAsistencia = {
  id: number;
  fechaHora: string;
  docenteId: number;
  docente: string;
  motivo: string;
  actorIdentificador?: string | null;
  actorNombre?: string | null;
  actorRol?: string | null;
  cambios: {
    alumnoId: number;
    nombreCompleto: string;
    estadoAnterior: string;
    estadoNuevo: string;
  }[];
};

export type ArchivoJustificacion = {
  id: number;
  nombreOriginal: string;
};

/** Refleja AsistenciaDtos.JustificacionAsistenciaResponse. */
export type Justificacion = {
  id: number;
  motivo: string;
  fechaHoraEnvio: string;
  fechaHoraRevision: string | null;
  observacionRevision: string | null;
  estado: EstadoJustificacion;
  archivos: ArchivoJustificacion[];
  version: number;
  estudiante: string;
  curso: string;
  seccion: string;
  fechaSesion: string;
  horaSesion: string | null;
};
