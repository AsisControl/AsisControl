"use client";
import { useEffect, useState } from "react";
import { api, message } from "@/core/api/client";
import { StatusPill } from "@/shared/ui";
import { normalizePage } from "@/shared/lib/pagination";
import type { Page } from "@/shared/types/api";
import { EnrollmentHistory } from "./enrollment-history";
import type { History } from "./types";
export type GuardianRequest = {
  id: number;
  alumnoId: number;
  alumno: string;
  codigoAlumno: string;
  anioAcademico: number;
  grado: number;
  seccion: string;
  estado: string;
  version: number;
  fechaEnvio: string | null;
  fechaRevision: string | null;
  observaciones: string | null;
  motivoRechazo: string | null;
};
/** Campos de MatriculaResponse que el apoderado necesita para el seguimiento. */
type GuardianEnrollmentRecord = {
  id: number;
  codigoMatricula: string;
  solicitudMatriculaId: number | null;
  seccion: string;
  grado: number;
  estado: string;
};
type Tracking = {
  version: number;
  enrollment: GuardianEnrollmentRecord | null;
  events: History[];
};
async function loadTracking(request: GuardianRequest): Promise<Tracking> {
  const records = normalizePage(
    await api<Page<GuardianEnrollmentRecord>>("/api/matriculas?size=100"),
  );
  const enrollment =
    records.content.find((item) => item.solicitudMatriculaId === request.id) ?? null;
  const [requestEvents, enrollmentEvents] = await Promise.all([
    api<History[]>(`/api/solicitudes-matricula/${request.id}/historial`),
    enrollment ? api<History[]>(`/api/matriculas/${enrollment.id}/historial`) : [],
  ]);
  const events = [...requestEvents, ...enrollmentEvents].sort(
    (a, b) => a.fecha.localeCompare(b.fecha) || a.id - b.id,
  );
  return { version: request.version, enrollment, events };
}
/**
 * Seguimiento de una solicitud (US-49): datos vigentes, si ya existe matrícula y la cronología
 * completa de la solicitud y la matrícula. El backend solo responde sobre estudiantes asociados.
 */
export function GuardianEnrollmentTrack({ request }: { request: GuardianRequest }) {
  const [tracking, setTracking] = useState<Tracking | null>(null);
  const [error, setError] = useState("");
  const { id, version } = request;
  useEffect(() => {
    let active = true;
    loadTracking(request)
      .then((value) => {
        if (!active) return;
        setTracking(value);
        setError("");
      })
      .catch((e) => {
        if (active) setError(message(e));
      });
    return () => {
      active = false;
    };
    // La solicitud cambia de objeto al listar; su identidad la dan el id y la versión.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id, version]);
  const enrollment = tracking?.enrollment ?? null;
  return (
    <>
      <dl className="detail-grid">
        <div>
          <dt>Estudiante</dt>
          <dd>{request.alumno}</dd>
        </div>
        <div>
          <dt>Año / grado</dt>
          <dd>
            {request.anioAcademico} · {request.grado}°
          </dd>
        </div>
        <div>
          <dt>Estado de la solicitud</dt>
          <dd>
            <StatusPill state={request.estado} />
          </dd>
        </div>
        <div>
          <dt>Sección</dt>
          <dd>{enrollment ? `${enrollment.grado}° ${enrollment.seccion}` : request.seccion}</dd>
        </div>
        {enrollment && (
          <div>
            <dt>Matrícula</dt>
            <dd>{enrollment.codigoMatricula}</dd>
          </div>
        )}
        {enrollment && (
          <div>
            <dt>Estado de la matrícula</dt>
            <dd>
              <StatusPill state={enrollment.estado} />
            </dd>
          </div>
        )}
      </dl>
      {tracking && !enrollment && request.estado !== "MATRICULA_FINALIZADA" && (
        <p className="dialog-note">
          Esta solicitud aún no está finalizada: todavía no existe una matrícula ni se ha
          reservado una vacante.
        </p>
      )}
      <h4>Historial de cambios</h4>
      {error ? (
        <p className="hint">No se pudo cargar el historial: {error}</p>
      ) : !tracking ? (
        <p role="status">Cargando historial…</p>
      ) : tracking.events.length ? (
        <EnrollmentHistory events={tracking.events} showActor={false} />
      ) : (
        <p className="empty">Aún no hay cambios registrados.</p>
      )}
    </>
  );
}
