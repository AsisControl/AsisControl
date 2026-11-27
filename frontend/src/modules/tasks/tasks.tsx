"use client";
import { useEffect, useState } from "react";
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { usePaged } from "@/shared/hooks/use-paged";
import { formatWeight } from "@/shared/lib/formatters";
import { Feedback, PageHeader, Pager, StatusPill, Loading } from "@/shared/ui";
import type { StatusTone } from "@/shared/ui";
import { listarAsignacionesPropias, obtenerContextoAsignacion, TAREAS_PATH } from "./services/tarea-service";
import type { AsignacionCursoResponse, EstadoPublicacionTarea, TareaResponse } from "./types";
import styles from "./tasks.module.css";
import { TaskFormDialog } from "./task-form-dialog";
import { TaskDeleteDialog } from "./task-delete-dialog";
import { PencilIcon } from "@/modules/academic";
import { TaskTransitionDialog, type TaskTransition } from "./task-transition-dialog";
import { message } from "./task-error-copy";
import { useTaskDeliveryCounts } from "./use-task-delivery-counts";

const tones: Record<EstadoPublicacionTarea, StatusTone> = {
  BORRADOR: "info",
  PUBLICADA: "success",
  CERRADA: "warning",
  ARCHIVADA: "neutral",
};

/** LocalDateTime no incluye zona: mostramos sus componentes sin desplazar la hora. */
function fechaLimiteLabel(value: string) {
  const [date, time] = value.split("T");
  const [year, month, day] = date.split("-");
  return { date: `${day}/${month}/${year}`, time: time?.slice(0, 5) };
}

