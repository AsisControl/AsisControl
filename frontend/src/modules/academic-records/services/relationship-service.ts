import { api } from "@/core/api/client";
import { relationshipsPath } from "./guardian-link-service";

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

export function listRelationships(studentId: number) {
  return api<Relationship[]>(relationshipsPath(studentId));
}

export function editRelationship(studentId: number, guardianId: number, input: RelationshipFields & { motivo: string }) {
  return api<Relationship>(`${relationshipsPath(studentId)}/${guardianId}`, {
    method: "PUT",
    body: JSON.stringify(input),
  });
}

export function reactivateRelationship(studentId: number, guardianId: number, motivo: string) {
  return api<Relationship>(`${relationshipsPath(studentId)}/${guardianId}/estado`, {
    method: "PUT",
    body: JSON.stringify({ activo: true, motivo }),
  });
}

export function relationshipHistory(studentId: number, guardianId: number) {
  return api<RelationshipEvent[]>(`${relationshipsPath(studentId)}/${guardianId}/historial`);
}
