"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { ApiError } from "@/core/api/client";
import { markNotificationsRead } from "@/modules/notifications";
import { Feedback, isoDateLabel, Loading, PanelHeading } from "@/shared/ui";
import { obtenerResultadoPublicado, type PublishedResult } from "./services/evaluation-service";

const RESULT_UNAVAILABLE = "La evaluación ya no está disponible para esta cuenta. Puedes consultar el resumen en Progreso.";

export function PublishedEvaluation({ evaluation, student, notice }: {
  evaluation?: string; student?: string; notice?: string;
}) {
  const [result, setResult] = useState<PublishedResult | null>(null);
  const [failure, setFailure] = useState<"unavailable" | "retry" | null>(null);
  const [loadAttempt, setLoadAttempt] = useState(0);
  const [readAttempt, setReadAttempt] = useState(0);
  const [readFailed, setReadFailed] = useState(false);
  const valid = Boolean(evaluation && /^\d+$/.test(evaluation) && student && /^\d+$/.test(student));
  const noticeId = notice && /^\d+$/.test(notice) ? Number(notice) : null;

  useEffect(() => {
    if (!valid || !evaluation || !student) return;
    let active = true;
    obtenerResultadoPublicado(evaluation, student)
      .then((updated) => {
        if (active) setResult(updated);
      }).catch((cause) => {
        if (!active) return;
        const inaccessible = cause instanceof ApiError && [403, 404].includes(cause.status);
        setFailure(inaccessible ? "unavailable" : "retry");
      });
    return () => { active = false; };
  }, [evaluation, student, valid, loadAttempt]);

  useEffect(() => {
    if (!result || noticeId === null) return;
    let active = true;
    void markNotificationsRead(noticeId).catch(() => {
      if (active) setReadFailed(true);
    });
    return () => { active = false; };
  }, [result, noticeId, readAttempt]);

  const error = !valid || failure === "unavailable" ? RESULT_UNAVAILABLE
    : failure === "retry" ? "No se pudo cargar la evaluación." : undefined;
  return <section className="panel">
    <PanelHeading title={result?.titulo ?? "Detalle de la evaluación"}
      summary={result && `${result.estudiante} · ${result.curso} · ${isoDateLabel(result.fechaEvaluacion)}`}
      actions={<Link href="/progreso" className="secondary-action">Volver a Progreso</Link>} />
    <Feedback error={error} />
    {failure === "retry" && <button type="button" onClick={() => {
      setFailure(null);
      setLoadAttempt((attempt) => attempt + 1);
    }}>Reintentar carga de la evaluación</button>}
    {readFailed && <div className="toolbar">
      <p role="status">La nota está disponible. No se pudo marcar el aviso como leído.</p>
      <button type="button" onClick={() => {
        setReadFailed(false);
        setReadAttempt((attempt) => attempt + 1);
      }}>Reintentar marcado</button>
    </div>}
    {!error && !result && <Loading>Cargando calificaciones…</Loading>}
    {result && <div className="user-table-wrap">
      <table className="user-table" aria-label="Calificación publicada">
        <thead><tr><th scope="col">Competencia</th><th scope="col">Calificación vigente</th></tr></thead>
        <tbody>{result.notas.map((grade) => <tr key={grade.idCompetencia}>
          <td data-label="Competencia">{grade.competencia}</td>
          <td data-label="Calificación vigente"><strong>{grade.nivelLogro ?? "—"}</strong></td>
        </tr>)}</tbody>
      </table>
    </div>}
  </section>;
}
