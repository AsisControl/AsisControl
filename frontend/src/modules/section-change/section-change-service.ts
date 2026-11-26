import { api } from "@/core/api/client";
import type { AcademicYear } from "@/modules/academic-config";
import type { SectionEnrollment } from "./types";

export const getEnrollmentYear = (year: number) =>
  api<AcademicYear>(`/api/configuracion-academica/anios/${year}`);

export const getStudentEnrollments = (studentId: number) =>
  api<{ content: SectionEnrollment[] }>(`/api/matriculas?alumnoId=${studentId}&size=100`);

export function changeSection(enrollment: SectionEnrollment, seccionId: number, motivo: string) {
  return api<SectionEnrollment>(`/api/matriculas/${enrollment.id}/seccion`, {
    method: "PUT",
    body: JSON.stringify({ seccionId, motivo, version: enrollment.version }),
  });
}
