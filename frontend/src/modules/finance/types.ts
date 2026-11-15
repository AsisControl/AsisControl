export type AdministrativeStatus = "ACTIVO" | "ANULADO";
export type FinancialStatus = "PENDIENTE" | "PARCIAL" | "PAGADO" | "VENCIDO" | "ANULADO" | "EXONERADO";

export type PaymentCommitment = {
  id: number;
  idMatricula: number;
  idAlumno: number;
  alumno: string;
  codigoAlumno: string;
  codigoMatricula: string;
  anioAcademico: number;
  codigoCompromiso: string;
  concepto: string;
  numeroCuota: number | null;
  descripcion: string | null;
  montoTotal: number;
  montoPagado: number;
  saldo: number;
  moneda: string;
  fechaVencimiento: string;
  estado: FinancialStatus;
  estadoFinanciero: FinancialStatus;
  estadoAdministrativo: AdministrativeStatus;
  fechaCreacion: string;
  fechaActualizacion: string;
  motivoAnulacion: string | null;
  fechaAnulacion: string | null;
  version: number;
  idConcepto?: number | null;
  mes?: number | null;
  referenciaCobro?: string | null;
  montoBase?: number | null;
  montoDescuento?: number | null;
  beneficioNombre?: string | null;
  idBeneficio?: number | null;
  idTarifa?: number | null;
  tarifaVersion?: number | null;
};

export type EligibleEnrollment = {
  id: number;
  codigoMatricula: string;
  alumno: string;
  codigoAlumno: string;
  alumnoId: number;
  anioAcademico: number;
  grado: number;
  seccion: string;
  estado: "ACTIVA";
};

export type CommitmentInput = {
  idMatricula: number;
  concepto: string;
  numeroCuota: number | null;
  montoTotal: number;
  moneda: "PEN";
  fechaVencimiento: string;
  descripcion: string | null;
  idConcepto?: number;
  referenciaCobro?: string;
};

export type CommitmentChange = {
  montoTotal: number;
  fechaVencimiento: string;
  motivo: string;
  version: number;
};
