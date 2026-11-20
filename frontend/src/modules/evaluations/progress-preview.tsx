"use client";

import { useState } from "react";
import { PageHeader, TabNav } from "@/shared/ui";
import { listarAsignacionesPropias, obtenerPlanilla } from "./services/evaluation-service";
import { evaluationDate, LEVEL_SCALE, PERIODS, sectionOf } from "./lib/labels";
import type { Evaluation, GradeSheetData, GradeSheetStudent, OwnAssignment } from "./types";
import styles from "./evaluations.module.css";
import { listProgressEvaluations, listStudentGrades, type ProgressGrade } from "./services/progress-service";
import { Empty, ReadPanel } from "./progress-read-panel";
import { PeriodProgress } from "./period-progress";

export function ProgressPreview() {
  const [mode, setMode] = useState("evaluation");
  return <section>
    <PageHeader title="Progreso por competencias" description="Consulta los resultados disponibles de cada estudiante." />
    <TabNav label="Modo de consulta de progreso" current={mode} onSelect={setMode} tabs={[
      { id: "evaluation", label: "Consulta por evaluación" },
      { id: "period", label: "Informe por período" },
    ]} />
    <p className={styles.notice}>{mode === "evaluation"
      ? "Vista preliminar por evaluación. Los resultados pueden cambiar; esta consulta no constituye una libreta publicada ni un consolidado del período."
      : "Consulta la última versión publicada de la libreta de cada estudiante."}</p>
    <ReadPanel key={mode} load={listarAsignacionesPropias}>{(items) => <AssignmentSelector assignments={items} mode={mode} />}</ReadPanel>
  </section>;
}

function AssignmentSelector({ assignments, mode }: { assignments: OwnAssignment[]; mode: string }) {
  const [id, setId] = useState("");
  const assignment = assignments.find((item) => String(item.id) === id);
  if (!assignments.length) return <Empty>No tienes cursos y secciones asignados.</Empty>;
  return <>
    <div className={`panel ${styles.filters}`}>
      <label className="field">Curso y sección
        <select value={id} onChange={(event) => setId(event.target.value)}>
          <option value="">Selecciona un curso y sección</option>
          {assignments.map((item) => <option key={item.id} value={item.id}>{item.curso} · {sectionOf(item)} · {item.anioAcademico}</option>)}
        </select>
      </label>
    </div>
    {assignment ? mode === "period"
      ? <PeriodProgress key={assignment.id} assignmentId={assignment.id} />
      : <AssignmentProgress key={assignment.id} assignment={assignment} />
      : <Empty>Selecciona un curso y sección para consultar su progreso.</Empty>}
  </>;
}

function AssignmentProgress({ assignment }: { assignment: OwnAssignment }) {
  const [load] = useState(() => () => listProgressEvaluations(assignment.id));
  return <ReadPanel load={load}>{(evaluations) => <EvaluationSelector evaluations={evaluations} />}</ReadPanel>;
}

function EvaluationSelector({ evaluations }: { evaluations: Evaluation[] }) {
  const [id, setId] = useState("");
  const evaluation = evaluations.find((item) => String(item.id) === id);
  if (!evaluations.length) return <Empty>No hay evaluaciones disponibles para este curso y sección.</Empty>;
  return <>
    <div className={`panel ${styles.filters}`}>
      <label className="field">Evaluación
        <select value={id} onChange={(event) => setId(event.target.value)}>
          <option value="">Selecciona una evaluación</option>
          {evaluations.map((item) => <option key={item.id} value={item.id}>{item.titulo} · {PERIODS[item.periodoEvaluacion]} · {evaluationDate(item.fechaEvaluacion)}</option>)}
        </select>
      </label>
    </div>
    {evaluation ? <EvaluationProgress key={evaluation.id} evaluation={evaluation} /> : <Empty>Selecciona una evaluación para ver sus estudiantes.</Empty>}
  </>;
}

