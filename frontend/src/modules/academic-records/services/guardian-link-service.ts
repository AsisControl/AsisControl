import { api } from "@/core/api/client";

export const STUDENT_RECORDS_PATH = "/api/alumnos";
export const GUARDIAN_RECORDS_PATH = "/api/apoderados";

export function relationshipsPath(studentId: number) {
  return `${STUDENT_RECORDS_PATH}/${studentId}/apoderados`;
}

/** PersonaDtos.VinculoApoderadoRequest. */
export type GuardianLinkValues = {
  apoderadoId: number;
  parentesco: string;
  principal: boolean;
};

export function linkGuardian(studentId: number, values: GuardianLinkValues) {
  return api(relationshipsPath(studentId), {
    method: "POST",
    body: JSON.stringify(values),
  });
}

/** Retira el vínculo: el apoderado deja de acceder a ese estudiante. */
export function unlinkGuardian(studentId: number, guardianId: number, motivo: string) {
  const query = new URLSearchParams({ motivo });
  return api(`${relationshipsPath(studentId)}/${guardianId}?${query}`, {
    method: "DELETE",
  });
}
