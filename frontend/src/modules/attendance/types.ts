export type CursoSesiones = { id: number; curso: string; seccion: string };

export type EstadoSesion = "PENDIENTE" | "BORRADOR" | "TOMADA";
export type EstadoMarca = "PRESENTE" | "TARDANZA" | "INASISTENCIA";
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
};

export type AlumnoSesion = {
  alumnoId: number;
  codigoAlumno: string;
  nombreCompleto: string;
  estado: EstadoMarca | null;
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
};

export type Marca = { alumnoId: number; estado: EstadoMarca };

export type CorreccionAsistencia = {
  id: number;
  fechaHora: string;
  docenteId: number;
  docente: string;
  motivo: string;
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
