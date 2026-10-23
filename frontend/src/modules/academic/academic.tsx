"use client";
import { useEffect, type ReactNode } from "react";
import { useRouter } from "next/navigation";
import { TabNav, type TabItem } from "@/shared/ui";
import { AssociationsPanel } from "./associations/associations-panel";
import { CompetenciesPanel } from "./competencies/competencies-panel";
import { CoursesPanel } from "./courses/courses-panel";
import styles from "./academic.module.css";
/** Pestañas de Gestión Curricular en el orden del prototipo; la URL es /academico/{id}. */
const tabs: TabItem[] = [
  { id: "cursos", label: "Cursos" },
  { id: "competencias", label: "Competencias" },
  { id: "asociaciones", label: "Asociaciones" },
  { id: "reglas-academicas", label: "Reglas académicas" },
  { id: "estudiantes-en-riesgo", label: "Estudiantes en riesgo" },
  { id: "evaluaciones", label: "Evaluaciones" },
  { id: "reportes", label: "Reportes" },
];
/** Pestañas con pantalla; las demás muestran el aviso de pendiente. */
const panels: Record<string, (canWrite: boolean) => ReactNode> = {
  cursos: (canWrite) => <CoursesPanel canWrite={canWrite} />,
  competencias: (canWrite) => <CompetenciesPanel canWrite={canWrite} />,
  asociaciones: (canWrite) => <AssociationsPanel canWrite={canWrite} />,
};
export const ACADEMIC_PATH = "/academico";
const DEFAULT_TAB = "cursos";
/** Gestión Curricular (menú Académico). Cada pestaña vive en su propia carpeta. */
export function Academic({
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
    if (!current) router.replace(`${ACADEMIC_PATH}/${DEFAULT_TAB}`);
  }, [current, router]);
  if (!current) return null;
  return (
    <section>
      <div className={styles.heading}>
        <h2>Gestión Curricular</h2>
        <p>Cursos, competencias, asociaciones y reglas académicas</p>
      </div>
      <TabNav
        label="Secciones de gestión curricular"
        tabs={tabs}
        current={current.id}
        onSelect={(id) => router.push(`${ACADEMIC_PATH}/${id}`)}
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
