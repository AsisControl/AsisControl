"use client";
import { useEffect, type ReactNode } from "react";
import { useRouter } from "next/navigation";
import { PageHeader, TabNav, type TabItem } from "@/shared/ui";
import { ClassroomsPanel } from "./classrooms/classrooms-panel";
/** Pestañas de Horario Académico en el orden del prototipo; la URL es /horarios/{id}. */
const tabs: TabItem[] = [
  { id: "horario", label: "Horario" },
  { id: "aulas", label: "Aulas" },
  { id: "asignaciones-docentes", label: "Asignaciones docentes" },
  { id: "historial-de-cambios", label: "Historial de cambios" },
];
/** Pestañas con pantalla; las demás muestran el aviso de pendiente. */
const panels: Record<string, (canWrite: boolean) => ReactNode> = {
  aulas: (canWrite) => <ClassroomsPanel canWrite={canWrite} />,
};
export const SCHEDULES_PATH = "/horarios";
const DEFAULT_TAB = "aulas";
/** Horario Académico (menú Horarios). Cada pestaña vive en su propia carpeta. */
export function Schedules({
  tab = DEFAULT_TAB,
  canWrite,
}: {
  tab?: string;
  /** Permiso ACADEMICO_ESCRIBIR: sin él la pantalla es de solo consulta. */
  canWrite: boolean;
}) {
  const router = useRouter();
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
      {panels[current.id] ? (
        panels[current.id](canWrite)
      ) : (
        <article className="role-message">
          <span className="message-mark">!</span>
          <div>
            <p className="eyebrow">{current.label.toUpperCase()}</p>
            <h2>Esta sección todavía está pendiente de implementación.</h2>
          </div>
        </article>
      )}
    </section>
  );
}
