import type { PaymentCommitment } from "@/modules/finance";

export type ChargeConcept = {
  id: number; codigo: string; nombre: string; descripcion: string | null;
  periodicidad: "MENSUAL" | "UNICO"; esPension: boolean; montoSugerido: number | null;
  requiereAceptacion: boolean; activo: boolean; version: number;
};
export type PensionRate = {
  id: number; idConcepto: number; concepto?: string; anioAcademico: number; grado: number;
  montoMensual: number; activo: boolean; version: number;
};
export type StudentOption = { id: number; codigoAlumno: string; nombreCompleto: string };
export type StudentBenefit = {
  id: number; idAlumno: number; alumno: string; anioAcademico: number; idConcepto: number; concepto: string;
  nombre: string; tipo: "PORCENTAJE" | "IMPORTE_FIJO"; valor: number; mesInicio: number; mesFin: number;
  motivo: string; activo: boolean; version: number;
};
export type BenefitPreview = {
  idBeneficio: number; mensaje: string; ajustables: PaymentCommitment[]; omitidos: PaymentCommitment[]; firmaAjuste: string;
};
export type PensionPlan = {
  idMatricula?: number; idSolicitud?: number; idAlumno: number; alumno: string; anioAcademico: number; grado: number;
  idTarifa?: number; montoMensual?: number; puedeGenerar: boolean; mensaje: string;
  items: Array<{ mes: number; numeroCuota: number; concepto: string; fechaVencimiento: string;
    montoBase: number; montoDescuento: number; montoTotal: number; beneficioNombre?: string;
    idCompromiso?: number; estado: string }>;
  legados: PaymentCommitment[]; firmaPlan?: string; tarifaVersion?: number | null;
};
export type OptionalChargeOffer = {
  id: number; codigo: string; idMatricula: number; idAlumno: number; alumno: string; anioAcademico: number;
  idConcepto: number; concepto: string; referencia: string; titulo: string; descripcion: string | null;
  montoBase: number; montoDescuento: number; monto: number; beneficioNombre: string | null;
  fechaVencimiento: string; fechaLimiteAceptacion: string; estado: "PROPUESTA" | "ACEPTADA" | "RECHAZADA" | "CANCELADA" | "VENCIDA";
  idCompromiso: number | null; codigoCompromiso: string | null; fechaCreacion: string; respondidaEn: string | null; version: number;
};
