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
