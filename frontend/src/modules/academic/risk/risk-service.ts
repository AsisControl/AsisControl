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
const signs = { MENOR_QUE: "<", MENOR_IGUAL: "≤", MAYOR_QUE: ">", MAYOR_IGUAL: "≥", IGUAL: "=" };
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
export type RiskEvaluation = { resultado: keyof typeof outcomes; reglas: Rule[] };
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
