/** Valor legible de un campo antes y después del cambio; null si no existía. */
export type HistoryField = {
  campo: string;
  anterior: string | null;
  nuevo: string | null;
};
export type History = {
  id: number;
  actor: string;
  /** null para accesos que ya no están activos. */
  actorNombre: string | null;
  rol: string | null;
  fecha: string;
  accion: string;
  motivo: string | null;
  campos: HistoryField[];
};

export type EnrollmentPaymentPlan = {
  idSolicitud: number | null;
  idMatricula: number | null;
  idAlumno: number;
  alumno: string;
  anioAcademico: number;
  grado: number;
  idTarifa: number | null;
  tarifaVersion: number | null;
  firmaPlan: string | null;
  montoMensual: number;
  puedeGenerar: boolean;
  mensaje: string | null;
  items: Array<{
    mes: number;
    numeroCuota: number;
    concepto: string;
    fechaVencimiento: string;
    montoBase: number;
    montoDescuento: number;
    montoTotal: number;
    beneficioNombre: string | null;
    idCompromiso: number | null;
    estado: string;
  }>;
};
