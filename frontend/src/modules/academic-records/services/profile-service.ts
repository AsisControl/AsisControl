import { api } from "@/core/api/client";
import type { AccessCredential } from "@/modules/students";
import type { personData } from "@/shared/forms/person-fields";

export type ProfileRole = "docente" | "apoderado";
type ProfileInput = {
  persona: ReturnType<typeof personData>;
  fechaIngreso?: string;
  especialidad?: string;
};

export function registrarPerfil(role: ProfileRole, input: ProfileInput) {
  const path = role === "docente" ? "/api/docentes" : "/api/apoderados";
  return api<{ credencialAcceso?: AccessCredential }>(path, {
    method: "POST",
    body: JSON.stringify(input),
  });
}
