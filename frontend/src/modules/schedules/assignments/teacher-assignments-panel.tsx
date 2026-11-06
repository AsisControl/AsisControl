"use client";
import { useState } from "react";
import { Feedback, PanelHeading, Pager, RowMenu, StatusPill } from "@/shared/ui";
import { usePaged } from "@/shared/hooks/use-paged";
import { countLabel, timeLabel } from "@/shared/lib/formatters";
import type { AssignmentState, TeacherAssignment } from "../types";
import styles from "../schedules.module.css";
import { AssignmentFormDialog, sectionLabel } from "./assignment-form-dialog";
import { AssignmentRetireDialog } from "./assignment-retire-dialog";
import { ASIGNACIONES_PATH, assignmentFilters } from "./services/asignacionService";
type AssignmentDialog =
  | { kind: "create" }
  | { kind: "edit" | "retire"; assignment: TeacherAssignment };
const STATE_OPTIONS: { value: AssignmentState; label: string }[] = [
  { value: "ACTIVAS", label: "Activas" },
  { value: "RETIRADAS", label: "Retiradas" },
  { value: "TODAS", label: "Todas" },
];
/** Pestaña Asignaciones docentes de Horario Académico (US-58): listado, alta, edición y retiro. */
export function TeacherAssignmentsPanel({ canWrite }: { canWrite: boolean }) {
  const [state, setState] = useState<AssignmentState>("ACTIVAS");
  const [year, setYear] = useState("");
  const assignments = usePaged<TeacherAssignment>(
    ASIGNACIONES_PATH,
    "",
    assignmentFilters(state, year),
  );
  const [dialog, setDialog] = useState<AssignmentDialog | null>(null);
  const [success, setSuccess] = useState("");
  function open(next: AssignmentDialog) {
    setSuccess("");
    setDialog(next);
  }
  function finish(feedback: string) {
    setDialog(null);
    setSuccess(feedback);
    assignments.refresh();
  }
  const rows = assignments.data?.content ?? [];
  const total = assignments.data?.totalElements ?? 0;
  return (
    <section className="panel">
      <PanelHeading
        title="Asignaciones docentes"
        summary={
          <>
            {assignments.data
              ? countLabel(total, "asignación", "asignaciones")
              : "Qué docente dicta cada curso en cada sección"}
            {assignments.updatedAt &&
              ` · Última actualización: ${timeLabel(assignments.updatedAt)}`}
          </>
        }
        actions={
          <>
            <button
              type="button"
              disabled={assignments.loading}
              onClick={assignments.refresh}
            >
              Actualizar
            </button>
            {canWrite && (
              <button
                type="button"
                className="primary-action"
                onClick={() => open({ kind: "create" })}
              >
                + Nueva asignación
              </button>
            )}
          </>
        }
      />
      <p className={styles.availabilityNotice}>
        Un docente solo registra asistencia y notas en los cursos y secciones
        que tiene asignados. No se puede retirar a un docente que todavía tiene
        bloques de horario.
      </p>
      <div className={styles.filters}>
        <select
          aria-label="Filtrar por estado"
          value={state}
          onChange={(e) => {
            setState(e.target.value as AssignmentState);
            assignments.setPage(0);
          }}
        >
          {STATE_OPTIONS.map((option) => (
            <option key={option.value} value={option.value}>
              {option.label}
            </option>
          ))}
        </select>
        <input
          type="number"
          aria-label="Filtrar por año académico"
          placeholder="Año académico"
          min={2000}
          max={2100}
          value={year}
          onChange={(e) => {
            setYear(e.target.value);
            assignments.setPage(0);
          }}
        />
      </div>
      <Feedback error={assignments.error} success={success} />
      {assignments.loading ? (
        <p role="status">Cargando asignaciones…</p>
      ) : rows.length ? (
        <div className={styles.tableWrap}>
          <table className={`user-table ${styles.table}`}>
            <thead>
              <tr>
                <th>Docente</th>
                <th>Curso</th>
                <th>Sección</th>
                <th>Año</th>
                <th>Horario</th>
                <th>Estado</th>
                {canWrite && <th>Acciones</th>}
              </tr>
            </thead>
            <tbody>
              {rows.map((assignment) => (
                <tr key={assignment.id}>
                  <td data-label="Docente">
                    <strong>{assignment.docente}</strong>
                  </td>
                  <td data-label="Curso">{assignment.curso}</td>
                  <td data-label="Sección">
                    {sectionLabel(assignment.grado, assignment.seccion)}
                  </td>
                  <td data-label="Año">{assignment.anioAcademico}</td>
                  <td data-label="Horario">
                    {assignment.horariosActivos
                      ? countLabel(assignment.horariosActivos, "bloque", "bloques")
                      : "Sin bloques"}
                  </td>
                  <td data-label="Estado">
                    {assignment.activo ? (
                      <StatusPill state="ACTIVA" label="Activa" tone="success" />
                    ) : (
                      <StatusPill state="RETIRADA" label="Retirada" tone="neutral" />
                    )}
                  </td>
                  {canWrite && (
                    <td data-label="Acciones">
                      {assignment.activo && (
                        <RowMenu
                          itemName={`${assignment.docente} en ${assignment.curso}`}
                          options={[
                            {
                              label: "Editar",
                              onSelect: () => open({ kind: "edit", assignment }),
                            },
                            {
                              label: "Retirar docente",
                              danger: true,
                              onSelect: () => open({ kind: "retire", assignment }),
                            },
                          ]}
                        />
                      )}
                    </td>
                  )}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : (
        !assignments.error && (
          <p className="empty">
            No hay asignaciones con esos filtros.
            {canWrite && state === "ACTIVAS" && " Usa «Nueva asignación» para registrar la primera."}
          </p>
        )
      )}
      <Pager
        data={assignments.data}
        loading={assignments.loading}
        setPage={assignments.setPage}
      />
      {(dialog?.kind === "create" || dialog?.kind === "edit") && (
        <AssignmentFormDialog
          assignment={dialog.kind === "edit" ? dialog.assignment : undefined}
          onClose={() => setDialog(null)}
          onSaved={(saved) =>
            finish(
              dialog.kind === "edit"
                ? `Asignación de ${saved.docente} actualizada.`
                : `Se asignó a ${saved.docente} el curso ${saved.curso} (${sectionLabel(saved.grado, saved.seccion)}).`,
            )
          }
        />
      )}
      {dialog?.kind === "retire" && (
        <AssignmentRetireDialog
          assignment={dialog.assignment}
          onClose={() => setDialog(null)}
          onSaved={(saved) =>
            finish(`Se retiró a ${saved.docente} del curso ${saved.curso}.`)
          }
        />
      )}
    </section>
  );
}
