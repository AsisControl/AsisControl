"use client";
import { useState, type FormEvent } from "react";
import { api, message } from "@/core/api/client";
import { Field, FormDialog, ReasonField } from "@/shared/ui";
import { isOverCapacity, vacancies } from "../lib/section-metrics";
import styles from "../academic-config.module.css";
import type { Section, YearPanelProps } from "../types";
import { OccupancyBar } from "./occupancy-bar";
type SectionAction =
  | { kind: "section" }
  | { kind: "section-edit"; target: Section }
  | { kind: "section-disable"; target: Section; next: boolean };
const titles: Record<SectionAction["kind"], string> = {
  section: "Crear sección",
  "section-edit": "Editar sección",
  "section-disable": "Cambiar estado de sección",
};
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
  const [proposedGrade, setProposedGrade] = useState(0);
  const [gradeFilter, setGradeFilter] = useState("");
  const open = (value: SectionAction) => {
    setError("");
    onSuccess("");
    setAction(value);
    setProposedGrade(0);
  };
  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!action) return;
    const form = new FormData(event.currentTarget);
    const str = (key: string) => String(form.get(key) || "").trim();
    setBusy(true);
    setError("");
    try {
      if (action.kind === "section-disable") {
        await api(`/api/secciones/${action.target.id}/estado`, {
          method: "PUT",
          body: JSON.stringify({
            activo: action.next,
            version: action.target.version,
            motivo: str("motivo"),
          }),
        });
      } else {
        const original = action.kind === "section-edit" ? action.target : null;
        let path = "/api/secciones";
        let tutor: number | null = null;
        if (original) {
          const detail = await api<{ docenteTutorId: number | null }>(
            `${path}/${original.id}`,
          );
          tutor = detail.docenteTutorId;
          path += `/${original.id}`;
        }
        await api(path, {
          method: original ? "PUT" : "POST",
          body: JSON.stringify({
            nombre: str("nombre"),
            grado: original?.grado || Number(form.get("numero")),
            anioAcademico: year.anio,
            capacidadMaxima: Number(form.get("capacidad")),
            docenteTutorId: tutor,
            motivo: str("motivo") || null,
            ...(original ? { version: original.version } : {}),
          }),
        });
      }
      await onReload();
      setAction(null);
      onSuccess(
        action.kind === "section-disable"
          ? "Estado de sección actualizado. Su información se conserva."
          : "Configuración guardada.",
      );
    } catch (error) {
      setError(message(error));
    } finally {
      setBusy(false);
    }
  }
  const target = action && action.kind !== "section" ? action.target : null;
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
      {action && (
        <FormDialog
          title={titles[action.kind]}
          busy={busy}
          error={error}
          onClose={() => setAction(null)}
          onSubmit={save}
          notes={
            action.kind === "section-disable" && (
              <p>
                Solo pueden inhabilitarse secciones sin solicitudes, matrículas
                ni asignaciones. Su registro se conserva.
              </p>
            )
          }
        >
          {action.kind === "section" && (
            <label className="field">
              Grado *
              <select
                name="numero"
                required
                defaultValue=""
                onChange={(event) =>
                  setProposedGrade(Number(event.target.value))
                }
              >
                <option value="" disabled>
                  Seleccionar grado
                </option>
                {year.grados
                  .filter((g) => g.activo)
                  .map((g) => (
                    <option key={g.numero} value={g.numero}>
                      {g.numero}° de secundaria
                    </option>
                  ))}
              </select>
            </label>
          )}
          {action.kind !== "section-disable" && (
            <>
              <Field
                label="Nombre de sección"
                name="nombre"
                required
                maxLength={20}
                defaultValue={target?.nombre || ""}
                placeholder="Ej. A"
              />
              <Field
                label="Capacidad máxima"
                name="capacidad"
                key={action.kind === "section" ? proposedGrade : "capacity"}
                type="number"
                min={1}
                max={100}
                required
                defaultValue={
                  target
                    ? target.capacidadMaxima
                    : year.grados.find((grade) => grade.numero === proposedGrade)
                        ?.capacidadDefault || 30
                }
              />
            </>
          )}
          {(action.kind !== "section" || year.estado === "ACTIVO") && (
            <ReasonField />
          )}
        </FormDialog>
      )}
    </>
  );
}
