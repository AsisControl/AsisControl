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
