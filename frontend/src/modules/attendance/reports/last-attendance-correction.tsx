"use client";
import { useEffect, useState } from "react";
import { message } from "@/core/api/client";
import { Feedback } from "@/shared/ui";
import { fechaHora } from "../lib";
import { listarCorrecciones, obtenerSesion } from "../services/asistencia-service";
import type { CorreccionAsistencia } from "../types";
import styles from "./attendance-reports.module.css";

export function LastAttendanceCorrection({ sessionId, attendanceId }: { sessionId: number; attendanceId: number }) {
  const [last, setLast] = useState<CorreccionAsistencia | null>(null);
  const [error, setError] = useState("");
  useEffect(() => {
    let alive = true;
    Promise.all([obtenerSesion(sessionId), listarCorrecciones(sessionId)])
      .then(([session, corrections]) => {
        const student = session.id === sessionId
          ? session.alumnos.find(item => item.asistenciaId === attendanceId) : undefined;
        if (!student) throw new Error("El registro cambió. Actualiza el reporte para consultar su corrección.");
        const correction = corrections
          .filter(item => item.cambios.some(change => change.alumnoId === student.alumnoId))
          .sort((a, b) => b.fechaHora.localeCompare(a.fechaHora) || b.id - a.id)[0];
        if (alive) setLast(correction || null);
      })
      .catch(problem => { if (alive) setError(message(problem)); });
    return () => { alive = false; };
  }, [sessionId, attendanceId]);
  if (error) return <Feedback error={error} />;
  if (!last) return null;
  return <dl className={`${styles.detail} ${styles.lastCorrection}`}>
    <dt>Última corrección</dt><dd>{fechaHora(last.fechaHora)} · {last.actorNombre || last.docente}</dd>
    <dt>Motivo</dt><dd>{last.motivo}</dd>
  </dl>;
}
