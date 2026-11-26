"use client";

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
  const [filter, setFilter] = useState("");
  const [competencies, setCompetencies] = useState<number[] | null>(null);
  const [competencyFilter, setCompetencyFilter] = useState("");
  const [report, setReport] = useState<ProgressReport | null>(null);
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
    select(selected.filter(id => ids === null || evaluations?.find(item => item.id === id)?.competencias?.some(c => ids.includes(c.id))));
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

  const shown = eligible.filter(item =>
    `${item.nombre} ${types[item.tipo] ?? item.tipo}`.toLocaleLowerCase("es").includes(filter.trim().toLocaleLowerCase("es")));
  return <>
    <section className={styles.reportPanel} aria-label="Selección del informe parcial">
      <Feedback error={error} />
      {!evaluations && !error && <Loading variant="inline">Cargando evaluaciones…</Loading>}
      {evaluations?.length === 0 && <p>Aún no hay evaluaciones publicadas para este curso y período. Publica una evaluación desde Mis evaluaciones y vuelve a consultar.</p>}
      {!!evaluations?.length && <fieldset className={styles.reportPdfSelection} disabled={busy}>
        <legend>Evaluaciones publicadas</legend>
        <div className={styles.reportFilters}>
          <label className="field">Estudiante<select value={student}
            onChange={event => { setStudent(event.target.value); setReport(null); }}>
            <option value="">Selecciona un estudiante</option>
            {students.map(item => <option key={item.idAlumno} value={item.idAlumno}>{item.alumno}</option>)}
          </select></label>
          {!!options.length && <label className="field">Buscar competencias<input type="search" value={competencyFilter}
            onChange={event => setCompetencyFilter(event.target.value)} /></label>}
          <label className="field">Filtrar evaluaciones<input type="search" value={filter} onChange={event => setFilter(event.target.value)} /></label>
        </div>
        {!!options.length && <fieldset className={styles.reportPdfSelection}>
          <legend>Competencias</legend>
          <div className={styles.reportActions}>
            <button type="button" className="secondary-action" onClick={() => selectCompetencies(null)}>Todas las competencias</button>
            <button type="button" className="secondary-action" onClick={() => selectCompetencies([])}>Limpiar competencias</button>
            <span>{competencies?.length ?? options.length} de {options.length}</span>
          </div>
          <div className={styles.reportPdfList}>{options.filter(item => item.nombre.toLocaleLowerCase("es")
            .includes(competencyFilter.trim().toLocaleLowerCase("es"))).map(item => <label key={item.id}>
            <input type="checkbox" checked={competencies === null || competencies.includes(item.id)} onChange={event => {
              const current = competencies ?? options.map(c => c.id);
              selectCompetencies(event.target.checked ? [...current, item.id] : current.filter(id => id !== item.id));
            }} />{item.nombre}
          </label>)}</div>
        </fieldset>}
        <div className={styles.reportActions}>
          <button type="button" className="secondary-action" disabled={!eligible.length} onClick={() => select(eligible.map(item => item.id))}>Seleccionar todas</button>
          <button type="button" className="secondary-action" disabled={!selected.length} onClick={() => select([])}>Limpiar selección</button>
          <span role="status">Seleccionadas: {selectedEligible.length} de {eligible.length}</span>
        </div>
        <div className={styles.reportPdfList}>{shown?.map(item => <label key={item.id}>
          <input type="checkbox" checked={selected.includes(item.id)} onChange={event =>
            select(event.target.checked ? [...selected, item.id] : selected.filter(id => id !== item.id))} />
          <span>{item.nombre} · {types[item.tipo] ?? item.tipo} · {evaluationDate(item.fecha)}</span>
        </label>)}</div>
        {shown?.length === 0 && <p>El filtro no encontró evaluaciones.</p>}
        <div className={styles.reportActions}>
          <button type="button" className="primary-action" disabled={!student || !selectedEligible.length} onClick={() => void run()}>Consultar informe parcial</button>
          {report && <button type="button" className="secondary-action" onClick={() => void run(true)}>Descargar PDF parcial</button>}
        </div>
      </fieldset>}
    </section>
    {report && <ReportContent report={report} partial sustento="docente" />}
  </>;
}