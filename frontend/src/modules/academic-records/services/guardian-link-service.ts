import { api } from "@/core/api/client";

/** PersonaDtos.VinculoApoderadoRequest. */
export type GuardianLinkValues = {
  apoderadoId: number;
  parentesco: string;
  principal: boolean;
};

export function linkGuardian(studentId: number, values: GuardianLinkValues) {
  return api(`/api/alumnos/${studentId}/apoderados`, {
    method: "POST",
    body: JSON.stringify(values),
  });
}

/** Retira el vínculo: el apoderado deja de acceder a ese estudiante. */
export function unlinkGuardian(studentId: number, guardianId: number) {
  return api(`/api/alumnos/${studentId}/apoderados/${guardianId}`, {
    method: "DELETE",
  });
}