function EvaluationProgress({ evaluation }: { evaluation: Evaluation }) {
  const [load] = useState(() => async () => {
    const sheet = await obtenerPlanilla(evaluation.id);
    if (sheet.evaluacion.id !== evaluation.id || sheet.evaluacion.idAsignacionCurso !== evaluation.idAsignacionCurso)
      throw new Error("La planilla no corresponde a la evaluación seleccionada.");
    if (sheet.evaluacion.estado === "ANULADA") throw new Error("La evaluación ya no está disponible.");
    return sheet;
  });
  return <ReadPanel load={load}>{(sheet) => <StudentSelector sheet={sheet} />}</ReadPanel>;
}

function StudentSelector({ sheet }: { sheet: GradeSheetData }) {
  const [id, setId] = useState("");
  const student = sheet.alumnos.find((item) => String(item.idAlumno) === id);
  if (!sheet.alumnos.length) return <Empty>No hay estudiantes con matrícula activa en esta evaluación.</Empty>;
  return <>
    <div className={`panel ${styles.filters}`}>
      <label className="field">Estudiante
        <select value={id} onChange={(event) => setId(event.target.value)}>
          <option value="">Selecciona un estudiante</option>
          {sheet.alumnos.map((item) => <option key={item.idAlumno} value={item.idAlumno}>{item.nombreCompleto}</option>)}
        </select>
      </label>
    </div>
    {student ? <StudentResults key={student.idAlumno} evaluation={sheet.evaluacion} student={student} /> : <Empty>Selecciona un estudiante para ver sus competencias y resultados.</Empty>}
  </>;
}

function StudentResults({ evaluation, student }: { evaluation: Evaluation; student: GradeSheetStudent }) {
  const [load] = useState(() => () => listStudentGrades(evaluation.id, student.idAlumno));
  return <ReadPanel load={load}>{(grades) => <Results evaluation={evaluation} student={student} grades={grades} />}</ReadPanel>;
}

function Results({ evaluation, student, grades }: { evaluation: Evaluation; student: GradeSheetStudent; grades: ProgressGrade[] }) {
  const states = { BORRADOR: "Borrador", PUBLICADA: "Publicada", CERRADA: "Cerrada", ANULADA: "Anulada" };
  return <article className={`panel ${styles.sheet}`} aria-label="Vista preliminar del estudiante">
    <div className={styles.sheetHeading}><div>
      <h2>{student.nombreCompleto}</h2>
      <p>{student.codigoAlumno || "Sin código de estudiante"}</p>
      <p>{evaluation.curso} · {evaluation.seccion} · {evaluation.anioAcademico}</p>
      <p>{evaluation.titulo} · {PERIODS[evaluation.periodoEvaluacion]} · {evaluationDate(evaluation.fechaEvaluacion)}</p>
      <p>Estado de la evaluación: {states[evaluation.estado]}</p>
    </div></div>
    <p className="hint">{LEVEL_SCALE}</p>
    {!evaluation.competencias.length ? <Empty>No hay competencias disponibles en esta evaluación.</Empty> : <>
      {!grades.length && <p className="hint">Este estudiante aún no tiene resultados registrados en esta evaluación.</p>}
      <div className={styles.sheetTable}><table>
        <caption>Competencias y resultados disponibles</caption>
        <thead><tr><th scope="col">Competencia</th><th scope="col">Nivel</th><th scope="col">Observación</th></tr></thead>
        <tbody>{evaluation.competencias.map((competency) => {
          const grade = grades.find((item) => item.idCompetencia === competency.id);
          return <tr key={competency.id}>
            <th scope="row">{competency.codigo} · {competency.nombre}</th>
            <td>{grade?.nivelLogro ?? "Sin resultado"}</td>
            <td style={{ whiteSpace: "pre-wrap", overflowWrap: "anywhere" }}>{grade?.observacion?.trim() || "Sin observación registrada"}</td>
          </tr>;
        })}</tbody>
      </table></div>
    </>}
  </article>;
}
