"use client";
import { useState, type FormEvent } from "react";
import { ApiError, message } from "@/core/api/client";
import { Feedback, FormDialog, Pager, PanelHeading, RowMenu, StatusPill } from "@/shared/ui";
import type { MenuOption } from "@/shared/ui";
import { countLabel } from "@/shared/lib/formatters";
import { usePaged } from "@/shared/hooks/use-paged";
import { EvaluationFormDialog } from "./evaluation-form-dialog";
import { courseOptions, evaluationDate, PERIODS, sectionOptions, STATE_TONES } from "./lib/labels";
import {
  cambiarEstadoEvaluacion,
  eliminarEvaluacion,
  evaluationsPath,
} from "./services/evaluation-service";
import type { Evaluation, EvaluationState, OwnAssignment } from "./types";
import styles from "./evaluations.module.css";

type Operation = "PUBLICADA" | "CERRADA" | "ANULADA" | "ELIMINAR";
const OPERATIONS: Record<Operation, { title: string; detail: string; done: string; danger?: boolean }> = {
  PUBLICADA: {
    title: "Publicar evaluación",
    detail:
      "Los niveles quedarán disponibles para la consolidación. Después solo podrán cambiarse con una corrección y su motivo.",
    done: "Evaluación publicada.",
  },
  CERRADA: {
    title: "Cerrar evaluación",
    detail: "La evaluación quedará de solo consulta y ya no admitirá correcciones.",
    done: "Evaluación cerrada.",
  },
  ANULADA: {
    title: "Anular evaluación",
    detail: "La evaluación dejará de contar para la consolidación. No se puede deshacer.",
    done: "Evaluación anulada.",
    danger: true,
  },
  ELIMINAR: {
    title: "Eliminar evaluación",
    detail: "La evaluación dejará de mostrarse en tu listado.",
    done: "Evaluación eliminada.",
    danger: true,
  },
};
const STATES: EvaluationState[] = ["BORRADOR", "PUBLICADA", "CERRADA", "ANULADA"];
const stateName = (state: string) => state.charAt(0) + state.slice(1).toLowerCase();

