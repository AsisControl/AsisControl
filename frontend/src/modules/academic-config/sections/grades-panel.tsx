"use client";
import { useId, useState } from "react";
import { availableGradeNumbers } from "../lib/validation";
import styles from "../academic-config.module.css";
import type { Grade, YearPanelProps } from "../types";
import { GradeFormDialog, type GradeDialogMode } from "./grade-form-dialog";
type GradeAction = { mode: GradeDialogMode; id?: number };
/** US-06: grados habilitados del año y su capacidad por defecto (plegable). */
export function GradesPanel({
  year,
  editable,
  onReload,
  onSuccess,
}: YearPanelProps) {
  const [expanded, setExpanded] = useState(false);
  const [action, setAction] = useState<GradeAction | null>(null);
  const bodyId = useId();
  const grades = [...year.grados].sort((a, b) => a.numero - b.numero);
  const canAdd = availableGradeNumbers(grades.map((g) => g.numero)).length > 0;
  const hasActiveGrades = grades.some((g) => g.activo);
  const target = action?.id
    ? year.grados.find((g) => g.id === action.id)
    : undefined;
  const activeSectionsOf = (grade: Grade) =>
    year.secciones.filter((s) => s.activo && s.grado === grade.numero);
  const open = (value: GradeAction) => {
    onSuccess("");
    setAction(value);
  };
  /** Tras guardar: recarga el año, cierra el modal y avisa el éxito. */
  async function saved(text: string) {
    try {
      await onReload();
      onSuccess(text);
    } catch {
      onSuccess(`${text} No se pudo actualizar la tabla; recarga la página.`);
    } finally {
      setAction(null);
    }
  }
  const colSpan = editable ? 6 : 5;
  return (
    <section className={styles.collapsible}>
      <button
        type="button"
        className={styles.collapsibleToggle}
        aria-expanded={expanded}
        aria-controls={bodyId}
        onClick={() => setExpanded(!expanded)}
      >
        <span className={styles.collapsibleTitle}>Grados habilitados</span>
        <span className={styles.chevron} aria-hidden="true">
          {expanded ? "▲" : "▼"}
        </span>
      </button>
      {expanded && (
        <div id={bodyId} className={styles.collapsibleBody}>
          {editable ? (
            <div className={styles.toolbar}>
              <button
                type="button"
                disabled={!canAdd}
                onClick={() => open({ mode: "create" })}
              >
                Habilitar grado
              </button>
              {!canAdd && (
                <span className={styles.fieldHint}>
                  Los cinco grados ya están configurados.
                </span>
              )}
            </div>
          ) : (
            <p className="hint">Año cerrado: los grados son de solo lectura.</p>
          )}
          <div className={styles.tableWrap}>
            <table className={styles.table}>
              <thead>
                <tr>
                  <th scope="col">Grado</th>
                  <th scope="col">Capacidad por defecto</th>
                  <th scope="col">Capacidad total</th>
                  <th scope="col">Secciones</th>
                  <th scope="col">Estado</th>
                  {editable && <th scope="col">Acciones</th>}
                </tr>
              </thead>
              <tbody>
                {grades.map((grade) => {
                  const active = activeSectionsOf(grade);
                  return (
                    <tr
                      key={grade.id}
                      className={grade.activo ? undefined : styles.rowDisabled}
                    >
                      <td className={styles.gradeCell}>{grade.numero}° Grado</td>
                      <td>{grade.capacidadDefault}</td>
                      <td className={styles.strongCell}>
                        {active.reduce((sum, s) => sum + s.capacidadMaxima, 0)}
                      </td>
                      <td>{active.length}</td>
                      <td>
                        <span
                          className={
                            grade.activo
                              ? styles.activeTag
                              : styles.disabledBadge
                          }
                        >
                          {grade.activo ? "Habilitado" : "Inhabilitado"}
                        </span>
                      </td>
                      {editable && (
                        <td>
                          <div className={styles.rowActions}>
                            <button
                              type="button"
                              onClick={() =>
                                open({ mode: "edit", id: grade.id })
                              }
                            >
                              Editar
                            </button>
                            <button
                              type="button"
                              className={styles.textAction}
                              onClick={() =>
                                open({ mode: "state", id: grade.id })
                              }
                            >
                              {grade.activo ? "Inhabilitar" : "Habilitar"}
                            </button>
                          </div>
                        </td>
                      )}
                    </tr>
                  );
                })}
                {!grades.length && (
                  <tr>
                    <td className={styles.emptyCell} colSpan={colSpan}>
                      Habilita al menos un grado para poder crear secciones.
                    </td>
                  </tr>
                )}
                {grades.length > 0 && !hasActiveGrades && (
                  <tr>
                    <td className={styles.emptyCell} colSpan={colSpan}>
                      Todos los grados están inhabilitados. Habilita al menos
                      uno para poder crear secciones.
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        </div>
      )}
      {editable && action && (action.mode === "create" || target) && (
        <GradeFormDialog
          year={year}
          mode={action.mode}
          grade={target}
          activeSections={target ? activeSectionsOf(target).length : 0}
          onClose={() => setAction(null)}
          onSaved={saved}
          onStale={() => void onReload().catch(() => undefined)}
        />
      )}
    </section>
  );
}
