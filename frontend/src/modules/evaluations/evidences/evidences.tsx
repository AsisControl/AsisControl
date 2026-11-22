"use client";
import { useState } from "react";
import { Feedback, Loading, PanelHeading } from "@/shared/ui";
import { sectionOf } from "../lib/labels";
import type { OwnAssignment } from "../types";
import { competencyLabel } from "./lib/format";
import { EvidenceFormDialog } from "./evidence-form-dialog";
import { EvidenceList } from "./evidence-list";
import { useEvidenceContext } from "./use-evidence-context";
import styles from "./evidences.module.css";

const NO_STUDENTS_REASON =
  "El backend aún no ofrece los estudiantes de una asignación. Se obtienen de la planilla de una evaluación; crea una evaluación para este curso.";

/** PROPUESTA de interfaz: pestaña Evidencias de Evaluaciones (US-17). */
export function Evidences({
  assignments,
  canWrite,
}: {
  assignments: OwnAssignment[];
  canWrite: boolean;
}) {
  const [assignmentId, setAssignmentId] = useState("");
  const [studentId, setStudentId] = useState("");
  const [competenciaId, setCompetenciaId] = useState("");
  const [periodoId, setPeriodoId] = useState("");
  const [creating, setCreating] = useState(false);
  const [success, setSuccess] = useState("");
  // Cada registro nuevo vuelve a montar el listado para mostrarlo al instante.
  const [version, setVersion] = useState(0);
  const assignment = assignments.find((item) => String(item.id) === assignmentId);
  const { context, error, loading } = useEvidenceContext(assignment);
  const student = context?.students?.find((item) => String(item.id) === studentId);
  const noStudents = !!context && context.students === null;
  return (
    <>
      <div className={`panel ${styles.filters}`}>
        <label className="field">
          Curso y sección
          <select
            value={assignmentId}
            onChange={(event) => {
              setAssignmentId(event.target.value);
              setStudentId("");
              setCompetenciaId("");
              setPeriodoId("");
              setSuccess("");
            }}
          >
            <option value="">Selecciona un curso y sección</option>
            {assignments.map((item) => (
              <option key={item.id} value={item.id}>
                {item.curso} · {sectionOf(item)} · {item.anioAcademico}
              </option>
            ))}
          </select>
        </label>
        <label className="field">
          <span>
            Estudiante
            {noStudents && <span className={styles.pending}>Pendiente de backend</span>}
          </span>
          <select
            value={studentId}
            disabled={!context?.students?.length}
            onChange={(event) => {
              setStudentId(event.target.value);
              setSuccess("");
            }}
          >
            <option value="">Selecciona un estudiante</option>
            {context?.students?.map((item) => (
              <option key={item.id} value={item.id}>
                {item.nombre}
              </option>
            ))}
          </select>
          {noStudents && <small className={styles.reason}>{NO_STUDENTS_REASON}</small>}
        </label>
        <label className="field">
          Competencia
          <select
            value={competenciaId}
            disabled={!context}
            onChange={(event) => setCompetenciaId(event.target.value)}
          >
            <option value="">Todas las competencias</option>
            {context?.competencies.map((item) => (
              <option key={item.competenciaId} value={item.competenciaId}>
                {competencyLabel(item)}
              </option>
            ))}
          </select>
        </label>
        <label className="field">
          Período
          <select value={periodoId} disabled={!context} onChange={(event) => setPeriodoId(event.target.value)}>
            <option value="">Todos los períodos</option>
            {context?.periods.map((item) => (
              <option key={item.id} value={item.id}>
                {item.nombre}
              </option>
            ))}
          </select>
        </label>
      </div>
      <div className="panel">
        <PanelHeading
          title="Evidencias pedagógicas"
          summary={student ? student.nombre : "Selecciona un curso y un estudiante"}
          actions={
            canWrite && (
              <button
                type="button"
                className="primary-action"
                disabled={!student}
                onClick={() => {
                  setSuccess("");
                  setCreating(true);
                }}
              >
                + Nueva evidencia
              </button>
            )
          }
        />
        <Feedback error={error} />
        {loading && <Loading>Cargando datos del curso…</Loading>}
        {!assignment && <p className="empty">Selecciona un curso y sección para empezar.</p>}
        {assignment && context && student && (
          <EvidenceList
            key={`${assignment.id}-${student.id}-${competenciaId}-${periodoId}-${version}`}
            assignment={assignment}
            student={student}
            competencies={context.competencies}
            periods={context.periods}
            competenciaId={competenciaId}
            periodoId={periodoId}
            canWrite={canWrite}
            notice={success}
          />
        )}
        {assignment && context && !student && !noStudents && (
          <p className="empty">Selecciona un estudiante para ver sus evidencias.</p>
        )}
      </div>
      {creating && assignment && student && context && (
        <EvidenceFormDialog
          assignment={assignment}
          student={student}
          competencies={context.competencies}
          periods={context.periods}
          competenciaId={competenciaId}
          periodoId={periodoId}
          onClose={(refresh) => {
            setCreating(false);
            if (refresh) setVersion((value) => value + 1);
          }}
          onSaved={() => {
            setCreating(false);
            setSuccess("Evidencia registrada.");
            setVersion((value) => value + 1);
          }}
        />
      )}
    </>
  );
}
