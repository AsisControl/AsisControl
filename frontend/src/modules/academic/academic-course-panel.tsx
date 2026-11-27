"use client";

import { useState } from "react";
import { api } from "@/core/api/client";
import type { AcademicYear } from "@/modules/academic-config";
import { ReadPanel, ReportRoster, AcademicEvaluationList } from "@/modules/evaluations";
import { usePaged } from "@/shared/hooks/use-paged";
import { Feedback, Loading, Pager, PanelHeading } from "@/shared/ui";
import styles from "./grading-rules/grading-rules-panel.module.css";

type Course = { id: number; nombre: string; codigo: string };

type Tab = "evaluaciones" | "reportes";

export function AcademicCoursePanel({ tab }: { tab: Tab }) {
  const years = usePaged<AcademicYear>("/api/configuracion-academica/anios");
  const [id, setId] = useState("");
  const year = years.data?.content.find(item => String(item.id) === id);
  return <section className="panel">
    {tab === "reportes"
      ? <PanelHeading title="Reportes académicos" summary="Consulta las libretas y los niveles de logro por curso y sección." />
      : <PanelHeading title="Evaluaciones" summary="Consulta el estado, el peso y el avance de calificación por curso y sección." />}
    <Feedback error={years.error} />
    <div className={styles.scope}>
      {years.loading ? <Loading skeleton="fields">Cargando años académicos.</Loading> :
        <label className="field">Año académico
          <select value={id} onChange={event => setId(event.target.value)}>
            <option value="">Selecciona un año</option>
            {years.data?.content.map(item => <option key={item.id} value={item.id}>{item.anio}</option>)}
          </select>
        </label>}
      {year && <YearFilters key={year.id} tab={tab} year={year} />}
      {(years.data?.totalPages ?? 0) > 1 && <div className={styles.fullWidth}>
        <Pager data={years.data} loading={years.loading} setPage={page => { setId(""); years.setPage(page); }} />
      </div>}
    </div>
  </section>;
}

function YearFilters({ year, tab }: { year: AcademicYear; tab: Tab }) {
  const [period, setPeriod] = useState("");
  const [grade, setGrade] = useState("");
  const [section, setSection] = useState("");
  return <>
    <label className="field">Período académico
      <select value={period} onChange={event => { setPeriod(event.target.value); setGrade(""); setSection(""); }}>
        <option value="">Selecciona un período</option>
        {year.periodos.map(item => <option key={item.id} value={item.id}>{item.nombre}</option>)}
      </select>
    </label>
    {period && <label className="field">Grado
      <select value={grade} onChange={event => { setGrade(event.target.value); setSection(""); }}>
        <option value="">Selecciona un grado</option>
        {year.grados.map(item => <option key={item.id} value={item.numero}>{item.numero}.° grado</option>)}
      </select>
    </label>}
    {grade && <label className="field">Sección
      <select value={section} onChange={event => setSection(event.target.value)}>
        <option value="">Selecciona una sección</option>
        {year.secciones.filter(item => item.grado === Number(grade)).map(item =>
          <option key={item.id} value={item.id}>{item.nombre}</option>)}
      </select>
    </label>}
    {section && <CourseFilter key={section} tab={tab} year={year.anio} period={period} grade={grade} section={section} />}
  </>;
}

function CourseFilter({ tab, year, period, grade, section }: { tab: Tab; year: number; period: string; grade: string; section: string }) {
  const [load] = useState(() => () => api<{ cursos: Course[] }>(`/api/planes-estudios/${year}/${grade}`));
  const [course, setCourse] = useState("");
  const query = new URLSearchParams({ anio: String(year), periodo: period, grado: grade, seccion: section, curso: course });
  return <ReadPanel load={load}>{plan => <>
    {plan.cursos.length ? <label className="field">Curso
      <select value={course} onChange={event => setCourse(event.target.value)}>
        <option value="">Selecciona un curso</option>
        {plan.cursos.map(item => <option key={item.id} value={item.id}>{item.nombre} · {item.codigo}</option>)}
      </select>
    </label> : <p>No hay cursos en el plan de este grado.</p>}
    {course && <div key={query.toString()} className={styles.fullWidth}>{tab === "reportes"
      ? <ReportRoster query={query.toString()} /> : <AcademicEvaluationList query={query.toString()} />}</div>}
  </>}</ReadPanel>;
}
