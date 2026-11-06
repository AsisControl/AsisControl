/** Tipos de /api/asistencias/docentes (AsistenciaDtos del backend). */
export type EstadoCodigoQR = "VIGENTE" | "UTILIZADO" | "EXPIRADO" | "INVALIDADO";

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
  docenteId: number;
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
};

export type DocenteOption = { id: number; label: string };
