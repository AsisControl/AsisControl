/** Tipos de /api/asistencias/docentes (AsistenciaDtos del backend). */
export type EstadoCodigoQR = "VIGENTE" | "UTILIZADO" | "EXPIRADO" | "INVALIDADO";

/** Cruce con la lista de alumnos: OBSERVADA hasta que el docente finaliza la asistencia de su sesión. */
export type VerificacionClase = "CONFIRMADA" | "OBSERVADA";

export type EstadoAsistencia =
  | "PRESENTE"
  | "TARDANZA"
  | "SIN_MARCACION"
  | "INASISTENCIA"
  | "INASISTENCIA_JUSTIFICADA"
  | "TARDANZA_JUSTIFICADA";

/** Fechas como LocalDateTime del servidor, sin zona: "2026-11-06T07:52:13". */
export type CodigoQR = {
  id: number;
  token: string;
  fechaHoraEmision: string;
  fechaHoraExpiracion: string;
  estado: EstadoCodigoQR;
};

export type AsistenciaDocente = {
  id: number;
  docenteId: number;
  codigoQRId: number | null;
  fechaJornada: string;
  fechaHoraMarcada: string | null;
  estado: EstadoAsistencia;
  contingencia: boolean;
  motivoContingencia: string | null;
  version: number;
  sesionClaseId: number | null;
  verificacion: VerificacionClase;
  docenteNombre: string;
};

export type ResumenMarcacion = {
  fecha: string;
  totalDocentes: number;
  marcaron: number;
  pendientesConfirmacion: number;
  sinMarcar: number;
};

export type EstadoSesionClase = "PENDIENTE" | "BORRADOR" | "TOMADA";

/** Clase del docente en una fecha y si ya tiene la lista de alumnos registrada. */
export type ClaseDia = {
  sesionId: number;
  curso: string;
  seccion: string;
  horaInicio: string;
  horaFin: string;
  estado: EstadoSesionClase;
};

export type DocenteOption = { id: number; label: string };
