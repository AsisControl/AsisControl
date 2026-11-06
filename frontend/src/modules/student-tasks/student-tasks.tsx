"use client";

import { useEffect, useState } from "react";
import { message } from "@/core/api/client";
import { PageHeader } from "@/shared/ui/page";
import { Pager } from "@/shared/ui/pager";
import type { Page } from "@/shared/types/api";
import { listStudentTasks } from "./api";
import type { StudentTask } from "./types";
import { canSubmit, taskDate, taskStatus } from "./presentation";
import { TaskDialog } from "./task-dialog";
import styles from "./student-tasks.module.css";

export function StudentTasks() {
  const [query, setQuery] = useState({ search: "", status: "", page: 0, revision: 0 });
  const [data, setData] = useState<Page<StudentTask> | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [selectedTask, setSelectedTask] = useState<StudentTask | null>(null);

  useEffect(() => {
    let active = true;
    const timer = setTimeout(() => {
      listStudentTasks(query.page, query.search, query.status)
        .then((result) => { if (active) setData(result); })
        .catch((e) => { if (active) setError(message(e)); })
        .finally(() => { if (active) setLoading(false); });
    }, 250);
    return () => { active = false; clearTimeout(timer); };
  }, [query]);

  function changeQuery(values: Partial<typeof query>) {
    setLoading(true); setError(""); setData(null);
    setQuery((current) => ({ ...current, ...values }));
  }

  return (
    <section aria-label="Mis tareas">
      <PageHeader title="Mis Tareas" description="Tareas asignadas en tus cursos" />
      {notice && <p role="status">{notice}</p>}
      <div className={`panel ${styles.panel}`}>
        <div className={styles.filters}>
          <input type="search" aria-label="Buscar tarea o curso" placeholder="Buscar tarea o curso..."
            value={query.search} onChange={(e) => changeQuery({ search: e.target.value, page: 0 })} />
          <select aria-label="Filtrar por estado" value={query.status}
            onChange={(e) => changeQuery({ status: e.target.value, page: 0 })}>
            <option value="">Todas las entregas</option>
            <option value="pending">Entrega pendiente</option>
            <option value="submitted">Entregado</option>
          </select>
        </div>
        {loading && <p role="status">Cargando tareas...</p>}
        {error && <div role="alert"><p>{error}</p><button onClick={() => changeQuery({ revision: query.revision + 1 })}>Reintentar</button></div>}
        {!loading && !error && <>
          <div className={styles.tableWrap}>
            <table className={`user-table ${styles.table}`} aria-label="Tareas asignadas">
              <thead><tr>{["Curso", "Tarea", "Docente", "Fecha límite", "Estado", "Acción"].map((title) => <th key={title} scope="col">{title}</th>)}</tr></thead>
              <tbody>{data?.content.map((task) => (
                <tr key={task.tarea.id}>
                  <td data-label="Curso" className={styles.course}>{task.curso}</td>
                  <td data-label="Tarea" className={styles.taskTitle}>{task.tarea.nombre}<small className={styles.context}>{task.seccion}</small></td>
                  <td data-label="Docente" className={styles.teacher}>{task.docente}</td>
                  <td data-label="Fecha límite" className={styles.deadline}>{taskDate(task.tarea.fechaLimite)}</td>
                  <td data-label="Estado"><span className={`${styles.badge} ${task.entrega ? styles.submitted : styles.pending}`}>{taskStatus(task)}</span></td>
                  <td data-label="Acción"><button type="button" className={canSubmit(task) ? styles.deliverButton : undefined}
                    aria-label={`${canSubmit(task) ? "Entregar" : "Ver detalle de"} ${task.tarea.nombre}`}
                    onClick={() => setSelectedTask(task)}>{canSubmit(task) ? "Entregar" : "Ver detalle"}</button></td>
                </tr>
              ))}</tbody>
            </table>
          </div>
          {data?.content.length === 0 && <p className={styles.empty} role="status">No hay tareas disponibles con estos filtros. Solo se muestran tareas publicadas de tus matrículas activas.</p>}
          <Pager data={data} loading={loading} setPage={(page) => changeQuery({ page })} />
        </>}
      </div>
      {selectedTask && <TaskDialog key={selectedTask.tarea.id} task={selectedTask}
        onClose={() => setSelectedTask(null)} onSaved={() => {
          setSelectedTask(null); setNotice(selectedTask.entrega ? "Tu entrega se actualizó correctamente." : "Tu trabajo se entregó correctamente.");
          changeQuery({ revision: query.revision + 1 });
        }} />}
    </section>
  );
}
