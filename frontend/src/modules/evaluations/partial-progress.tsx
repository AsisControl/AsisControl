"use client";

import { CheckboxSelect } from "@/shared/ui/checkbox-select";
import { useEffect, useState } from "react";
import { message } from "@/core/api/client";
import { Feedback, Loading } from "@/shared/ui";
import { evaluationDate } from "./lib/labels";
import { ReportContent } from "./report-content";
import type { ProgressReport } from "./report-types";
import { downloadPartialReport, getPartialReport, listPartialEvaluations, type PartialEvaluation } from "./services/report-service";
import styles from "./evaluations.module.css";

const types: Record<string, string> = {
  EXAMEN: "Examen", PRACTICA_CALIFICADA: "Práctica calificada", EVALUACION_ORAL: "Evaluación oral",
  PROYECTO: "Proyecto", TRABAJO_ACADEMICO: "Trabajo académico",
};

export function PartialProgress({ assignment, period, students }: {
  assignment: number;
  period: number;
  students: { idAlumno: number; alumno: string }[];
}) {
  const [student, setStudent] = useState("");
  const [evaluations, setEvaluations] = useState<PartialEvaluation[] | null>(null);
  const [selected, setSelected] = useState<number[]>([]);
  const [competencies, setCompetencies] = useState<number[] | null>(null);
  const [report, setReport] = useState<ProgressReport | null>(null);
  const [removed, setRemoved] = useState(0);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    let active = true;
    listPartialEvaluations(assignment, period)
      .then(data => { if (active) setEvaluations(data); })
      .catch(failure => { if (active) setError(message(failure)); });
    return () => { active = false; };
  }, [assignment, period]);

  function select(ids: number[]) {
    setSelected(ids);
    setReport(null);
  }

  const options = [...new Map(evaluations?.flatMap(item => (item.competencias ?? []).map(c => [c.id, c] as const))).values()]
    .sort((a, b) => a.nombre.localeCompare(b.nombre, "es"));
  const eligible = evaluations?.filter(item => competencies === null || item.competencias?.some(c => competencies.includes(c.id))) ?? [];
  const selectedEligible = selected.filter(id => eligible.some(item => item.id === id));

  function selectCompetencies(ids: number[] | null) {
    setCompetencies(ids);
    const retained = selected.filter(id => ids === null || evaluations?.find(item => item.id === id)?.competencias?.some(c => ids.includes(c.id)));
    setRemoved(selected.length - retained.length);
    select(retained);
  }

  async function run(pdf = false) {
    if (busy || !student || !selectedEligible.length) return;
    setBusy(true);
    setError("");
    const context = { idAlumno: Number(student), idAsignacionCurso: assignment, idPeriodoAcademico: period };
    try {
      const selectedCompetencies = competencies?.filter(id => eligible.some(item => selectedEligible.includes(item.id)
        && item.competencias?.some(c => c.id === id)));
      if (pdf) await downloadPartialReport(context, selectedEligible, selectedCompetencies);
      else setReport(await getPartialReport(context, selectedEligible, selectedCompetencies));
    } catch (failure) { setError(message(failure)); }
    finally { setBusy(false); }
  }


  return <>
    <section className={styles.reportPanel} aria-label="Selección del informe parcial">
      <Feedback error={error} />
      {!evaluations && !error && <Loading variant="inline">Cargando evaluaciones…</Loading>}
      {evaluations?.length === 0 && <p>Aún no hay evaluaciones publicadas para este curso y período. Publica una evaluación desde Mis evaluaciones y vuelve a consultar.</p>}
      {!!evaluations?.length && <fieldset className={styles.reportPdfSelection} disabled={busy}>
        <legend>Evaluaciones publicadas</legend>
        <div className={`${styles.reportFilters} ${styles.partialFilters}`}>
          <label className="field">Estudiante<select value={student}
            onChange={event => { setStudent(event.target.value); setReport(null); }}>
            <option value="">Selecciona un estudiante</option>
            {students.map(item => <option key={item.idAlumno} value={item.idAlumno}>{item.alumno}</option>)}
          </select></label>
          {!!options.length && <div><span className={styles.partialFieldLabel}>Competencias</span><CheckboxSelect singular="competencia" plural="competencias"
            options={options.map(item => ({ id: item.id, label: item.nombre }))}
            selected={competencies ?? options.map(item => item.id)}
            onChange={ids => selectCompetencies(ids.length === options.length ? null : ids)} /></div>}
          <div><span className={styles.partialFieldLabel}>Evaluaciones</span><CheckboxSelect singular="evaluación" plural="evaluaciones"
            options={eligible.map(item => ({ id: item.id, label: item.nombre + " · " + (types[item.tipo] ?? item.tipo) + " · " + evaluationDate(item.fecha) }))}
            selected={selectedEligible} onChange={ids => { setRemoved(0); select(ids); }} /></div>
        </div>
        {removed > 0 && <p role="status">Se quitaron {removed} evaluaciones que ya no corresponden a las competencias seleccionadas.</p>}

        <div className={`${styles.reportActions} ${styles.partialActions}`}>
          <button type="button" className="primary-action" disabled={!student || !selectedEligible.length} onClick={() => void run()}>Consultar informe parcial</button>
          {report && <button type="button" className="secondary-action" onClick={() => void run(true)}>Descargar PDF parcial</button>}
        </div>
      </fieldset>}
    </section>
    {report && <ReportContent report={report} partial sustento="docente" partialContext={{
      idAlumno: Number(student), idAsignacionCurso: assignment, idPeriodoAcademico: period,
    }} />}
  </>;
}
