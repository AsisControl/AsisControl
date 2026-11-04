"use client";
import Link from "next/link";
import { useEffect, useState } from "react";
import { message } from "@/core/api/client";
import { usePaged } from "@/shared/hooks/use-paged";
import { Feedback, PageHeader, Pager, StatusPill, type StatusTone } from "@/shared/ui";
import { obtenerContextoAsignacion, obtenerTarea } from "./services/tarea-service";
import { entregasPath } from "./services/entrega-service";
import { useDeliveryStudents } from "./use-delivery-students";
import { DeliveryGradeDialog } from "./delivery-grade-dialog";
import { TaskDeliveryFiles } from "./task-delivery-files";
import { TaskRoster } from "./task-roster";
import type { EntregaTareaResponse, EstadoEntrega, TareaResponse } from "./types";
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
  const [task, setTask] = useState<TareaResponse | null>(null);
  const [checking, setChecking] = useState(true);
  const [error, setError] = useState("");
  const [reload, setReload] = useState(0);
  const [retired, setRetired] = useState(false);
  useEffect(() => {
    let active = true;
    obtenerTarea(taskId).then((value) => {
      if (!active) return;
      setTask(value);
      // US-58: sin asignación activa el backend rechaza calificar; si no se resuelve, decide el servidor.
      obtenerContextoAsignacion(value.asignacionCursoId)
        .then((context) => { if (active) setRetired(context.activo === false); })
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
    <PageHeader title="Entregas recibidas" description={task?.nombre ?? "Seguimiento de tarea"}
      actions={<Link className={styles.backLink} href="/tareas"><span aria-hidden="true">← </span>Volver a tareas</Link>} />
    {!task ? <section className={`panel ${styles.card}`}>
      <Feedback error={error} />
      {checking ? <p role="status">Cargando tarea…</p> : <button type="button" onClick={refreshTask}>Reintentar carga</button>}
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
  const [success, setSuccess] = useState("");
  const [rosterReload, setRosterReload] = useState(0);
  const canGrade = canWrite && ["PUBLICADA", "CERRADA"].includes(task.estado) && !checking && !taskError;
  function refresh() {
    setGrading(null);
    setSuccess("");
    refreshTask();
    deliveries.refresh();
    setRosterReload((value) => value + 1);
  }
  const error = taskError || deliveries.error;
  return <>
    {success && <div className={styles.successNotice}>
      <Feedback success={success} />
      <button type="button" aria-label="Cerrar mensaje de éxito" onClick={() => setSuccess("")}>Cerrar</button>
    </div>}
    <section className={`panel ${styles.card}`} aria-label="Listado de entregas">
      <div className={styles.toolbar}>
        <button type="button" className={styles.refresh} disabled={checking || deliveries.loading} onClick={refresh}>Actualizar</button>
      </div>
      {task.estado === "ARCHIVADA" && <p className="hint">Tarea archivada: consulta de solo lectura.</p>}
      {retired && task.estado !== "ARCHIVADA" && <p className="hint">Ya no estás asignado a este curso y sección: consulta de solo lectura.</p>}
      <TaskRoster taskId={task.id} reload={rosterReload} />
      <Feedback error={error} />
      {checking || deliveries.loading ? <p role="status">Cargando entregas…</p> : error ? null : deliveries.data?.content.length ? (
        <div className={styles.tableWrap}>
          <table className={`user-table ${styles.table} ${styles.deliveryTable}`}>
            <caption className={styles.srOnly}>Entregas recibidas de {task.nombre}</caption>
            <thead><tr>
              <th scope="col">Estudiante</th><th scope="col">Fecha y hora</th><th scope="col">Estado</th>
              <th scope="col">Adjuntos</th><th scope="col">Nota</th><th scope="col">Comentario</th>
              {canGrade && <th scope="col">Acciones</th>}
            </tr></thead>
            <tbody>{deliveries.data.content.map((delivery) => {
              const student = students?.get(delivery.alumnoId);
              const name = student?.persona.nombreCompleto || `Alumno #${delivery.alumnoId}`;
              return <tr key={delivery.id}>
                <td data-label="Estudiante"><strong>{name}</strong>{student?.codigoAlumno && <small>{student.codigoAlumno}</small>}</td>
                <td data-label="Fecha y hora"><time dateTime={delivery.fechaHoraEntrega}>{deliveryDate(delivery.fechaHoraEntrega)}</time></td>
                <td data-label="Estado"><StatusPill state={delivery.estado} label={delivery.estado === "ENTREGADO_TARDE" ? "Entregado tarde" : undefined} tone={tones[delivery.estado]} /></td>
                <td data-label="Adjuntos"><TaskDeliveryFiles files={delivery.archivos} /></td>
                <td data-label="Nota">{delivery.nota === null ? "Sin calificar" : `${delivery.nota} / ${task.puntajeMaximo}`}</td>
                <td className={styles.deliveryComment} data-label="Comentario">{delivery.comentario || "Sin comentario"}</td>
                {canGrade && <td data-label="Acciones">{delivery.estado !== "ANULADO" && <button className={styles.editButton} type="button"
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
    </section>
    {canGrade && grading && <DeliveryGradeDialog key={grading.id} taskId={task.id} deliveryId={grading.id} student={grading.student}
      onClose={() => setGrading(null)} onRefresh={refresh} onSaved={() => {
        setGrading(null);
        setSuccess("Calificación guardada.");
        refreshTask();
        deliveries.refresh();
        setRosterReload((value) => value + 1);
      }} />}
  </>;
}