/** Pestaña Mis evaluaciones: listado con filtros y ciclo de vida de cada evaluación. */
export function EvaluationList({
  canWrite,
  assignments,
  creating,
  onCloseCreate,
  onGrade,
}: {
  canWrite: boolean;
  assignments: OwnAssignment[];
  creating: boolean;
  onCloseCreate: () => void;
  onGrade: (evaluation: Evaluation) => void;
}) {
  const [periodo, setPeriodo] = useState("");
  const [idCurso, setIdCurso] = useState("");
  const [idSeccion, setIdSeccion] = useState("");
  const [estado, setEstado] = useState("");
  const [busqueda, setBusqueda] = useState("");
  const list = usePaged<Evaluation>(
    evaluationsPath({ periodo, idCurso, idSeccion, estado, busqueda: busqueda.trim() }),
  );
  const [editing, setEditing] = useState<Evaluation | null>(null);
  const [pending, setPending] = useState<{ evaluation: Evaluation; operation: Operation } | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");
  const total = list.data?.totalElements ?? 0;
  // Con la asignación retirada solo se consulta: el backend rechaza cualquier cambio.
  const ownsAssignment = (item: Evaluation) =>
    assignments.some((assignment) => assignment.id === item.idAsignacionCurso);
  const ask = (evaluation: Evaluation, operation: Operation) => {
    setError("");
    setSuccess("");
    setPending({ evaluation, operation });
  };
  async function confirm(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!pending) return;
    setBusy(true);
    setError("");
    try {
      if (pending.operation === "ELIMINAR") await eliminarEvaluacion(pending.evaluation.id);
      else await cambiarEstadoEvaluacion(pending.evaluation.id, pending.operation);
      setSuccess(OPERATIONS[pending.operation].done);
      setPending(null);
      list.refresh();
    } catch (e) {
      setError(
        e instanceof ApiError && e.code === "EVALUACION_INCOMPLETA"
          ? `${e.message}. Completa la planilla con Calificar.`
          : message(e),
      );
    } finally {
      setBusy(false);
    }
  }
  function menu(item: Evaluation): MenuOption[] {
    const options: MenuOption[] = [];
    if (item.estado === "BORRADOR") {
      options.push({ label: "Calificar", onSelect: () => onGrade(item) });
      options.push({
        label: "Editar",
        onSelect: () => {
          setSuccess("");
          setEditing(item);
        },
      });
    }
    if (item.estado === "PUBLICADA")
      options.push({ label: "Cerrar", onSelect: () => ask(item, "CERRADA") });
    if (item.estado === "BORRADOR" || item.estado === "PUBLICADA")
      options.push({ label: "Anular", danger: true, onSelect: () => ask(item, "ANULADA") });
    if (item.estado === "BORRADOR" || item.estado === "ANULADA")
      options.push({ label: "Eliminar", danger: true, onSelect: () => ask(item, "ELIMINAR") });
    return options;
  }
  function saved(evaluation: Evaluation, grade: boolean) {
    setEditing(null);
    onCloseCreate();
    if (grade) {
      onGrade(evaluation);
      return;
    }
    setSuccess("Borrador guardado.");
    list.refresh();
  }
  return (
    <>
      <div className={`panel ${styles.filters} ${styles.filtersFive}`}>
        <label className="field">
          Período
          <select value={periodo} onChange={(event) => setPeriodo(event.target.value)}>
            <option value="">Todos los períodos</option>
            {Object.entries(PERIODS).map(([value, label]) => (
              <option key={value} value={value}>
                {label}
              </option>
            ))}
          </select>
        </label>
        <label className="field">
          Curso
          <select
            value={idCurso}
            onChange={(event) => {
              setIdCurso(event.target.value);
              setIdSeccion("");
            }}
          >
            <option value="">Todos los cursos</option>
            {courseOptions(assignments).map(([id, label]) => (
              <option key={id} value={id}>
                {label}
              </option>
            ))}
          </select>
        </label>
        <label className="field">
          Sección
          <select value={idSeccion} onChange={(event) => setIdSeccion(event.target.value)}>
            <option value="">Todas las secciones</option>
            {sectionOptions(assignments, idCurso).map(([id, label]) => (
              <option key={id} value={id}>
                {label}
              </option>
            ))}
          </select>
        </label>
        <label className="field">
          Estado
          <select value={estado} onChange={(event) => setEstado(event.target.value)}>
            <option value="">Todos los estados</option>
            {STATES.map((value) => (
              <option key={value} value={value}>
                {stateName(value)}
              </option>
            ))}
          </select>
        </label>
        <label className="field">
          Buscar
          <input
            type="search"
            placeholder="Buscar evaluación..."
            value={busqueda}
            onChange={(event) => setBusqueda(event.target.value)}
          />
        </label>
      </div>
      <div className="panel">
        <PanelHeading
          title="Mis evaluaciones"
          summary={list.data ? countLabel(total, "evaluación", "evaluaciones") : "Evaluaciones de tus cursos"}
        />
        <Feedback error={list.error} success={success} />
        {list.loading ? (
          <p role="status">Cargando evaluaciones…</p>
        ) : list.data?.content.length ? (
          <div className="user-table-wrap">
            <table className="user-table">
              <thead>
                <tr>
                  <th>Evaluación</th>
                  <th>Curso</th>
                  <th>Sección</th>
                  <th>Fecha</th>
                  <th>Competencias</th>
                  <th>Estado</th>
                  <th>Acciones</th>
                </tr>
              </thead>
              <tbody>
                {list.data.content.map((item) => {
                  const writable = canWrite && ownsAssignment(item);
                  const options = writable ? menu(item) : [];
                  return (
                    <tr key={item.id}>
                      <td data-label="Evaluación">
                        <strong>{item.titulo}</strong>
                        <small>{PERIODS[item.periodoEvaluacion]}</small>
                      </td>
                      <td data-label="Curso">{item.curso}</td>
                      <td data-label="Sección">
                        <strong>{item.seccion}</strong>
                      </td>
                      <td data-label="Fecha">{evaluationDate(item.fechaEvaluacion)}</td>
                      <td data-label="Competencias">
                        <span className={styles.count}>{item.competencias.length}</span>{" "}
                        {item.competencias.length === 1 ? "competencia" : "competencias"}
                      </td>
                      <td data-label="Estado">
                        <StatusPill
                          state={item.estado}
                          label={stateName(item.estado)}
                          tone={STATE_TONES[item.estado]}
                        />
                      </td>
                      <td data-label="Acciones">
                        <div className={styles.actions}>
                          {writable && item.estado === "BORRADOR" ? (
                            <button type="button" onClick={() => ask(item, "PUBLICADA")}>
                              Publicar
                            </button>
                          ) : writable && item.estado === "PUBLICADA" ? (
                            <button type="button" onClick={() => onGrade(item)}>
                              Calificar
                            </button>
                          ) : item.estado !== "ANULADA" ? (
                            <button type="button" onClick={() => onGrade(item)}>
                              Ver
                            </button>
                          ) : null}
                          {options.length > 0 && <RowMenu itemName={item.titulo} options={options} />}
                        </div>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        ) : (
          !list.error && <p className="empty">No hay evaluaciones con estos filtros.</p>
        )}
        <Pager data={list.data} loading={list.loading} setPage={list.setPage} />
      </div>
      {(creating || editing) && (
        <EvaluationFormDialog
          key={editing?.id ?? "nueva"}
          evaluation={editing ?? undefined}
          assignments={assignments}
          onClose={() => {
            setEditing(null);
            onCloseCreate();
          }}
          onSaved={saved}
        />
      )}
      {pending && (
        <FormDialog
          title={OPERATIONS[pending.operation].title}
          description={pending.evaluation.titulo}
          submitLabel={OPERATIONS[pending.operation].title}
          danger={OPERATIONS[pending.operation].danger}
          busy={busy}
          error={error}
          onClose={() => setPending(null)}
          onSubmit={confirm}
        >
          <p className="full-field hint">{OPERATIONS[pending.operation].detail}</p>
        </FormDialog>
      )}
    </>
  );
}
