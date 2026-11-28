import { api } from "@/core/api/client";
import type { AcademicYear } from "@/modules/academic-config";
import type { Page } from "@/shared/types/api";
import type { EnrollmentPaymentPlan } from "./types";

export const getAcademicYears = () =>
  api<Page<AcademicYear>>("/api/configuracion-academica/anios?size=50");

export const getStudentPlan = (studentId: number, year: number, grade: number) =>
  api<EnrollmentPaymentPlan>(
    `/api/configuracion-pagos/planes/alumnos/${studentId}/preview?anioAcademico=${year}&grado=${grade}`,
  );

export function enrollStudent(alumnoId: number, seccionId: number, firmaPlan: string) {
  return api<{ codigoMatricula: string }>("/api/matriculas/directas", {
    method: "POST",
    body: JSON.stringify({ alumnoId, seccionId, firmaPlan }),
  });
}
