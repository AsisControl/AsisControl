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
  grado?: number | null;
  seccion?: string | null;
  idSeccion?: number | null;
};

/** Cantidades y montos de un estado dentro del filtro consultado. */
export type StateSummary = {
  estado: FinancialStatus;
  cantidad: number;
  montoTotal: number;
  montoPagado: number;
  saldo: number;
};

export type CommitmentSummary = {
  fechaCalculo: string;
  cantidad: number;
  montoTotal: number;
  montoPagado: number;
  saldo: number;
  porEstado: StateSummary[];
  morosidad: {
    montoVencido: number;
    compromisosVencidos: number;
    estudiantesMorosos: number;
  };
};

export type TransactionStatus = "APROBADA" | "PENDIENTE" | "RECHAZADA" | "EXPIRADA" | "ANULADA";

export type PaymentTransaction = {
  id: number;
  idCompromisoPago: number;
  idApoderado: number | null;
  idAdministrador: number | null;
  numeroOperacion: string;
  monto: number;
  medioPago: string;
  estado: TransactionStatus;
  fechaTransaccion: string;
  observacion: string | null;
  fechaCreacion: string;
  idAlumno?: number | null;
  alumno?: string | null;
  codigoAlumno?: string | null;
  codigoCompromiso?: string | null;
  concepto?: string | null;
};

export type InternalReceipt = {
  id: number;
  idTransaccionPago: number;
  idArchivoAdjunto: number | null;
  tipoComprobante: string;
  serie: string;
  numero: string;
  fechaEmision: string;
  montoTotal: number;
};

/** Una transacción del compromiso en orden, con su efecto sobre el saldo. */
export type CommitmentMovement = {
  secuencia: number;
  transaccion: PaymentTransaction;
  aplicaAlSaldo: boolean;
  pagadoAcumulado: number;
  saldoResultante: number;
  comprobante: InternalReceipt | null;
};

export type CommitmentDetailWithMovements = {
  compromiso: PaymentCommitment;
  movimientos: CommitmentMovement[];
};

export type SectionChoice = {
  id: number;
  nombre: string;
  grado: number;
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
