"use client";
import { useEffect, useState } from "react";
import { message } from "@/core/api/client";
import { dateLabel } from "@/shared/lib/formatters";
import { obtenerConsolidado, obtenerCorrecciones } from "./services/evaluation-service";
import type {
  ConsolidatedLevel,
  CorrectionEntry,
  Evaluation,
  GradeSheetStudent,
} from "./types";
import styles from "./evaluations.module.css";

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
  useEffect(() => {
    if (!student) return;
    let active = true;
    const name = (id: number) =>
      evaluation.competencias.find((item) => item.id === id)?.nombre ?? "";
    Promise.all([
      obtenerConsolidado(student.idAlumno, evaluation.idAsignacionCurso),
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
  }, [student, evaluation]);
  if (!student)
    return (
      <section className={`panel ${styles.placeholder}`}>
        Selecciona un estudiante para ver su detalle de progreso
      </section>
    );
  return (
    <section className="panel" aria-label="Detalle del estudiante">
      <h4>{student.nombreCompleto}</h4>
      {error ? (
        <p className="hint">No se pudo cargar el detalle: {error}</p>
      ) : !progress ? (
        <p role="status">Cargando detalle…</p>
      ) : (
        <>
          {progress.levels.length ? (
            <ul className={styles.progressList}>
              {progress.levels.map((item) => (
                <li key={item.idCompetencia}>
                  <span>{item.nombreCompetencia}</span>
                  <strong>{item.nivelConsolidado}</strong>
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
