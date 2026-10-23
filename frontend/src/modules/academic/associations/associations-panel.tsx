"use client";
import { useEffect, useState, type FormEvent } from "react";
import { message } from "@/core/api/client";
import { Feedback, FormDialog, RowMenu } from "@/shared/ui";
import type { CourseCompetency } from "../types";
import styles from "../academic.module.css";
import { countLabel, timeLabel } from "../components/labels";
import { PencilIcon } from "../components/pencil-icon";
import { AssociationFormDialog, type Option } from "./association-form-dialog";
import {
  listarAsociaciones,
  listarOpciones,
  quitarAsociacion,
} from "./services/asociacionService";
type AssociationDialog =
  | { kind: "create" }
  | { kind: "edit" | "remove"; association: CourseCompetency };
type Catalog = { courses: Option[]; competencies: Option[] };
/** Pestaña Asociaciones: qué competencias trabaja cada curso y en qué orden. */
export function AssociationsPanel({ canWrite }: { canWrite: boolean }) {
  const [associations, setAssociations] = useState<CourseCompetency[] | null>(
    null,
  );
  const [catalog, setCatalog] = useState<Catalog>({
    courses: [],
    competencies: [],
  });
  const [courseFilter, setCourseFilter] = useState(0);
  const [reload, setReload] = useState(0);
  const [loading, setLoading] = useState(true);
  const [updatedAt, setUpdatedAt] = useState<Date | null>(null);
  const [loadError, setLoadError] = useState("");
  const [dialog, setDialog] = useState<AssociationDialog | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");
  useEffect(() => {
    let active = true;
    Promise.all([listarAsociaciones(), listarOpciones()])
      .then(([list, options]) => {
        if (!active) return;
        setAssociations(list);
        setCatalog(options);
        setLoadError("");
      })
      .catch((e) => {
        if (active) setLoadError(message(e));
      })
      .finally(() => {
        if (!active) return;
        setLoading(false);
        setUpdatedAt(new Date());
      });
    return () => {
      active = false;
    };
  }, [reload]);
  function refresh() {
    setLoading(true);
    setReload((value) => value + 1);
  }
  function open(next: AssociationDialog) {
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
  async function remove(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (dialog?.kind !== "remove") return;
    setBusy(true);
    setError("");
    try {
      await quitarAsociacion(dialog.association.id);
      finish(
        `${dialog.association.competenciaNombre} ya no está asociada a ${dialog.association.cursoNombre}.`,
      );
    } catch (e) {
      setError(message(e));
    } finally {
      setBusy(false);
    }
  }
  // Un filtro de un curso que ya no existe vuelve a "Todos los cursos".
  const activeFilter = catalog.courses.some((item) => item.id === courseFilter)
    ? courseFilter
    : 0;
  const visible = (associations ?? []).filter(
    (item) => !activeFilter || item.cursoId === activeFilter,
  );
  const canCreate =
    catalog.courses.length > 0 && catalog.competencies.length > 0;
  return (
    <section className="panel">
      <div className={styles.listHeading}>
        <div>
          <h3>Competencias por curso</h3>
          <p>
            {associations
              ? countLabel(visible.length, "asociación", "asociaciones")
              : "Relación entre cursos y competencias"}
            {updatedAt && ` · Última actualización: ${timeLabel(updatedAt)}`}
          </p>
        </div>
        <div className={styles.headingActions}>
          <button type="button" disabled={loading} onClick={refresh}>
            Actualizar
          </button>
          {canWrite && (
            <button
              type="button"
              className="primary-action"
              disabled={!canCreate}
              onClick={() => open({ kind: "create" })}
            >
              + Nueva asociación
            </button>
          )}
        </div>
      </div>
      <select
        className={styles.search}
        aria-label="Filtrar por curso"
        value={activeFilter || ""}
        onChange={(event) => setCourseFilter(Number(event.target.value))}
      >
        <option value="">Todos los cursos</option>
        {catalog.courses.map((course) => (
          <option key={course.id} value={course.id}>
            {course.nombre} · {course.codigo}
          </option>
        ))}
      </select>
      <Feedback error={loadError} success={success} />
      {canWrite && associations && !canCreate && (
        <p className="hint">
          Para asociar, registra primero al menos un curso y una competencia en
          sus pestañas.
        </p>
      )}
      {loading && !associations ? (
        <p role="status">Cargando asociaciones…</p>
      ) : visible.length ? (
        <div className={styles.tableWrap}>
          <table className={`user-table ${styles.table}`}>
            <thead>
              <tr>
                <th>Curso</th>
                <th>Orden</th>
                <th>Competencia</th>
                {canWrite && <th>Acciones</th>}
              </tr>
            </thead>
            <tbody>
              {visible.map((association) => (
                <tr key={association.id}>
                  <td data-label="Curso">
                    <strong>{association.cursoNombre}</strong>
                    <small>{association.cursoCodigo}</small>
                  </td>
                  <td data-label="Orden">
                    <span className={styles.order}>{association.orden}</span>
                  </td>
                  <td data-label="Competencia">
                    <strong>{association.competenciaNombre}</strong>
                    <small>{association.competenciaCodigo}</small>
                  </td>
                  {canWrite && (
                    <td data-label="Acciones">
                      <div className={styles.actions}>
                        <button
                          type="button"
                          className={styles.iconButton}
                          aria-label={`Editar ${association.competenciaNombre} en ${association.cursoNombre}`}
                          title="Editar"
                          onClick={() => open({ kind: "edit", association })}
                        >
                          <PencilIcon />
                        </button>
                        <RowMenu
                          itemName={`${association.competenciaNombre} en ${association.cursoNombre}`}
                          options={[
                            {
                              label: "Quitar del curso",
                              danger: true,
                              onSelect: () =>
                                open({ kind: "remove", association }),
                            },
                          ]}
                        />
                      </div>
                    </td>
                  )}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : (
        !loadError && (
          <p className="empty">
            {activeFilter
              ? "Este curso todavía no tiene competencias asociadas."
              : "Aún no hay competencias asociadas a los cursos."}
          </p>
        )
      )}
      {(dialog?.kind === "create" || dialog?.kind === "edit") && (
        <AssociationFormDialog
          association={dialog.kind === "edit" ? dialog.association : undefined}
          associations={associations ?? []}
          courses={catalog.courses}
          competencies={catalog.competencies}
          initialCourseId={activeFilter || undefined}
          onClose={close}
          onSaved={(saved) =>
            finish(
              `${saved.competenciaNombre} asociada a ${saved.cursoNombre} en el orden ${saved.orden}.`,
            )
          }
        />
      )}
      {dialog?.kind === "remove" && (
        <FormDialog
          title="Quitar competencia del curso"
          description={`${dialog.association.cursoNombre} · ${dialog.association.competenciaNombre}`}
          submitLabel="Quitar del curso"
          danger
          busy={busy}
          error={error}
          onClose={close}
          onSubmit={remove}
        >
          <p className="full-field hint">
            El curso dejará de trabajar esta competencia. La competencia sigue
            en el catálogo y puedes volver a asociarla después.
          </p>
        </FormDialog>
      )}
    </section>
  );
}