export function Tasks({ canWrite = false, canUpload = false }: { canWrite?: boolean; canUpload?: boolean }) {
  // El backend aplica el ámbito docente; no inferimos docenteId desde la sesión.
  const params = useSearchParams();
  const [search, setSearch] = useState(params?.get("buscar") ?? "");
  const [estado, setEstado] = useState(params?.get("estado") ?? "");
  const [curso, setCurso] = useState(params?.get("cursoId") ?? "");
  const [orden, setOrden] = useState(params?.get("orden") ?? "recientes");
  const [courseOptions, setCourseOptions] = useState<AsignacionCursoResponse[]>([]);
  useEffect(() => {
    let active = true;
    listarAsignacionesPropias().then(items => {
      if (active) setCourseOptions([...new Map(items.map(item => [item.cursoId, item])).values()]);
    }).catch(() => undefined);
    return () => { active = false; };
  }, []);
  const filters = new URLSearchParams({ orden });
  if (estado) filters.set("estado", estado);
  if (curso) filters.set("cursoId", curso);
  const tasks = usePaged<TareaResponse>(TAREAS_PATH, search.trim(), filters.toString());
  const returnFilters = new URLSearchParams(filters);
  if (search.trim()) returnFilters.set("buscar", search.trim());
  if (orden === "recientes") returnFilters.delete("orden");
  const returnQuery = returnFilters.size ? `?${returnFilters}` : "";
  const [summaryRevision, setSummaryRevision] = useState(0);
  const counts = useTaskDeliveryCounts(tasks.data, tasks.loading, tasks.error, summaryRevision);
  function refresh() {
    setSummaryRevision((value) => value + 1);
    tasks.refresh();
  }
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    // Solo presentación: no cambia estados ni determina si el servidor permite cerrar.
    const update = () => setNow(Date.now());
    const timer = setInterval(update, 30000);
    window.addEventListener("focus", update);
    return () => { clearInterval(timer); window.removeEventListener("focus", update); };
  }, []);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [creating, setCreating] = useState(false);
  const evaluation = Number(params?.get("evaluacion"));
  const assignment = Number(params?.get("asignacion"));
  const [dismissedEvaluation, setDismissedEvaluation] = useState<number>();
  const preselection = canWrite && evaluation !== dismissedEvaluation
    && Number.isSafeInteger(evaluation) && evaluation > 0 && Number.isSafeInteger(assignment) && assignment > 0
    ? { evaluation, assignment } : null;
  function closeCreate() {
    setCreating(false);
    if (preselection) {
      const url = new URL(window.location.href);
      url.searchParams.delete("evaluacion");
      url.searchParams.delete("asignacion");
      window.history.replaceState(window.history.state, "", url);
      setDismissedEvaluation(preselection.evaluation);
    }
  }
  const [deleting, setDeleting] = useState<TareaResponse | null>(null);
  const [success, setSuccess] = useState("");
  const [transition, setTransition] = useState<{ task: TareaResponse; action: TaskTransition } | null>(null);
  const [contexts, setContexts] = useState<{
    source: typeof tasks.data;
    values: Map<number, AsignacionCursoResponse | null>;
  } | null>(null);

  useEffect(() => {
    const source = tasks.data;
    if (!source || tasks.loading || tasks.error) return;
    let active = true;
    const ids = [...new Set(source.content.map((task) => task.asignacionCursoId))];
    // No se consulta el catálogo global: solo referencias de la página autorizada.
    Promise.all(ids.map(async (id) => {
      try {
        const value = await obtenerContextoAsignacion(id);
        return [id, value.id === id ? value : null] as const;
      } catch {
        return [id, null] as const;
      }
    })).then((entries) => {
      if (active) setContexts({ source, values: new Map(entries) });
    });
    return () => { active = false; };
  }, [tasks.data, tasks.loading, tasks.error]);

  const currentContexts = contexts?.source === tasks.data ? contexts.values : null;
  const contextFailed = currentContexts && [...currentContexts.values()].some((value) => !value);
  return (
    <div className={styles.tasks}>
      <PageHeader title="Mis tareas" description="Tareas y actividades asignadas a tus cursos."
        actions={canWrite && <button type="button" className="primary-action" onClick={() => { setSuccess(""); setCreating(true); }}>Nueva tarea</button>} />
      {success && <div className={styles.successNotice}>
        <Feedback success={success} />
        <button type="button" aria-label="Cerrar mensaje de éxito" onClick={() => setSuccess("")}>Cerrar</button>
      </div>}
      <section className={`panel ${styles.card}`} aria-label="Listado de tareas">
        <div className={styles.taskFilters}>
          <label className="field">Buscar
            <input type="search" placeholder="Buscar tarea..." value={search} onChange={event => setSearch(event.target.value)} />
          </label>
          <label className="field">Estado
            <select value={estado} onChange={event => setEstado(event.target.value)}>
              <option value="">Todos</option>
              <option value="BORRADOR">Borrador</option>
              <option value="PUBLICADA">Publicada</option>
              <option value="CERRADA">Cerrada</option>
              <option value="ARCHIVADA">Archivada</option>
            </select>
          </label>
          <label className="field">Curso
            <select value={curso} onChange={event => setCurso(event.target.value)}>
              <option value="">Todos los cursos</option>
              {courseOptions.map(item => <option key={item.cursoId} value={item.cursoId}>{item.curso}</option>)}
            </select>
          </label>
          <label className="field">Ordenar por
            <select value={orden} onChange={event => setOrden(event.target.value)}>
              <option value="recientes">Más recientes</option>
              <option value="antiguas">Más antiguas</option>
              <option value="titulo">Nombre: A–Z</option>
              <option value="titulo-desc">Nombre: Z–A</option>
            </select>
          </label>
          <div className={styles.taskFilterActions}>
            <button type="button" className="secondary-action" onClick={() => {
              setSearch(""); setEstado(""); setCurso(""); setOrden("recientes"); tasks.setPage(0);
            }}>Limpiar filtros</button>
            <button className="secondary-action" type="button" disabled={tasks.loading} onClick={() => { setSuccess(""); refresh(); }}>Actualizar</button>
          </div>
        </div>
        <Feedback error={tasks.error ? message(tasks.error, "No se pudieron cargar las tareas. Inténtalo de nuevo.") : ""} />
        {contextFailed && !tasks.loading && !tasks.error && (
          <p className="hint">No se pudo cargar el curso o la sección de algunas tareas. Inténtalo de nuevo con Actualizar.</p>
        )}
        {tasks.loading ? <Loading>Cargando tareas…</Loading> : tasks.error ? null : tasks.data?.content.length ? (
          <div className={styles.tableWrap}>
            <table className={`user-table ${styles.table} ${styles.withActions}`}>
              <caption className={styles.srOnly}>Tareas asignadas a tus cursos y secciones</caption>
              <thead><tr>
                <th scope="col">Tarea</th>
                <th scope="col" className={styles.taskState}>Estado</th><th scope="col">Vencimiento</th>
                <th scope="col" className={styles.taskWeight}>Peso</th>
                <th className={styles.deliveriesColumn} scope="col">Entregas</th>
                <th className={styles.actionsColumn} scope="col">Acciones</th>
              </tr></thead>
              <tbody>{tasks.data.content.map((task) => {
                const context = currentContexts?.get(task.asignacionCursoId);
                const summary = counts?.get(task.id);
                // US-58: retirado del curso conserva la consulta, pero el backend rechaza cambios.
                const retired = context?.activo === false;
                const rowWrite = canWrite && !retired;
                const deadline = fechaLimiteLabel(task.fechaLimite);
                return <tr key={task.id}>
                  <td data-label="Tarea"><strong>{task.nombre}</strong>
                    <small className={styles.taskContext}>{context
                    ? `${context.curso} · ${context.seccion} · ${context.anioAcademico}`
                    : currentContexts ? "Curso/sección no disponible" : "Cargando curso/sección…"}</small>
                    {retired && <small className={styles.deadlineNotice}>Ya no estás asignado a este curso: solo consulta</small>}</td>
                  <td className={styles.taskState} data-label="Estado"><StatusPill state={task.estado} tone={tones[task.estado]} />
                    {task.estado === "PUBLICADA" && new Date(task.fechaLimite).getTime() < now && (
                      <small className={styles.deadlineNotice}>Plazo vencido</small>
                    )}
                  </td>
                  <td className={styles.taskDeadline} data-label="Vencimiento"><time dateTime={task.fechaLimite}>{deadline.date}<small>{deadline.time}</small></time></td>
                  <td className={styles.taskWeight} data-label="Peso">{!task.evaluacionAcademica?.cuentaParaResultadoAcademico
                    ? "Sin peso" : task.evaluacionAcademica.pesoComponente == null ? "Peso pendiente"
                      : formatWeight(task.evaluacionAcademica.pesoComponente)}</td>
                  <td data-label="Entregas">{summary
                    ? <span>{summary.entregados} de {summary.totalAlumnos}<span className={styles.srOnly}> estudiantes entregaron</span></span>
                    : <span className={styles.summaryUnavailable}><span aria-hidden="true">—</span><span className={styles.srOnly}>Resumen de entregas no disponible</span></span>}</td>
                  <td data-label="Acciones"><div className={styles.rowActions}>
                    {rowWrite && (task.estado === "BORRADOR" || task.estado === "PUBLICADA") && <button type="button" className={styles.iconButton} title="Editar tarea" aria-label={`Editar ${task.nombre}`} onClick={() => { setSuccess(""); setEditingId(task.id); }}><PencilIcon /></button>}
                    {rowWrite && task.estado !== "ARCHIVADA" && <button type="button" className={styles.editButton}
                      aria-label={`${task.estado === "BORRADOR" ? task.evaluacionAcademica ? "Activar" : "Publicar" : task.estado === "PUBLICADA" ? "Cerrar" : "Archivar"} ${task.nombre}`}
                      onClick={() => { setSuccess(""); setTransition({ task, action: task.estado === "BORRADOR" ? "publicar" : task.estado === "PUBLICADA" ? "cerrar" : "archivar" }); }}>
                      {task.estado === "BORRADOR" ? task.evaluacionAcademica ? "Activar" : "Publicar" : task.estado === "PUBLICADA" ? "Cerrar" : "Archivar"}
                    </button>}
                    <span className={styles.actionPair}>
                      {task.estado !== "BORRADOR" && <Link className={styles.editButton} aria-label={`Entregas de ${task.nombre}`}
                        href={`/tareas/${task.id}/entregas${returnQuery}`} onClick={() => setSuccess("")}>Entregas</Link>}
                      {rowWrite && <button type="button" className={`${styles.editButton} ${styles.dangerButton}`}
                        aria-label={`Eliminar ${task.nombre}`} onClick={() => { setSuccess(""); setDeleting(task); }}>Eliminar</button>}
                    </span>
                  </div></td>
                </tr>;
              })}</tbody>
            </table>
          </div>
        ) : <p className="empty">{search || estado || curso ? "No hay tareas que coincidan con estos filtros." : "Aún no tienes tareas registradas."}</p>}
        {!tasks.error && <Pager data={tasks.data} loading={tasks.loading} setPage={(page) => { setSuccess(""); tasks.setPage(page); }} />}
      </section>
      {canWrite && editingId !== null && <TaskFormDialog key={editingId} taskId={editingId} canUpload={canUpload}
        onClose={() => setEditingId(null)} onSaved={(saved) => {
          setEditingId(null);
          setSuccess(saved.estado === "PUBLICADA" ? "Tarea actualizada." : "Borrador actualizado.");
          refresh();
        }} />}
      {canWrite && (creating || preselection) && <TaskFormDialog canUpload={canUpload}
        {...(preselection ? { asignacionCursoId: preselection.assignment, preselectedEvaluationId: preselection.evaluation } : {})}
        onClose={closeCreate} onSaved={() => {
        closeCreate();
        setSuccess("Borrador creado.");
        refresh();
      }} />}
      {canWrite && deleting && <TaskDeleteDialog key={deleting.id} task={deleting} onClose={() => setDeleting(null)} onSaved={() => {
        setDeleting(null);
        setSuccess("Tarea eliminada.");
        refresh();
      }} />}
      {canWrite && transition && <TaskTransitionDialog key={`${transition.task.id}-${transition.action}`} {...transition}
        onClose={() => setTransition(null)} onRefresh={() => { setTransition(null); setSuccess(""); refresh(); }}
        onSaved={() => {
          setTransition(null);
          setSuccess(transition.action === "publicar" ? "Tarea publicada." : transition.action === "cerrar" ? "Tarea cerrada." : "Tarea archivada.");
          refresh();
        }} />}
    </div>
  );
}
