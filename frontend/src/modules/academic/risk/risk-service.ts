import { api } from "@/core/api/client";

export const ALERTS_PATH = "/api/alertas-riesgo";
export const states = { ACTIVA: "Activa", EN_SEGUIMIENTO: "En seguimiento", RESUELTA: "Resuelta" };
export const levels = { BAJO: "Bajo", MEDIO: "Medio", ALTO: "Alto" };
export const outcomes = {
  EN_RIESGO: "En riesgo",
  SIN_RIESGO: "Sin riesgo",
  DATOS_INSUFICIENTES: "Datos insuficientes",
};
export type RiskState = keyof typeof states;
type Operator = keyof typeof signs;
export const signs = { MENOR_QUE: "<", MENOR_IGUAL: "≤", MAYOR_QUE: ">", MAYOR_IGUAL: "≥", IGUAL: "=" };
export const comparison = (operator: Operator | null, threshold: number | null) =>
  operator && threshold !== null ? `${signs[operator]} ${threshold}` : "—";

export type Alert = {
  id: number;
  nombreAlumno: string;
  nombreCriterio: string;
  indicador: string;
  periodo: string | null;
  nivelRiesgo: keyof typeof levels;
  estado: RiskState;
  mensaje: string;
  observacion: string | null;
  valorDetectado: number;
  umbral: number | null;
  operador: Operator | null;
};
type Rule = {
  idCriterio: number;
  nombreCriterio: string;
  resultado: keyof typeof outcomes;
  motivo: string;
  evidencias: string[];
};
type EvaluatedRule = Rule & {
  indicador: keyof typeof indicators;
  umbral: number;
  operador: Operator;
  valor: number | null;
};
export type RiskEvaluation = { resultado: keyof typeof outcomes; reglas: EvaluatedRule[] };
type HistoryItem = { id: number; fecha: string; evaluacion: Rule };

export async function readAlert(id: number) {
  return Promise.all([
    api<Alert>(`${ALERTS_PATH}/${id}`),
    api<HistoryItem[]>(`${ALERTS_PATH}/${id}/historial`),
  ]);
}
export function changeAlertState(id: number, estado: RiskState, observacion: string) {
  return api<Alert>(`${ALERTS_PATH}/${id}/estado`, {
    method: "PUT",
    body: JSON.stringify({ estado, observacion: observacion.trim() || null }),
  });
}
export function evaluateRisk(idAlumno: number, idPeriodo: number) {
  return api<RiskEvaluation>("/api/evaluaciones-riesgo", {
    method: "POST",
    body: JSON.stringify({ idAlumno, idPeriodo }),
  });
}

export const CRITERIA_PATH = "/api/criterios-riesgo";
export const indicators = {
  PROMEDIO_ACADEMICO: "Promedio académico",
  COMPETENCIAS_EN_INICIO: "Competencias en Inicio",
  INASISTENCIAS: "Inasistencias",
  TARDANZAS: "Tardanzas",
  TAREAS_NO_ENTREGADAS: "Tareas no entregadas",
};
type CriterionInput = {
  nombre: string;
  descripcion: string | null;
  indicador: string;
  operador: string;
  umbral: number;
  nivelRiesgo: string;
  minimoDatos: number;
};
export type Criterion = CriterionInput & {
  id: number;
  indicador: keyof typeof indicators;
  operador: Operator;
  nivelRiesgo: keyof typeof levels;
};
export function saveCriterion(criterion: CriterionInput, id?: number) {
  return api<Criterion>(id ? `${CRITERIA_PATH}/${id}` : CRITERIA_PATH, {
    method: id ? "PUT" : "POST",
    body: JSON.stringify(criterion),
  });
}
export function disableCriterion(id: number) {
  return api<void>(`${CRITERIA_PATH}/${id}`, { method: "DELETE" });
}
