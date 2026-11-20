"use client";
import { useEffect, useState } from "react";
import { message } from "@/core/api/client";
import { Feedback, Loading } from "@/shared/ui";
import { textoPlazo } from "../lib";
import { obtenerSesion } from "../services/asistencia-service";
import styles from "./attendance-reports.module.css";
import { SessionCorrection } from "../session-correction";
import type { SesionAsistencia } from "../types";

export function AdminSessionCorrection({ sessionId, attendanceId, onCancel, onSaved }: {
  sessionId: number; attendanceId: number; onCancel: () => void; onSaved: () => void;
}) {
  const [session, setSession] = useState<SesionAsistencia | null>(null);
  const [error, setError] = useState("");
  useEffect(() => {
    let alive = true;
    obtenerSesion(sessionId).then(value => { if (alive) setSession(value); })
      .catch(problem => { if (alive) setError(message(problem)); });
    return () => { alive = false; };
  }, [sessionId]);
  if (error) return <><Feedback error={error} /><button className="secondary-action" onClick={onCancel}>Volver al reporte</button></>;
  if (!session) return <Loading>Cargando asistencia finalizada…</Loading>;
  if (session.estado !== "TOMADA" || !session.puedeEditar) {
    const plazo = textoPlazo(session);
    const aviso = session.estado !== "TOMADA"
      ? "Esta clase todavía no tiene la asistencia finalizada, por eso aún no se puede corregir."
      : `El plazo para corregir esta asistencia ya venció${plazo ? ` (se podía corregir ${plazo})` : ""}. `
        + "Solo se corrige hasta el inicio de la siguiente clase del curso o hasta el final del día de la clase.";
    return <section className={styles.unavailable} role="status">
      <h3>Corrección no disponible</h3>
      <p>{session.curso} · {session.seccion} · {session.fecha}</p>
      <p>{aviso}</p>
      <div className="form-actions">
        <button className="secondary-action" onClick={onCancel}>Volver al reporte</button>
      </div>
    </section>;
  }
  const student = session.id === sessionId
    ? session.alumnos.find(item => item.asistenciaId === attendanceId)
    : undefined;
  if (!student) return <>
    <p>El registro seleccionado ya no está disponible en esta sesión. Vuelve al reporte y actualízalo.</p>
    <button className="secondary-action" onClick={onCancel}>Volver al reporte</button>
  </>;
  return <>
    <h3>Corregir asistencia de {student.nombreCompleto}</h3>
    <p>{session.curso} · {session.seccion} · {session.fecha}</p>
    <SessionCorrection embedded key={`${sessionId}:${attendanceId}`}
      sesion={{ ...session, alumnos: [student] }} onCancel={onCancel} onSaved={onSaved} />
  </>;
}
