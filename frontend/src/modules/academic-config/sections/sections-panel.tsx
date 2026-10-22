"use client";
import { useState, type FormEvent } from "react";
import { api, message } from "@/core/api/client";
import { FormDialog, ReasonField } from "@/shared/ui";
import { isOverCapacity, vacancies } from "../lib/section-metrics";
import styles from "../academic-config.module.css";
import type { Section, YearPanelProps } from "../types";
import { OccupancyBar } from "./occupancy-bar";
import { SectionFormDialog } from "./section-form-dialog";
type SectionAction =
  | { kind: "section" }
  | { kind: "section-edit"; target: Section }
  | { kind: "section-disable"; target: Section; next: boolean };
const iconProps = {
  width: 16,
  height: 16,
  viewBox: "0 0 24 24",
  fill: "none",
  stroke: "currentColor",
  strokeWidth: 2,
  strokeLinecap: "round" as const,
  strokeLinejoin: "round" as const,
  "aria-hidden": true,
};
const PencilIcon = () => (
  <svg {...iconProps}>
    <path d="M12 20h9" />
    <path d="M16.5 3.5a2.1 2.1 0 0 1 3 3L7 19l-4 1 1-4z" />
  </svg>
);
const AlertIcon = () => (
  <svg {...iconProps}>
    <path d="M10.3 3.9 1.8 18a2 2 0 0 0 1.7 3h17a2 2 0 0 0 1.7-3L13.7 3.9a2 2 0 0 0-3.4 0z" />
    <path d="M12 9v4M12 17h.01" />
  </svg>
);
/** US-06: secciones del año, capacidad máxima, vacantes y ocupación. */
export function SectionsPanel({
  year,
  editable,
  onReload,
  onSuccess,
}: YearPanelProps) {
  const [action, setAction] = useState<SectionAction | null>(null);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const [gradeFilter, setGradeFilter] = useState("");
  const open = (value: SectionAction) => {
    setError("");
    onSuccess("");
    setAction(value);
  };
  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!action || action.kind !== "section-disable" || busy) return;
    const form = new FormData(event.currentTarget);
    setBusy(true);
    setError("");
    try {
      await api(`/api/secciones/${action.target.id}/estado`, {
        method: "PUT",
        body: JSON.stringify({
          activo: action.next,
          version: action.target.version,
          motivo: String(form.get("motivo") || "").trim(),
        }),
      });
      await onReload();
      setAction(null);
      onSuccess("Estado de sección actualizado. Su información se conserva.");
    } catch (error) {
      setError(message(error));
    } finally {
      setBusy(false);
    }
  }
  /** Tras crear o editar: recarga el año, cierra el modal y avisa el éxito. */
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
  const hasActiveGrades = year.grados.some((g) => g.activo);
  const gradeOptions = [...year.grados].sort((a, b) => a.numero - b.numero);
  const activeFilter = gradeOptions.some((g) => String(g.numero) === gradeFilter)
    ? gradeFilter
    : "";
  const visible = year.secciones.filter(
    (section) => !activeFilter || String(section.grado) === activeFilter,
  );
  const emptyText = !hasActiveGrades
    ? "El año no tiene grados habilitados. Habilita un grado para crear secciones."
    : !year.secciones.length
      ? "Sin secciones configuradas. Usa «Nueva sección» para crear la primera."
      : "No hay secciones para el grado seleccionado.";
  return (
    <>
      <div className={styles.sectionsHeader}>
        <h4>Grados y Secciones</h4>
        <div className={styles.sectionsControls}>
          <select
            className={styles.gradeFilter}
            aria-label="Filtrar por grado"
            value={activeFilter}
            onChange={(event) => setGradeFilter(event.target.value)}
          >
            <option value="">Todos los grados</option>
            {gradeOptions.map((g) => (
              <option key={g.numero} value={g.numero}>
                {g.numero}° Grado
              </option>
            ))}
          </select>
          {editable && (
            <div className={styles.newPeriod}>
              <button
                className={styles.newPeriodButton}
                disabled={!hasActiveGrades}
                onClick={() => open({ kind: "section" })}
              >
                + Nueva sección
              </button>
              {!hasActiveGrades && (
                <span className={styles.newPeriodHint}>
                  Habilita al menos un grado para crear secciones.
                </span>
              )}
            </div>
          )}
        </div>
      </div>
      <div className={styles.tableWrap}>
        <table className={styles.table}>
          <thead>
            <tr>
              <th>Grado</th>
              <th>Sección</th>
              <th>Capacidad máx.</th>
              <th>Matriculados</th>
              <th>Vacantes</th>
              <th>Ocupación</th>
              {editable && <th>Acciones</th>}
            </tr>
          </thead>
          <tbody>
            {visible.map((section) => (
              <tr
                key={section.id}
                className={section.activo ? undefined : styles.rowDisabled}
              >
                <td className={styles.gradeCell}>{section.grado}° Grado</td>
                <td className={styles.strongCell}>
                  Sección {section.nombre}
                  {!section.activo && (
                    <span className={styles.disabledBadge}>Inhabilitada</span>
                  )}
                </td>
                <td>{section.capacidadMaxima}</td>
                <td className={styles.strongCell}>{section.ocupacion}</td>
                <td>
                  {isOverCapacity(section) ? (
                    <span className={styles.overCapacity}>
                      <AlertIcon /> Sobre capacidad
                    </span>
                  ) : (
                    <span className={styles.vacancies}>
                      {vacancies(section)}
                    </span>
                  )}
                </td>
                <td>
                  <OccupancyBar section={section} />
                </td>
                {editable && (
                  <td>
                    <div className={styles.rowActions}>
                      {section.activo && (
                        <button
                          className={styles.iconButton}
                          aria-label={`Editar ${section.grado}° Grado, Sección ${section.nombre}`}
                          title="Editar"
                          onClick={() =>
                            open({ kind: "section-edit", target: section })
                          }
                        >
                          <PencilIcon />
                        </button>
                      )}
                      <button
                        className={styles.textAction}
                        onClick={() =>
                          open({
                            kind: "section-disable",
                            target: section,
                            next: !section.activo,
                          })
                        }
                      >
                        {section.activo ? "Inhabilitar" : "Habilitar"}
                      </button>
                    </div>
                  </td>
                )}
              </tr>
            ))}
            {!visible.length && (
              <tr>
                <td className={styles.emptyCell} colSpan={editable ? 7 : 6}>
                  {emptyText}
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
      {action && action.kind !== "section-disable" && (
        <SectionFormDialog
          year={year}
          section={action.kind === "section-edit" ? action.target : undefined}
          onClose={() => setAction(null)}
          onSaved={saved}
        />
      )}
      {action?.kind === "section-disable" && (
        <FormDialog
          title="Cambiar estado de sección"
          busy={busy}
          error={error}
          onClose={() => setAction(null)}
          onSubmit={save}
          notes={
            <p>
              Solo pueden inhabilitarse secciones sin solicitudes, matrículas ni
              asignaciones. Su registro se conserva.
            </p>
          }
        >
          <ReasonField />
        </FormDialog>
      )}
    </>
  );
}
