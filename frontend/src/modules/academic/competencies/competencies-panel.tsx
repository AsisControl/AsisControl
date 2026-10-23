"use client";
import { useEffect, useState, type FormEvent } from "react";
import { message } from "@/core/api/client";
import { Feedback, FormDialog, Pager, RowMenu } from "@/shared/ui";
import { usePaged } from "@/shared/hooks/use-paged";
import type { Competency, CourseCompetency } from "../types";
import styles from "../academic.module.css";
import {
  agruparPor,
  listarAsociaciones,
} from "../associations/services/asociacionService";
import { countLabel, timeLabel } from "../components/labels";
import { PencilIcon } from "../components/pencil-icon";
import { CompetencyDetailDialog } from "./competency-detail-dialog";
import { CompetencyFormDialog } from "./competency-form-dialog";
import {
  competenciasPath,
  desactivarCompetencia,
} from "./services/competenciaService";
type CompetencyDialog =
  | { kind: "create" }
  | { kind: "detail" | "edit" | "deactivate"; competency: Competency };
const SEARCH_DELAY_MS = 300;
/** Pestaña Competencias: catálogo de competencias y los cursos que las trabajan. */
export function CompetenciesPanel({ canWrite }: { canWrite: boolean }) {
  const [searchText, setSearchText] = useState("");
  const [search, setSearch] = useState("");
  const competencies = usePaged<Competency>(competenciasPath(search));
  const { setPage } = competencies;
  const [courses, setCourses] = useState<Map<
    number,
    CourseCompetency[]
  > | null>(null);
  const [coursesError, setCoursesError] = useState(false);
  const [reload, setReload] = useState(0);
  const [updatedAt, setUpdatedAt] = useState<Date | null>(null);
  const [dialog, setDialog] = useState<CompetencyDialog | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");
  useEffect(() => {
    let active = true;
    listarAsociaciones()
      .then((value) => {
        if (!active) return;
        setCourses(agruparPor(value, "competenciaId"));
        setCoursesError(false);
      })
      .catch(() => {
        if (active) setCoursesError(true);
      })
      .finally(() => {
        if (active) setUpdatedAt(new Date());
      });
    return () => {
      active = false;
    };
  }, [reload]);
  useEffect(() => {
    const timer = setTimeout(() => {
      setSearch(searchText.trim());
      setPage(0);
    }, SEARCH_DELAY_MS);
    return () => clearTimeout(timer);
  }, [searchText, setPage]);
  function refresh() {
    competencies.refresh();
    setReload((value) => value + 1);
  }
  function open(next: CompetencyDialog) {
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
      await desactivarCompetencia(dialog.competency.id);
      finish(`Competencia ${dialog.competency.nombre} desactivada.`);
    } catch (e) {
      setError(message(e));
    } finally {
      setBusy(false);
    }
  }
  const coursesOf = (competency: Competency) =>
    coursesError ? null : (courses?.get(competency.id) ?? []);
  const total = competencies.data?.totalElements ?? 0;
  const deactivatingCourses =
    dialog?.kind === "deactivate" ? coursesOf(dialog.competency) : null;
  return (
    <section className="panel">
      <div className={styles.listHeading}>
        <div>
          <h3>Competencias registradas</h3>
          <p>
            {competencies.data
              ? countLabel(total, "competencia", "competencias") +
                (search ? " encontradas" : " en el sistema")
              : "Catálogo de competencias del plan curricular"}
            {updatedAt && ` · Última actualización: ${timeLabel(updatedAt)}`}
          </p>
        </div>
        <div className={styles.headingActions}>
          <button
            type="button"
            disabled={competencies.loading}
            onClick={refresh}
          >
            Actualizar
          </button>
          {canWrite && (
            <button
              type="button"
              className="primary-action"
              onClick={() => open({ kind: "create" })}
            >
              + Nueva competencia
            </button>
          )}
        </div>
      </div>
      <input
        type="search"
        className={styles.search}
        aria-label="Buscar competencias"
        placeholder="Buscar por código o nombre…"
        value={searchText}
        onChange={(event) => setSearchText(event.target.value)}
      />
      <Feedback error={competencies.error} success={success} />
      {coursesError && (
        <p className="hint">No se pudieron consultar los cursos asociados.</p>
      )}
      {competencies.loading ? (
        <p role="status">Cargando competencias…</p>
      ) : competencies.data?.content.length ? (
        <div className={styles.tableWrap}>
          <table className={`user-table ${styles.table}`}>
            <thead>
              <tr>
                <th>Competencia</th>
                <th>Descripción</th>
                <th>Cursos</th>
                <th>Estado</th>
                <th>Acciones</th>
              </tr>
            </thead>
            <tbody>
              {competencies.data.content.map((competency) => {
                const linked = coursesOf(competency);
                return (
                  <tr key={competency.id}>
                    <td data-label="Competencia">
                      <strong>{competency.nombre}</strong>
                      <small>{competency.codigo}</small>
                    </td>
                    <td data-label="Descripción" className={styles.clamp}>
                      {competency.descripcion || "—"}
                    </td>
                    <td data-label="Cursos">
                      {linked ? countLabel(linked.length, "curso", "cursos") : "—"}
                    </td>
                    <td data-label="Estado">
                      <span
                        className={`status ${competency.activo ? "status-active" : "status-inactive"}`}
                      >
                        {competency.activo ? "Activa" : "Inactiva"}
                      </span>
                    </td>
                    <td data-label="Acciones">
                      <div className={styles.actions}>
                        <button
                          type="button"
                          className={styles.detailButton}
                          onClick={() => open({ kind: "detail", competency })}
                        >
                          Ver detalle
                        </button>
                        {canWrite && (
                          <>
                            <button
                              type="button"
                              className={styles.iconButton}
                              aria-label={`Editar ${competency.nombre}`}
                              title="Editar"
                              onClick={() => open({ kind: "edit", competency })}
                            >
                              <PencilIcon />
                            </button>
                            <RowMenu
                              itemName={competency.nombre}
                              options={[
                                {
                                  label: "Desactivar",
                                  danger: true,
                                  onSelect: () =>
                                    open({ kind: "deactivate", competency }),
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
        !competencies.error && (
          <p className="empty">
            {search
              ? "No hay competencias que coincidan con la búsqueda."
              : "Aún no hay competencias registradas."}
          </p>
        )
      )}
      <Pager
        data={competencies.data}
        loading={competencies.loading}
        setPage={setPage}
      />
      {dialog?.kind === "detail" && (
        <CompetencyDetailDialog
          competency={dialog.competency}
          courses={coursesOf(dialog.competency)}
          onClose={close}
          onEdit={
            canWrite
              ? () => setDialog({ kind: "edit", competency: dialog.competency })
              : undefined
          }
        />
      )}
      {(dialog?.kind === "create" || dialog?.kind === "edit") && (
        <CompetencyFormDialog
          competency={dialog.kind === "edit" ? dialog.competency : undefined}
          onClose={close}
          onSaved={(saved) =>
            finish(
              dialog.kind === "edit"
                ? `Competencia ${saved.nombre} actualizada.`
                : `Competencia ${saved.nombre} creada.`,
            )
          }
        />
      )}
      {dialog?.kind === "deactivate" && (
        <FormDialog
          title="Desactivar competencia"
          description={`${dialog.competency.nombre} · ${dialog.competency.codigo}`}
          submitLabel="Desactivar competencia"
          danger
          busy={busy}
          error={error}
          onClose={close}
          onSubmit={deactivate}
        >
          <p className="full-field hint">
            Dejará de mostrarse en el catálogo y en los cursos que la trabajan
            {deactivatingCourses?.length
              ? ` (${countLabel(deactivatingCourses.length, "curso", "cursos")})`
              : ""}
            . Su historial se conserva.
          </p>
        </FormDialog>
      )}
    </section>
  );
}
