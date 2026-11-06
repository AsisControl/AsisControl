"use client";
import { useEffect, useState } from "react";
import { message } from "@/core/api/client";
import { Feedback } from "@/shared/ui";
import { GradeSheet } from "./grade-sheet";
import { courseOptions, PERIODS, sectionOptions } from "./lib/labels";
import { listarEvaluaciones, obtenerEvaluacion } from "./services/evaluation-service";
import type { Evaluation, OwnAssignment } from "./types";
import styles from "./evaluations.module.css";

/** Pestaña Registro de calificaciones: elige el contexto y abre la planilla de la evaluación. */
export function GradeRegistry({
  canWrite,
  assignments,
  evaluationId,
}: {
  canWrite: boolean;
  assignments: OwnAssignment[];
  /** Evaluación abierta desde Mis evaluaciones o desde la dirección. */
  evaluationId: number | null;
}) {
  const [periodo, setPeriodo] = useState("");
  const [cursoId, setCursoId] = useState("");
  const [seccionId, setSeccionId] = useState("");
  const [competenciaId, setCompetenciaId] = useState("");
  const [selectedId, setSelectedId] = useState<number | null>(evaluationId);
  const [evaluations, setEvaluations] = useState<{ key: string; items: Evaluation[] } | null>(null);
  const [error, setError] = useState("");
  const contextKey = `${periodo}|${cursoId}|${seccionId}`;
  useEffect(() => {
    if (evaluationId === null) return;
    let active = true;
    obtenerEvaluacion(evaluationId)
      .then((value) => {
        if (!active) return;
        setCursoId(String(value.idCurso));
        setSeccionId(String(value.idSeccion));
        setSelectedId(value.id);
      })
      .catch((e) => {
        if (active) setError(message(e));
      });
    return () => {
      active = false;
    };
  }, [evaluationId]);
  useEffect(() => {
    if (!cursoId || !seccionId) return;
    let active = true;
    listarEvaluaciones({ periodo, idCurso: cursoId, idSeccion: seccionId })
      .then((items) => {
        if (active)
          setEvaluations({
            key: contextKey,
            items: items.filter((item) => item.estado !== "ANULADA"),
          });
      })
      .catch((e) => {
        if (active) setError(message(e));
      });
    return () => {
      active = false;
    };
  }, [contextKey, periodo, cursoId, seccionId]);
  const items = evaluations?.key === contextKey ? evaluations.items : null;
  const current = items?.find((item) => item.id === selectedId) ?? items?.[0] ?? null;
  const ready = !!cursoId && !!seccionId;
  return (
    <>
      <div className={`panel ${styles.filters}`}>
        <label className="field">
          Período
          <select value={periodo} onChange={(event) => setPeriodo(event.target.value)}>
            <option value="">Todos los períodos</option>
            {Object.entries(PERIODS).map(([value, label]) => (
              <option key={value} value={value}>
                {label}
              </option>
            ))}
          </select>
        </label>
        <label className="field">
          Curso
          <select
            value={cursoId}
            onChange={(event) => {
              setCursoId(event.target.value);
              setSeccionId("");
              setCompetenciaId("");
            }}
          >
            <option value="">— Selecciona un curso —</option>
            {courseOptions(assignments).map(([id, label]) => (
              <option key={id} value={id}>
                {label}
              </option>
            ))}
          </select>
        </label>
        <label className="field">
          Sección
          <select
            value={seccionId}
            disabled={!cursoId}
            onChange={(event) => {
              setSeccionId(event.target.value);
              setCompetenciaId("");
            }}
          >
            <option value="">— Selecciona una sección —</option>
            {sectionOptions(assignments, cursoId).map(([id, label]) => (
              <option key={id} value={id}>
                {label}
              </option>
            ))}
          </select>
        </label>
        <label className="field">
          Competencia
          <select
            value={competenciaId}
            disabled={!current}
            onChange={(event) => setCompetenciaId(event.target.value)}
          >
            <option value="">Todas</option>
            {current?.competencias.map((item) => (
              <option key={item.id} value={item.id}>
                {item.nombre}
              </option>
            ))}
          </select>
        </label>
        {items && items.length > 1 && (
          <label className="field">
            Evaluación
            <select
              value={current?.id ?? ""}
              onChange={(event) => {
                setSelectedId(Number(event.target.value));
                setCompetenciaId("");
              }}
            >
              {items.map((item) => (
                <option key={item.id} value={item.id}>
                  {item.titulo}
                </option>
              ))}
            </select>
          </label>
        )}
      </div>
      <Feedback error={error} />
      {current ? (
        <GradeSheet
          key={current.id}
          evaluationId={current.id}
          competencyId={competenciaId ? Number(competenciaId) : null}
          canWrite={canWrite && assignments.some((item) => item.id === current.idAsignacionCurso)}
        />
      ) : ready && !items ? (
        <p role="status">Cargando evaluaciones…</p>
      ) : (
        <div className={`panel ${styles.emptyState}`}>
          <strong>
            {ready
              ? "No hay evaluaciones para este curso y sección"
              : "Selecciona período, curso y sección para ver las evaluaciones"}
          </strong>
          <small>
            {ready
              ? "Crea una desde Mis evaluaciones."
              : "Los estudiantes y competencias se cargarán automáticamente según tu asignación."}
          </small>
        </div>
      )}
    </>
  );
}
