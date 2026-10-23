"use client";
import { useEffect, useState, type FormEvent } from "react";
import { message } from "@/core/api/client";
import { Feedback, FormDialog, Pager, RowMenu } from "@/shared/ui";
import { usePaged } from "@/shared/hooks/use-paged";
import type { Course, CourseCompetency } from "../types";
import styles from "../academic.module.css";
import { CourseDetailDialog } from "./course-detail-dialog";
import { CourseFormDialog } from "./course-form-dialog";
import {
  CURSOS_PATH,
  desactivarCurso,
  listarCompetenciasPorCurso,
} from "./services/cursoService";
type CourseDialog =
  | { kind: "create" }
  | { kind: "detail" | "edit" | "deactivate"; course: Course };
const timeLabel = (date: Date) =>
  date.toLocaleTimeString("es-PE", {
    timeZone: "America/Lima",
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
  });
const competencyCount = (count: number) =>
  `${count} ${count === 1 ? "competencia" : "competencias"}`;
const PencilIcon = () => (
  <svg
    viewBox="0 0 24 24"
    width="16"
    height="16"
    fill="none"
    stroke="currentColor"
    strokeWidth="2"
    strokeLinecap="round"
    strokeLinejoin="round"
    aria-hidden="true"
  >
    <path d="M12 20h9" />
    <path d="M16.5 3.5a2.1 2.1 0 0 1 3 3L7 19l-4 1 1-4z" />
  </svg>
);
/** Pestaña Cursos de Gestión Curricular: listado, detalle, alta, edición y baja. */
export function CoursesPanel({ canWrite }: { canWrite: boolean }) {
  const courses = usePaged<Course>(CURSOS_PATH);
  const [competencies, setCompetencies] = useState<Map<
    number,
    CourseCompetency[]
  > | null>(null);
  const [competenciesError, setCompetenciesError] = useState(false);
  const [reload, setReload] = useState(0);
  const [updatedAt, setUpdatedAt] = useState<Date | null>(null);
  const [dialog, setDialog] = useState<CourseDialog | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");
  useEffect(() => {
    let active = true;
    listarCompetenciasPorCurso()
      .then((value) => {
        if (!active) return;
        setCompetencies(value);
        setCompetenciesError(false);
      })
      .catch(() => {
        if (active) setCompetenciesError(true);
      })
      .finally(() => {
        if (active) setUpdatedAt(new Date());
      });
    return () => {
      active = false;
    };
  }, [reload]);
  function refresh() {
    courses.refresh();
    setReload((value) => value + 1);
  }
  function open(next: CourseDialog) {
    setError("");
    setSuccess("");
    setDialog(next);
  }
  function close() {
    setDialog(null);
    setError("");
  }
  function finish(feedback: string) {
    setDialog(null);
    setSuccess(feedback);
    refresh();
  }
  async function deactivate(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (dialog?.kind !== "deactivate") return;
    setBusy(true);
    setError("");
    try {
      await desactivarCurso(dialog.course.id);
      finish(`Curso ${dialog.course.nombre} desactivado.`);
    } catch (e) {
      setError(message(e));
    } finally {
      setBusy(false);
    }
  }
  const competenciesOf = (course: Course) =>
    competenciesError ? null : (competencies?.get(course.id) ?? []);
  const total = courses.data?.totalElements ?? 0;
  return (
    <section className="panel">
      <div className={styles.listHeading}>
        <div>
          <h3>Cursos registrados</h3>
          <p>
            {courses.data
              ? `${total} ${total === 1 ? "curso" : "cursos"} en el sistema`
              : "Plan curricular de la institución"}
            {updatedAt && ` · Última actualización: ${timeLabel(updatedAt)}`}
          </p>
        </div>
        <div className={styles.headingActions}>
          <button type="button" disabled={courses.loading} onClick={refresh}>
            Actualizar
          </button>
          {canWrite && (
            <button
              type="button"
              className="primary-action"
              onClick={() => open({ kind: "create" })}
            >
              + Nuevo curso
            </button>
          )}
        </div>
      </div>
      <Feedback error={courses.error} success={success} />
      {competenciesError && (
        <p className="hint">
          No se pudieron consultar las competencias asociadas.
        </p>
      )}
      {courses.loading ? (
        <p role="status">Cargando cursos…</p>
      ) : courses.data?.content.length ? (
        <div className={styles.tableWrap}>
          <table className={`user-table ${styles.table}`}>
            <thead>
              <tr>
                <th>Curso</th>
                <th>Horas semanales</th>
                <th>Estado</th>
                <th>Competencias</th>
                <th>Acciones</th>
              </tr>
            </thead>
            <tbody>
              {courses.data.content.map((course) => {
                const linked = competenciesOf(course);
                return (
                  <tr key={course.id}>
                    <td data-label="Curso">
                      <strong>{course.nombre}</strong>
                      <small>{course.codigo}</small>
                    </td>
                    <td data-label="Horas semanales">
                      {course.horasSemanales} h
                    </td>
                    <td data-label="Estado">
                      <span
                        className={`status ${course.activo ? "status-active" : "status-inactive"}`}
                      >
                        {course.activo ? "Activo" : "Inactivo"}
                      </span>
                    </td>
                    <td data-label="Competencias">
                      {linked ? competencyCount(linked.length) : "—"}
                    </td>
                    <td data-label="Acciones">
                      <div className={styles.actions}>
                        <button
                          type="button"
                          className={styles.detailButton}
                          onClick={() => open({ kind: "detail", course })}
                        >
                          Ver detalle
                        </button>
                        {canWrite && (
                          <>
                            <button
                              type="button"
                              className={styles.iconButton}
                              aria-label={`Editar ${course.nombre}`}
                              title="Editar"
                              onClick={() => open({ kind: "edit", course })}
                            >
                              <PencilIcon />
                            </button>
                            <RowMenu
                              itemName={course.nombre}
                              options={[
                                {
                                  label: "Desactivar",
                                  danger: true,
                                  onSelect: () =>
                                    open({ kind: "deactivate", course }),
                                },
                              ]}
                            />
                          </>
                        )}
                      </div>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      ) : (
        !courses.error && (
          <p className="empty">
            Aún no hay cursos registrados.
            {canWrite && " Usa «Nuevo curso» para crear el primero."}
          </p>
        )
      )}
      <Pager
        data={courses.data}
        loading={courses.loading}
        setPage={courses.setPage}
      />
      {dialog?.kind === "detail" && (
        <CourseDetailDialog
          course={dialog.course}
          competencies={competenciesOf(dialog.course)}
          onClose={close}
          onEdit={
            canWrite
              ? () => setDialog({ kind: "edit", course: dialog.course })
              : undefined
          }
        />
      )}
      {(dialog?.kind === "create" || dialog?.kind === "edit") && (
        <CourseFormDialog
          course={dialog.kind === "edit" ? dialog.course : undefined}
          onClose={close}
          onSaved={(saved) =>
            finish(
              dialog.kind === "edit"
                ? `Curso ${saved.nombre} actualizado.`
                : `Curso ${saved.nombre} creado.`,
            )
          }
        />
      )}
      {dialog?.kind === "deactivate" && (
        <FormDialog
          title="Desactivar curso"
          busy={busy}
          error={error}
          onClose={close}
          onSubmit={deactivate}
        >
          <p className="full-field">
            {dialog.course.nombre} dejará de mostrarse en el plan curricular y
            no podrá usarse en nuevas asignaciones. Su historial se conserva.
          </p>
        </FormDialog>
      )}
    </section>
  );
}
