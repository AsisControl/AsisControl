"use client";
import { useEffect, useState, type FormEvent } from "react";
import { message } from "@/core/api/client";
import {
  Feedback,
  FormDialog,
  Pager,
  RowMenu,
  PanelHeading,
} from "@/shared/ui";
import { usePaged } from "@/shared/hooks/use-paged";
import type { Course, CourseCompetency } from "../types";
import styles from "../academic.module.css";
import { CourseDetailDialog } from "./course-detail-dialog";
import { CourseFormDialog } from "./course-form-dialog";
import {
  agruparPor,
  listarAsociaciones,
} from "../associations/services/asociacionService";
import { countLabel, timeLabel } from "@/shared/lib/formatters";
import { PencilIcon } from "../components/pencil-icon";
import { CURSOS_PATH, desactivarCurso } from "./services/cursoService";
import { courseContextLabel } from "./course-context";
type CourseDialog =
  | { kind: "create" }
  | { kind: "detail" | "edit" | "deactivate"; course: Course };
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
    listarAsociaciones()
      .then((value) => {
        if (!active) return;
        setCompetencies(agruparPor(value, "cursoId"));
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
      <PanelHeading
        title="Cursos registrados"
        summary={
          <>
            {courses.data
              ? `${total} ${total === 1 ? "curso" : "cursos"} en el sistema`
              : "Plan curricular de la institución"}
            {updatedAt && ` · Última actualización: ${timeLabel(updatedAt)}`}
          </>
        }
        actions={
          <>
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
          </>
        }
      />
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
                <th>Año y grado</th>
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
                    <td data-label="Año y grado">
                      {course.grado === null ? (
                        <span className="hint">
                          {courseContextLabel(course)}
                        </span>
                      ) : (
                        courseContextLabel(course)
                      )}
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
                      {linked
                        ? countLabel(
                            linked.length,
                            "competencia",
                            "competencias",
                          )
                        : "—"}
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
          description={`${dialog.course.nombre} · ${dialog.course.codigo}`}
          submitLabel="Desactivar curso"
          danger
          busy={busy}
          error={error}
          onClose={close}
          onSubmit={deactivate}
        >
          <p className="full-field hint">
            {dialog.course.nombre} dejará de mostrarse en el plan curricular y
            no podrá usarse en nuevas asignaciones. Su historial se conserva.
          </p>
        </FormDialog>
      )}
    </section>
  );
}
