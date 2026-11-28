"use client";
import { useEffect, useState } from "react";
import { message } from "@/core/api/client";
import { Feedback, Loading } from "@/shared/ui";
import { AcademicYearFilter } from "./academic-year-filter";
import { GradeSheet } from "./grade-sheet";
import { academicYears } from "./lib/academic-year";
import { courseOptions, PERIODS, sectionOptions } from "./lib/labels";
import { listarEvaluaciones, obtenerAnioVigente, obtenerEvaluacion } from "./services/evaluation-service";
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
  const [year, setYear] = useState("");
  const [activeYear, setActiveYear] = useState<number | null>(null);
  const [loadingYear, setLoadingYear] = useState(true);
  const [periodo, setPeriodo] = useState("");
  const [cursoId, setCursoId] = useState("");
  const [seccionId, setSeccionId] = useState("");
  const [competenciaId, setCompetenciaId] = useState("");
  const [selectedId, setSelectedId] = useState<number | null>(evaluationId);
  const [evaluations, setEvaluations] = useState<{ key: string; items: Evaluation[] } | null>(null);
  const [error, setError] = useState("");
  const yearOptions = academicYears(
    ...assignments.map((item) => item.anioAcademico),
    activeYear,
    year ? Number(year) : null,
  );
  const assignmentsForYear = assignments.filter((item) => String(item.anioAcademico) === year);
  const assignmentId = assignmentsForYear.find((item) =>
    String(item.cursoId) === cursoId && String(item.seccionId) === seccionId)?.id;
  const contextKey = `${year}|${periodo}|${assignmentId ?? ""}`;

  useEffect(() => {
    let active = true;
    async function initialize() {
      try {
        const [activeAcademicYear, evaluation] = await Promise.all([
          obtenerAnioVigente(),
          evaluationId === null ? null : obtenerEvaluacion(evaluationId),
        ]);
        if (!active) return;
        setActiveYear(activeAcademicYear);
        setYear(String(evaluation?.anioAcademico ?? activeAcademicYear ?? ""));
        if (evaluation) {
          setCursoId(String(evaluation.idCurso));
          setSeccionId(String(evaluation.idSeccion));
          setSelectedId(evaluation.id);
        }
      } catch (e) {
        if (active) setError(message(e));
      } finally {
        if (active) setLoadingYear(false);
      }
    }
    void initialize();
    return () => { active = false; };
  }, [evaluationId]);
  useEffect(() => {
    if (!assignmentId) return;
    let active = true;
    listarEvaluaciones({ periodo, idAsignacionCurso: String(assignmentId) })
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
  }, [contextKey, periodo, assignmentId]);
  const items = evaluations?.key === contextKey ? evaluations.items : null;
  const current = items?.find((item) => item.id === selectedId) ?? items?.[0] ?? null;
  const ready = assignmentId !== undefined;
  return (
    <>
      <div className={`panel ${styles.filters}`}>
        <AcademicYearFilter
          value={year}
          years={yearOptions}
          activeYear={activeYear}
          loading={loadingYear}
          onChange={(value) => {
            setYear(value);
            setPeriodo("");
            setCursoId("");
            setSeccionId("");
            setCompetenciaId("");
            setSelectedId(null);
            setEvaluations(null);
            setError("");
          }}
        />
        <label className="field">
          Período
          <select value={periodo} disabled={!year} onChange={(event) => {
            setPeriodo(event.target.value);
            setCompetenciaId("");
            setSelectedId(null);
            setError("");
          }}>
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
            disabled={!year}
            onChange={(event) => {
              setCursoId(event.target.value);
              setSeccionId("");
              setCompetenciaId("");
              setSelectedId(null);
              setError("");
            }}
          >
            <option value="">— Selecciona un curso —</option>
            {courseOptions(assignmentsForYear).map(([id, label]) => (
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
              setSelectedId(null);
              setError("");
            }}
          >
            <option value="">— Selecciona una sección —</option>
            {sectionOptions(assignmentsForYear, cursoId).map(([id, label]) => (
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
      ) : !error && (loadingYear || (ready && !items)) ? (
        <Loading>Cargando evaluaciones…</Loading>
      ) : (
        <div className={`panel ${styles.emptyState}`}>
          <strong>
            {!year ? "Selecciona un año académico para continuar"
              : !assignmentsForYear.length ? "No tienes cursos asignados en este año académico"
              : ready ? "No hay evaluaciones para este curso y sección"
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
