import { api } from "@/core/api/client";

export const STUDENT_RECORDS_PATH = "/api/alumnos";
export const GUARDIAN_RECORDS_PATH = "/api/apoderados";

export type Kinship = "PADRE" | "MADRE" | "TUTOR_LEGAL" | "OTRO_FAMILIAR" | "NO_DECLARADO";
type RelationshipFields = { parentesco: Kinship; principal: boolean };
export type Relationship = RelationshipFields & {
  id: number;
  apoderadoId: number;
  nombreCompleto: string;
  activo: boolean;
  apoderadoActivo: boolean;
};
export type RelationshipEvent = {
  id: number;
  actor: string;
  actorNombre: string | null;
  fecha: string;
  accion: string;
  motivo: string | null;
};

function relationshipsPath(studentId: number) {
  return `${STUDENT_RECORDS_PATH}/${studentId}/apoderados`;
}

export function listRelationships(studentId: number) {
  return api<Relationship[]>(relationshipsPath(studentId));
}

export function createRelationship(studentId: number, input: RelationshipFields & { apoderadoId: number }) {
  return api<RelationshipFields>(relationshipsPath(studentId), {
    method: "POST",
    body: JSON.stringify(input),
  });
}

export function editRelationship(studentId: number, guardianId: number, input: RelationshipFields & { motivo: string }) {
  return api<Relationship>(`${relationshipsPath(studentId)}/${guardianId}`, {
    method: "PUT",
    body: JSON.stringify(input),
  });
}

export function changeRelationshipState(studentId: number, guardianId: number, activo: boolean, motivo: string) {
  return api<Relationship>(`${relationshipsPath(studentId)}/${guardianId}/estado`, {
    method: "PUT",
    body: JSON.stringify({ activo, motivo }),
  });
}

export function relationshipHistory(studentId: number, guardianId: number) {
  return api<RelationshipEvent[]>(`${relationshipsPath(studentId)}/${guardianId}/historial`);
}
