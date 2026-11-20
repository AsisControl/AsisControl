export type ReportRowContext = {
  idInforme: number;
  idDetalleInforme: number;
};

export type ProgressReportRow = ReportRowContext & {
  competencia: string;
  evaluacion: string;
  fecha: string;
  resultado: string;
  ponderacion: string;
  observacion: string;
};

export type ReportEvidence = {
  idEvidencia: number;
  descripcion: string | null;
  archivo: { nombreOriginal: string } | null;
};
