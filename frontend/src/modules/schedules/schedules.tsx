"use client";
import { useEffect, type ReactNode } from "react";
import { useRouter } from "next/navigation";
import { PageHeader, TabNav, type TabItem } from "@/shared/ui";
import { TeacherAssignmentsPanel } from "./assignments/teacher-assignments-panel";
import { ClassroomsPanel } from "./classrooms/classrooms-panel";
import { ScheduleHistoryPanel } from "./history/schedule-history-panel";
import { TimetablePanel } from "./timetable/timetable-panel";
/** Pestañas de Horario Académico en el orden del prototipo; la URL es /horarios/{id}. */
const baseTabs: TabItem[] = [
  { id: "horario", label: "Horario" },
  { id: "aulas", label: "Aulas" },
  { id: "asignaciones-docentes", label: "Asignaciones docentes" },
];
const HISTORY_TAB: TabItem = { id: "historial-de-cambios", label: "Historial de cambios" };
/** Pantalla de cada pestaña. */
const panels: Record<string, (canWrite: boolean) => ReactNode> = {
  horario: (canWrite) => <TimetablePanel canWrite={canWrite} />,
  aulas: (canWrite) => <ClassroomsPanel canWrite={canWrite} />,
  "asignaciones-docentes": (canWrite) => <TeacherAssignmentsPanel canWrite={canWrite} />,
  [HISTORY_TAB.id]: () => <ScheduleHistoryPanel />,
};
export const SCHEDULES_PATH = "/horarios";
const DEFAULT_TAB = "horario";
/** Horario Académico (menú Horarios). Cada pestaña vive en su propia carpeta. */
export function Schedules({
  tab = DEFAULT_TAB,
  canWrite,
  canViewHistory = false,
}: {
  tab?: string;
  /** Permiso ACADEMICO_ESCRIBIR: sin él la pantalla es de solo consulta. */
  canWrite: boolean;
  /** Permiso AUDITORIA_LEER: el historial muestra qué usuario hizo cada cambio. */
  canViewHistory?: boolean;
}) {
  const router = useRouter();
  const tabs = canViewHistory ? [...baseTabs, HISTORY_TAB] : baseTabs;
  const current = tabs.find((item) => item.id === tab);
  useEffect(() => {
    if (!current) router.replace(`${SCHEDULES_PATH}/${DEFAULT_TAB}`);
  }, [current, router]);
  if (!current) return null;
  return (
    <section>
      <PageHeader
        title="Horario Académico"
        description="Horarios, aulas y asignaciones docentes de la institución"
      />
      <TabNav
        label="Secciones de horario académico"
        tabs={tabs}
        current={current.id}
        onSelect={(id) => router.push(`${SCHEDULES_PATH}/${id}`)}
      />
      {panels[current.id](canWrite)}
    </section>
  );
}
