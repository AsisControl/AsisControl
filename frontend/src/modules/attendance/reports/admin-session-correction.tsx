"use client";
import { useEffect, useState } from "react";
import { message } from "@/core/api/client";
import { Feedback, Loading } from "@/shared/ui";
import { obtenerSesion } from "../services/asistencia-service";
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
  if (!session) return <Loading skeleton="detail" rows={4}>Cargando asistencia finalizada…</Loading>;
  if (session.estado !== "TOMADA" || !session.puedeEditar) return <>
    <p>La sesión no está finalizada o no está disponible para corrección.</p>
    <button className="secondary-action" onClick={onCancel}>Volver al reporte</button>
  </>;
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
