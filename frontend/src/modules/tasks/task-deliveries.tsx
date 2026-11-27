"use client";
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { useEffect, useState } from "react";
import { api, ApiError } from "@/core/api/client";
import { message } from "./task-error-copy";
import { usePaged } from "@/shared/hooks/use-paged";
import { formatWeight } from "@/shared/lib/formatters";
import { Feedback, FormDialog, PageHeader, Pager, StatusPill, type StatusTone, Loading } from "@/shared/ui";
import { obtenerContextoAsignacion, obtenerTarea, listMissingDeliveries, type MissingDeliveries } from "./services/tarea-service";
import { entregasPath } from "./services/entrega-service";
import { useDeliveryStudents } from "./use-delivery-students";
import { DeliveryGradeDialog } from "./delivery-grade-dialog";
import { TaskDeliveryFiles } from "./task-delivery-files";
import { MissingDeliveryGrades } from "./missing-delivery-grades";
import { TaskRoster } from "./task-roster";
import { TaskDeliveryReview } from "./task-delivery-review";
import type { EntregaTareaResponse, EstadoEntrega, SeguimientoAlumno, TareaResponse } from "./types";
import styles from "./tasks.module.css";

const tones: Record<EstadoEntrega, StatusTone> = {
  PENDIENTE: "neutral", ENTREGADO: "info", ENTREGADO_TARDE: "warning", CALIFICADO: "success", ANULADO: "danger",
};

/** Componentes de LocalDateTime, sin conversión de zona ni cálculo histórico. */
function deliveryDate(value: string) {
  const [date, time] = value.split("T");
  const [year, month, day] = date.split("-");
  return `${day}/${month}/${year} ${time?.slice(0, 8) ?? ""}`.trim();
}

export function TaskDeliveries({ taskId, canWrite }: { taskId: number; canWrite: boolean }) {
  const params = useSearchParams();
  const returnFilters = new URLSearchParams();
  for (const name of ["buscar", "estado", "cursoId", "orden"]) {
    const value = params?.get(name);
    if (value) returnFilters.set(name, value);
  }
  const listHref = returnFilters.size ? `/tareas?${returnFilters}` : "/tareas";
  const [task, setTask] = useState<TareaResponse | null>(null);
  const [checking, setChecking] = useState(true);
  const [error, setError] = useState("");
  const [reload, setReload] = useState(0);
  const [retired, setRetired] = useState(false);
  const [contextLabel, setContextLabel] = useState("");
  useEffect(() => {
    let active = true;
    obtenerTarea(taskId).then((value) => {
      if (!active) return;
      setTask(value);
      // US-58: sin asignación activa el backend rechaza calificar; si no se resuelve, decide el servidor.
      obtenerContextoAsignacion(value.asignacionCursoId)
        .then((context) => { if (active) { setRetired(context.activo === false); setContextLabel(`${context.curso} · ${context.seccion}`); } })
        .catch(() => undefined);
    }).catch((e) => { if (active) setError(message(e)); })
      .finally(() => { if (active) setChecking(false); });
    return () => { active = false; };
  }, [taskId, reload]);
  function refreshTask() {
    setChecking(true);
    setError("");
    setReload((value) => value + 1);
  }
  return <div className={styles.tasks}>
    <PageHeader title="Seguimiento de entregas" description={task ? [task.nombre, contextLabel].filter(Boolean).join(" · ") : "Seguimiento de tarea"}
      actions={<Link className={styles.backLink} href={listHref}><span aria-hidden="true">← </span>Volver a tareas</Link>} />
    {!task ? <section className={`panel ${styles.card}`}>
      <Feedback error={error} />
      {checking ? <Loading skeleton="detail" rows={4}>Cargando tarea…</Loading> : <button type="button" onClick={refreshTask}>Reintentar carga</button>}
    </section> : task.estado === "BORRADOR" ? <section className={`panel ${styles.card}`}>
      <Feedback error={error} />
      <p>El seguimiento está disponible para tareas publicadas, cerradas o archivadas.</p>
      <button type="button" disabled={checking} onClick={refreshTask}>Actualizar</button>
    </section> : <DeliveryPanel key={task.id} task={task} canWrite={canWrite && !retired} retired={retired} checking={checking} taskError={error} refreshTask={refreshTask} />}
  </div>;
}

