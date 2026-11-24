import { api } from "@/core/api/client";
import { normalizePage } from "@/shared/lib/pagination";
import type { Page } from "@/shared/types/api";
import type { StudentTask } from "@/modules/student-tasks";
import type { ScheduleBlock } from "@/modules/schedules";
import type { AsistenciaEstudiante, EstudianteFamilia } from "@/modules/guardian-attendance";
import type { AvailableReport } from "@/modules/evaluations";
import type { PaymentCommitment } from "@/modules/finance";
import type { GuardianRequest } from "@/modules/enrollment";

export type HomeStudent = EstudianteFamilia;
export type StudentHomeData = {
  tasks: Page<StudentTask>;
  schedule: ScheduleBlock[];
  attendance: AsistenciaEstudiante[];
  reports: AvailableReport[];
};
export type GuardianHomeData = {
  obligations: PaymentCommitment[];
  requests: GuardianRequest[];
  attendance: AsistenciaEstudiante[];
  reports: AvailableReport[];
};

async function page<T>(path: string) {
  return normalizePage(await api<Page<T>>(path));
}

async function allPages<T>(path: string) {
  const items: T[] = [];
  for (let index = 0; ; index++) {
    const separator = path.includes("?") ? "&" : "?";
    const result = await page<T>(`${path}${separator}page=${index}&size=100`);
    items.push(...result.content);
    if (result.last || !result.content.length) return items;
  }
}

export function loadStudentHome(): Promise<StudentHomeData> {
  const year = new Date().getFullYear();
  return Promise.all([
    page<StudentTask>("/api/alumnos/me/tareas?page=0&size=100&entregada=false"),
    api<ScheduleBlock[]>(`/api/horarios/mio?anioAcademico=${year}`),
    allPages<AsistenciaEstudiante>("/api/asistencias/alumnos"),
    api<AvailableReport[]>("/api/calificaciones/informes-disponibles"),
  ]).then(([tasks, schedule, attendance, reports]) => ({ tasks, schedule, attendance, reports }));
}

export function loadHomeStudents() {
  return api<HomeStudent[]>("/api/familia/estudiantes");
}

export function loadGuardianHome(studentId: number): Promise<GuardianHomeData> {
  return Promise.all([
    allPages<PaymentCommitment>(`/api/apoderado/obligaciones?idAlumno=${studentId}`),
    allPages<GuardianRequest>(`/api/solicitudes-matricula?alumnoId=${studentId}`),
    allPages<AsistenciaEstudiante>(`/api/asistencias/alumnos?alumnoId=${studentId}`),
    api<AvailableReport[]>("/api/calificaciones/informes-disponibles"),
  ]).then(([obligations, requests, attendance, reports]) => ({
    obligations,
    requests,
    attendance,
    reports: reports.filter((item) => item.idAlumno === studentId),
  }));
}
