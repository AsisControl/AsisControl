/** Contratos de EvaluacionDto y CalificacionDto. */
export type EvaluationState = "BORRADOR" | "PUBLICADA" | "CERRADA" | "ANULADA";
export type AchievementLevel = "AD" | "A" | "B" | "C";
export type EvaluationPeriod =
  | "DIARIO"
  | "SEMANAL"
  | "MENSUAL"
  | "BIMESTRAL"
  | "TRIMESTRAL"
  | "PERIODO_ACADEMICO"
  | "ANUAL";
export type EvaluationCompetency = { id: number; codigo: string; nombre: string };
export type EvaluationGradingRule = {
  componentes: { tipoEvaluacion: string; nombre: string; peso: number }[];
};
export type Evaluation = {
  id: number;
  idAsignacionCurso: number;
  titulo: string;
  descripcion: string | null;
  tipoEvaluacion: string;
  periodoEvaluacion: EvaluationPeriod;
  estado: EvaluationState;
  fechaEvaluacion: string;
  ponderacionPorcentaje: number;
  reglaVigente?: boolean;
  componente?: string | null;
  pesoComponente?: number | null;
  competencias: EvaluationCompetency[];
  idCurso: number;
  curso: string;
  idSeccion: number;
  seccion: string;
  anioAcademico: number;
};
export type EvaluationInput = {
  idAsignacionCurso: number;
  titulo: string;
  descripcion: string | null;
  tipoEvaluacion: string;
  periodoEvaluacion: EvaluationPeriod;
  fechaEvaluacion: string;
  idsCompetencia: number[];
};
export type GradeCell = {
  pesoEfectivo?: number | null;
  idCalificacion: number;
  idCompetencia: number;
  nivelLogro: AchievementLevel;
  version: number;
  corregida: boolean;
};
export type GradeSheetStudent = {
  idAlumno: number;
  codigoAlumno: string | null;
  nombreCompleto: string;
  notas: GradeCell[];
};
export type GradeSheetData = { evaluacion: Evaluation; alumnos: GradeSheetStudent[] };
export type CorrectionEntry = {
  numeroVersion: number;
  nivelAnterior: AchievementLevel;
  nivelNuevo: AchievementLevel;
  motivo: string;
  autor: string;
  fecha: string;
};
export type ConsolidatedLevel = {
  idCompetencia: number;
  nombreCompetencia: string;
  nivelConsolidado: AchievementLevel | null;
  evaluacionesConsideradas: number;
  componentesFaltantes?: string[];
  numeroVersionRegla?: number;
};
/** Asignación propia del docente (AsignacionCursoResponse). */
export type OwnAssignment = {
  id: number;
  cursoId: number;
  curso: string;
  seccionId: number;
  seccion: string;
  grado: number;
  anioAcademico: number;
};
/** Competencia del plan correspondiente a la sección de una asignación docente. */
export type CourseCompetency = {
  competenciaId: number;
  competenciaCodigo: string;
  competenciaNombre: string;
};
