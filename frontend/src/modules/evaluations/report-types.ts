import type { AchievementLevel } from "./types";

export type ReportContext = {
  idAlumno: number;
  idAsignacionCurso: number;
  idPeriodoAcademico: number;
};

export type AvailableReport = ReportContext & {
  alumno: string;
  curso: string;
  anio: number;
  periodo: string;
};

export type ReportPeriod = { id: number; nombre: string };

/** ReporteAvanceDto: contenido vigente calculado al consultar, sin estado ni versión persistida. */
export type ProgressReport = {
  alumno: string;
  codigoAlumno: string | null;
  curso: string;
  docente: string;
  grado: number;
  seccion: string;
  anio: number;
  periodo: string;
  fechas: string;
  generadoEn: string;
  filas: {
    competencia: string;
    evaluacion: string;
    fecha: string;
    resultado: AchievementLevel | "Sin registrar";
    ponderacion: string;
    observacion: string;
  }[];
};