function DeliveryPanel({ task, canWrite, retired, checking, taskError, refreshTask }: {
  task: TareaResponse; canWrite: boolean; retired: boolean; checking: boolean; taskError: string; refreshTask: () => void;
}) {
  const deliveries = usePaged<EntregaTareaResponse>(entregasPath(task.id));
  const students = useDeliveryStudents(deliveries.error ? null : deliveries.data);
  const [grading, setGrading] = useState<{ id: number; student: string } | null>(null);
  const [review, setReview] = useState<SeguimientoAlumno | null>(null);
  const [success, setSuccess] = useState("");
  const [missing, setMissing] = useState<MissingDeliveries | null>(null);
  const [missingError, setMissingError] = useState("");
  const [showMissing, setShowMissing] = useState(false);
  const [rosterReload, setRosterReload] = useState(0);
  const [confirmPublication, setConfirmPublication] = useState(false);
  const [publishing, setPublishing] = useState(false);
  const [publicationError, setPublicationError] = useState("");
  const canGrade = canWrite && ["PUBLICADA", "CERRADA"].includes(task.estado) && !checking && !taskError;
  function refresh() {
    setGrading(null);
    setSuccess("");
    refreshTask();
    deliveries.refresh();
    setRosterReload((value) => value + 1);
  }
  async function publishGrades() {
    setPublishing(true);
    setPublicationError("");
    try {
      await api(`/api/tareas/${task.id}/publicar-calificaciones`, { method: "POST" });
      setConfirmPublication(false);
      refresh();
      setSuccess("Calificaciones publicadas.");
    } catch (e) {
      setPublicationError(e instanceof ApiError && e.code === "EVALUACION_INCOMPLETA"
        ? `${e.message}. Revisa las entregas y registra los niveles pendientes. Si hay estudiantes sin entrega, puedes calificarlos tras cerrar la recepción.`
        : message(e));
    } finally {
      setPublishing(false);
    }
  }
  const canGradeMissing = canGrade && task.estado === "CERRADA" && (!task.evaluacionAcademica || task.evaluacionAcademica.estado === "BORRADOR");
  useEffect(() => {
    if (!canGradeMissing) return;
    let active = true;
    listMissingDeliveries(task.id).then(data => {
      if (active) { setMissing(data); setMissingError(""); }
    }, failure => {
      if (active) { setMissing(null); setMissingError(message(failure, "No se pudo consultar la calificación sin entrega.")); }
    });
    return () => { active = false; };
  }, [canGradeMissing, task.id, rosterReload]);

  const error = taskError || (deliveries.error ? message(deliveries.error, "No se pudieron cargar las entregas. Inténtalo de nuevo.") : "");
  return <>
    {confirmPublication && <FormDialog title="Publicar calificaciones" submitLabel="Publicar"
      busy={publishing} error={publicationError} onClose={() => setConfirmPublication(false)}
      onSubmit={event => { event.preventDefault(); void publishGrades(); }}>
      <p>Se publicarán los resultados de la evaluación vinculada.</p>
    </FormDialog>}
    {canGradeMissing && showMissing && missing && <MissingDeliveryGrades taskId={task.id} data={missing}
      onClose={() => setShowMissing(false)} onSaved={count => {
        setShowMissing(false);
        setMissing(null);
        refresh();
        setSuccess(`Se registraron ${count} calificaciones.`);
      }} />}
    {success && <div className={styles.successNotice}>
      <Feedback success={success} />
      <button type="button" aria-label="Cerrar mensaje de éxito" onClick={() => setSuccess("")}>Cerrar</button>
    </div>}
    <section className={`panel ${styles.card}`} aria-label="Listado de entregas">
      <div className={styles.toolbar}>
        <StatusPill state={task.estado} label={task.estado === "CERRADA" ? "Recepción cerrada" : task.estado === "ARCHIVADA" ? "Archivada" : "Publicada"} tone={task.estado === "PUBLICADA" ? "success" : "neutral"} />
        <div className={styles.deliveryActions}>
        <button type="button" className="secondary-action" disabled={checking || deliveries.loading} onClick={refresh}>Actualizar</button>
        {task.evaluacionAcademica && <Link className="secondary-action" href={`/evaluaciones/registro-de-calificaciones/${task.evaluacionAcademica.id}`}>Ver resultados</Link>}
        {canGrade && task.evaluacionAcademica?.cuentaParaResultadoAcademico && task.evaluacionAcademica.estado === "BORRADOR" &&
          <button type="button" className="primary-action" onClick={() => { setPublicationError(""); setConfirmPublication(true); }}>Publicar calificaciones</button>}
        {canGradeMissing && !!missing?.alumnos.length && <button type="button" className="secondary-action" onClick={() => setShowMissing(true)}>Calificar sin entrega</button>}
        </div>
      </div>
      {task.evaluacionAcademica ? <p className="hint">
        {task.evaluacionAcademica.estado === "BORRADOR" ? "Calificaciones en borrador"
          : task.evaluacionAcademica.estado === "ANULADA" ? "Evaluación anulada" : "Calificaciones publicadas"} ·{" "}
        {!task.evaluacionAcademica.cuentaParaResultadoAcademico ? "Sin peso"
          : task.evaluacionAcademica.pesoComponente == null ? "Peso pendiente"
          : `Peso del componente: ${formatWeight(task.evaluacionAcademica.pesoComponente)}`}
      </p> : <p className="hint">Peso en el resultado académico: Sin peso</p>}
      {task.estado === "ARCHIVADA" && <p className="hint">Tarea archivada: consulta de solo lectura.</p>}
      {retired && task.estado !== "ARCHIVADA" && <p className="hint">Ya no estás asignado a este curso y sección: consulta de solo lectura.</p>}
      <Feedback error={error || (canGradeMissing ? missingError : "")} />
      <TaskRoster taskId={task.id} reload={rosterReload} maximo={task.puntajeMaximo} canGradePractice={canGrade && !task.evaluacionAcademica}
        canGradeWithoutDelivery={canGradeMissing}
        onReview={setReview} onGrade={student => {
          if (student.entregaId != null) setGrading({ id: student.entregaId, student: student.nombreCompleto });
        }} fallback={<>
      {checking || deliveries.loading ? <Loading>Cargando entregas…</Loading> : error ? null : deliveries.data?.content.length ? (
        <div className={styles.tableWrap}>
          <table className={`user-table ${styles.table} ${styles.deliveryTable}`}>
            <caption className={styles.srOnly}>Entregas recibidas de {task.nombre}</caption>
            <thead><tr>
              <th scope="col">Estudiante</th><th scope="col">Fecha y hora</th><th scope="col">Estado</th>
              <th scope="col">Entrega</th><th scope="col">Nota</th><th scope="col">Comentario</th>
              {canGrade && <th scope="col">Acciones</th>}
            </tr></thead>
            <tbody>{deliveries.data.content.map((delivery) => {
              const student = students?.get(delivery.alumnoId);
              const name = student?.persona.nombreCompleto || "Estudiante no disponible";
              return <tr key={delivery.id}>
                <td data-label="Estudiante"><strong>{name}</strong>{student?.codigoAlumno && <small>{student.codigoAlumno}</small>}</td>
                <td data-label="Fecha y hora"><time dateTime={delivery.fechaHoraEntrega}>{deliveryDate(delivery.fechaHoraEntrega)}</time></td>
                <td data-label="Estado"><StatusPill state={delivery.estado} label={delivery.estado === "ENTREGADO_TARDE" ? "Entregado tarde" : undefined} tone={tones[delivery.estado]} /></td>
                <td data-label="Entrega">{delivery.respuesta && <p className={styles.deliveryComment}>{delivery.respuesta}</p>}<TaskDeliveryFiles files={delivery.archivos} /></td>
                <td data-label="Nota">{task.evaluacionAcademica ? <Link href={`/evaluaciones/registro-de-calificaciones/${task.evaluacionAcademica.id}#alumno-${delivery.alumnoId}`}>Consultar resultado</Link>
                  : delivery.nota === null ? "Sin calificar" : `${delivery.nota} / ${task.puntajeMaximo}`}</td>
                <td className={styles.deliveryComment} data-label="Comentario">{delivery.comentario || "Sin comentario"}</td>
                {canGrade && <td data-label="Acciones">{delivery.estado !== "ANULADO" && !task.evaluacionAcademica && <button className={styles.editButton} type="button"
                  aria-label={`${delivery.nota === null ? "Calificar" : "Modificar calificación de"} ${name}`}
                  onClick={() => { setSuccess(""); setGrading({ id: delivery.id, student: name }); }}>
                  {delivery.nota === null ? "Calificar" : "Modificar nota"}
                </button>}</td>}
              </tr>;
            })}</tbody>
          </table>
        </div>
      ) : <p className="empty">Esta tarea aún no tiene entregas recibidas.</p>}
      {!error && <Pager data={deliveries.data} loading={checking || deliveries.loading} setPage={(page) => { setSuccess(""); deliveries.setPage(page); }} />}
      </>} />
    </section>
    {review && <TaskDeliveryReview key={review.alumnoId} task={task} student={review} canGrade={canGrade}
      onClose={() => setReview(null)} onSaved={() => {
        setReview(null);
        setSuccess("Calificación guardada.");
        setRosterReload(value => value + 1);
      }} />}
    {canGrade && grading && <DeliveryGradeDialog key={grading.id} taskId={task.id} deliveryId={grading.id} student={grading.student}
      onClose={() => setGrading(null)} onRefresh={refresh} onSaved={onlyFeedback => {
        setGrading(null);
        setSuccess(onlyFeedback ? "Devolución guardada." : "Calificación guardada.");
        refreshTask();
        deliveries.refresh();
        setRosterReload((value) => value + 1);
      }} />}
  </>;
}
