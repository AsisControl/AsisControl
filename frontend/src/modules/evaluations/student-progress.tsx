"use client";
import { useEffect, useState } from "react";
import { message } from "@/core/api/client";
import { dateLabel } from "@/shared/lib/formatters";
import { obtenerConsolidado, obtenerCorrecciones } from "./services/evaluation-service";
import { listReportPeriods } from "./services/report-service";
import type { ReportPeriod } from "./report-types";
import type {
  ConsolidatedLevel,
  CorrectionEntry,
  Evaluation,
  GradeSheetStudent,
} from "./types";
import styles from "./evaluations.module.css";

import { Loading } from "@/shared/ui";
type Correction = CorrectionEntry & { competencia: string };
type Progress = { levels: ConsolidatedLevel[]; corrections: Correction[] };

/** Detalle del estudiante elegido: nivel consolidado y trazabilidad de sus correcciones (US-50). */
export function StudentProgress({
  student,
  evaluation,
}: {
  student: GradeSheetStudent | null;
  evaluation: Evaluation;
}) {
  const [progress, setProgress] = useState<Progress | null>(null);
  const [error, setError] = useState("");
  const [periods, setPeriods] = useState<ReportPeriod[]>();
  const [period, setPeriod] = useState<number>();
  const assignment = evaluation.idAsignacionCurso;
  const cualitativa = Boolean(evaluation.modeloCualitativo);
  const initialPeriod = periods?.find(item => item.fechaInicio <= evaluation.fechaEvaluacion
    && item.fechaFin >= evaluation.fechaEvaluacion)?.id;
  const selectedPeriod = period ?? initialPeriod;
  useEffect(() => {
    if (!cualitativa) return;
    let active = true;
    listReportPeriods(assignment).then(items => { if (active) setPeriods(items); },
      failure => { if (active) setError(message(failure)); });
    return () => { active = false; };
  }, [assignment, cualitativa]);
  useEffect(() => {
    if (!student || cualitativa && selectedPeriod == null) return;
    let active = true;
    const name = (id: number) =>
      evaluation.competencias.find((item) => item.id === id)?.nombre ?? "";
    Promise.all([
      obtenerConsolidado(student.idAlumno, assignment, selectedPeriod),
      Promise.all(
        student.notas
          .filter((note) => note.corregida)
          .map(async (note) =>
            (await obtenerCorrecciones(note.idCalificacion)).map((entry) => ({
              ...entry,
              competencia: name(note.idCompetencia),
            })),
          ),
      ),
    ])
      .then(([levels, corrections]) => {
        if (active) setProgress({ levels, corrections: corrections.flat() });
      })
      .catch((e) => {
        if (active) setError(message(e));
      });
    return () => {
      active = false;
    };
  }, [student, evaluation, assignment, cualitativa, selectedPeriod]);
  if (!student)
    return (
      <section className={`panel ${styles.placeholder}`}>
        Selecciona un estudiante para ver su detalle de progreso
      </section>
    );
  return (
    <section className="panel" aria-label="Detalle del estudiante">
      <h4>{student.nombreCompleto}</h4>
      {cualitativa && <label className="field">Período académico
        <select value={selectedPeriod ?? ""} onChange={event => {
          setPeriod(Number(event.target.value) || undefined); setProgress(null); setError("");
        }}>
          <option value="">Selecciona un período</option>
          {periods?.map(item => <option key={item.id} value={item.id}>{item.nombre}</option>)}
        </select>
      </label>}
      {error ? (
        <p className="hint">No se pudo cargar el detalle: {error}</p>
      ) : cualitativa && periods && selectedPeriod == null ? (
        <p className="hint">Selecciona un período académico para consultar los resultados.</p>
      ) : !progress ? (
        <Loading skeleton="detail">Cargando detalle…</Loading>
      ) : (
        <>
          {progress.levels.length ? (
            <ul className={styles.progressList}>
              {progress.levels.map((item) => (
                <li key={item.idCompetencia}>
                  <span>{item.nombreCompetencia}</span>
                  <strong>{item.nivelConsolidado ?? (item.estadoAvance === "NO_EVALUADA" ? "Aún no evaluada" : item.estadoAvance === "PENDIENTE" ? "Pendiente" : "Datos insuficientes")}</strong>
                  {!!item.componentesFaltantes?.length && <small>Faltan: {item.componentesFaltantes.join(", ")}</small>}
                </li>
              ))}
            </ul>
          ) : (
            <p className="hint">Aún no tiene evaluaciones publicadas en este curso.</p>
          )}
          {progress.corrections.length > 0 && (
            <>
              <h4>Historial de correcciones</h4>
              <ol className="history-list">
                {progress.corrections.map((item) => (
                  <li key={`${item.competencia}-${item.numeroVersion}`}>
                    <strong>
                      {item.competencia}: {item.nivelAnterior} → {item.nivelNuevo}
                    </strong>
                    <small>
                      {dateLabel(item.fecha)} · {item.autor}
                    </small>
                    {item.notaAnterior != null && item.notaNueva != null && <small>
                      Nota: {item.notaAnterior} → {item.notaNueva}
                    </small>}
                    <p>Motivo: {item.motivo}</p>
                  </li>
                ))}
              </ol>
            </>
          )}
        </>
      )}
    </section>
  );
}
