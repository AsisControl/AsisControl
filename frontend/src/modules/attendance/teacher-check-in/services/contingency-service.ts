import { api } from "@/core/api/client";

export const CONTINGENCIES_PATH = "/api/asistencias/docentes/contingencias";
export const contingencyStates = { PENDIENTE: "Pendiente", APROBADA: "Aprobada", RECHAZADA: "Rechazada" };
export const arrivalStates = { PRESENTE: "Presente", TARDANZA: "Tardanza" };
export type ContingencyStatus = keyof typeof contingencyStates;
export type ArrivalStatus = keyof typeof arrivalStates;
export type Contingency = {
  id: number;
  docente: string;
  fechaJornada: string;
  horaReportada: string;
  motivo: string;
  estado: ContingencyStatus;
  fechaEnvio: string;
  fechaResolucion: string | null;
  motivoRechazo: string | null;
  estadoSugerido: ArrivalStatus | null;
  estadoRegistrado: ArrivalStatus | null;
};

export function reportContingency(data: { fechaJornada: string; horaReportada: string; motivo: string }) {
  return api<Contingency>(CONTINGENCIES_PATH, { method: "POST", body: JSON.stringify(data) });
}

type Resolution =
  | { decision: "APROBAR"; estadoSinVentana: string | null }
  | { decision: "RECHAZAR"; motivo: string };

export function resolveContingency(id: number, decision: Resolution) {
  return api<Contingency>(`${CONTINGENCIES_PATH}/${id}/resoluciones`, {
    method: "POST", body: JSON.stringify(decision),
  });
}
