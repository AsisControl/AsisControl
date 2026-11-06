"use client";
import { useEffect, useState } from "react";
import { message } from "./task-error-copy";
import { obtenerSeguimiento } from "./services/entrega-service";
import type { SeguimientoTareaResponse } from "./types";
import styles from "./tasks.module.css";

/**
 * Padrón de la sección (GET /api/tareas/{id}/seguimiento): cuántos entregaron y quiénes no.
 * `reload` cambia cuando el panel se actualiza o se guarda una calificación.
 */
export function TaskRoster({ taskId, reload }: { taskId: number; reload: number }) {
  const [data, setData] = useState<{ key: string; value: SeguimientoTareaResponse | null; error: string } | null>(null);
  const key = `${taskId}-${reload}`;
  useEffect(() => {
    let active = true;
    obtenerSeguimiento(taskId)
      .then((value) => { if (active) setData({ key, value, error: "" }); })
      .catch((e) => { if (active) setData({ key, value: null, error: message(e) }); });
    return () => { active = false; };
  }, [taskId, key]);

  // Al recargar se conserva el padrón anterior hasta que llega el nuevo, sin parpadeo.
  const current = data;
  if (!current) return <p className="hint" role="status">Cargando el padrón de la sección…</p>;
  if (!current.value) return <p className="hint">No se pudo cargar el padrón de la sección: {current.error}</p>;
  const { totalAlumnos, entregados, calificados, alumnos } = current.value;
  const missing = alumnos.filter((alumno) => alumno.matriculaActiva && alumno.entregaId === null);
  return <div className={styles.roster} role="region" aria-label="Padrón de la sección">
    <p className={styles.rosterSummary}>
      <strong>{entregados} de {totalAlumnos} estudiantes entregaron</strong>
      <span> · {calificados} {calificados === 1 ? "calificado" : "calificados"}</span>
    </p>
    {missing.length > 0 && <details>
      <summary>Sin entrega ({missing.length})</summary>
      <ul className={styles.rosterList}>
        {missing.map((alumno) => <li key={alumno.alumnoId}>
          {alumno.nombreCompleto}{alumno.codigoAlumno && <small> · {alumno.codigoAlumno}</small>}
        </li>)}
      </ul>
    </details>}
  </div>;
}
