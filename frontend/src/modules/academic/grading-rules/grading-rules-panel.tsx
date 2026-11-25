"use client";

import { useEffect, useState, type FormEvent } from "react";
import { api, message } from "@/core/api/client";
import { usePaged } from "@/shared/hooks/use-paged";
import { Feedback, Loading, Pager, PanelHeading } from "@/shared/ui";
import type { AcademicYear, Grade } from "@/modules/academic-config";
import styles from "./grading-rules-panel.module.css";

const PATH = "/api/reglas-calificacion";
const FORMULA = "PROMEDIO_PONDERADO";
const TYPES = {
  EXAMEN: "Examen",
  PRACTICA_CALIFICADA: "Práctica calificada",
  EVALUACION_ORAL: "Evaluación oral",
  PROYECTO: "Proyecto",
  TRABAJO_ACADEMICO: "Trabajo académico",
};
type EvaluationType = keyof typeof TYPES;
type Level = "AD" | "A" | "B" | "C";
type Component = { tipoEvaluacion: EvaluationType; nombre: string; peso: number };
type Rule = {
  id: number;
  estado: "BORRADOR" | "VIGENTE" | "HISTORICA";
  numeroVersion: number;
  version: number;
  componentes: Component[];
};
type Preview = { promedioPonderado: number; nivelConsolidado: Level };
type Course = { id: number; nombre: string; codigo: string };

export function GradingRulesPanel({ canWrite }: { canWrite: boolean }) {
  const years = usePaged<AcademicYear>("/api/configuracion-academica/anios");
  const [selectedYear, setYear] = useState("");
  const year = years.data?.content.find((item) => String(item.anio) === selectedYear);
  return <section className="panel">
    <PanelHeading title="Reglas académicas"
      summary="Configura cómo se calcula el nivel de logro por competencia en cada curso." />
    <Feedback error={years.error} />
    <div className={styles.scope}>
    {years.loading ? <Loading skeleton="fields">Cargando años académicos…</Loading> :
      <label className="field">Año académico
        <select value={selectedYear} onChange={(e) => setYear(e.target.value)}>
          <option value="">Selecciona un año</option>
          {years.data?.content.map((item) => <option key={item.id} value={item.anio}>{item.anio}</option>)}
        </select>
      </label>}
    {year && <GradeRules key={year.anio} year={year} canWrite={canWrite} />}
    <div className={styles.fullWidth}>
      <Pager data={years.data} loading={years.loading} setPage={(page) => { setYear(""); years.setPage(page); }} />
    </div>
    </div>
  </section>;
}

function GradeRules({ year, canWrite }: { year: AcademicYear; canWrite: boolean }) {
  const [grade, setGrade] = useState<Grade | null>(null);
  return <>
    <label className="field">Grado
      <select value={grade?.id ?? ""} onChange={(e) => {
        setGrade(year.grados.find((item) => String(item.id) === e.target.value) ?? null);
      }}>
        <option value="">Selecciona un grado</option>
        {year.grados.filter((item) => item.activo).map((item) =>
          <option key={item.id} value={item.id}>{item.numero}.° grado</option>)}
      </select>
    </label>
    {grade && <CourseRules key={grade.id} year={year.anio} grade={grade} canWrite={canWrite} />}
  </>;
}

function CourseRules({ year, grade, canWrite }: { year: number; grade: Grade; canWrite: boolean }) {
  const [courses, setCourses] = useState<Course[] | null>(null);
  const [course, setCourse] = useState("");
  const [error, setError] = useState("");
  useEffect(() => {
    let live = true;
    api<{ cursos: Course[] }>(`/api/planes-estudios/${year}/${grade.numero}`)
      .then((plan) => { if (live) setCourses(plan.cursos); })
      .catch((reason) => { if (live) setError(message(reason)); });
    return () => { live = false; };
  }, [year, grade.numero]);

  if (error) return <Feedback error={error} />;
  if (!courses) return <Loading skeleton="fields">Cargando cursos del plan…</Loading>;
  if (!courses.length) return <p>No hay cursos en el plan de este grado.</p>;

  return <>
    <label className="field">Curso
      <select value={course} onChange={(e) => setCourse(e.target.value)}>
        <option value="">Selecciona un curso</option>
        {courses.map((item) => <option key={item.id} value={item.id}>{item.nombre} · {item.codigo}</option>)}
      </select>
    </label>
    {course && <RuleEditor key={course} idCurso={Number(course)} idGrado={grade.id} canWrite={canWrite} />}
  </>;
}

function RuleEditor({ idCurso, idGrado, canWrite }: { idCurso: number; idGrado: number; canWrite: boolean }) {
  const context = `idCurso=${idCurso}&idGrado=${idGrado}`;
  const [history, setHistory] = useState<Rule[] | null>(null);
  const [error, setError] = useState("");
  useEffect(() => {
    let live = true;
    api<Rule[]>(`${PATH}?${context}`)
      .then((items) => { if (live) setHistory(items); })
      .catch((reason) => { if (live) setError(message(reason)); });
    return () => { live = false; };
  }, [context]);

  if (!history) return error ? <Feedback error={error} /> : <Loading skeleton="fields">Cargando reglas…</Loading>;
  const draft = history.find(({ estado }) => estado === "BORRADOR");
  const active = history.find(({ estado }) => estado === "VIGENTE");
  function update(rule: Rule) {
    setHistory((rules) => [rule, ...(rules ?? []).filter((item) => item.id !== rule.id).map((item) =>
      rule.estado === "VIGENTE" && item.estado === "VIGENTE" ? { ...item, estado: "HISTORICA" as const } : item)]);
  }
  return <section className={styles.fullWidth} aria-label="Fórmula del curso">
    {active ? <p><strong>Regla vigente · versión {active.numeroVersion}</strong></p> : <p>Este curso todavía no tiene una regla vigente.</p>}
    {canWrite && <RuleForm key={draft?.id ?? active?.id ?? "nuevo"} draft={draft} active={active}
      idCurso={idCurso} idGrado={idGrado} onUpdate={update} />}
    {history.length > 0 && <>
      <h4>Historial de reglas</h4>
      {history.map((item) => <details key={item.id}>
        <summary>Versión {item.numeroVersion} · {item.estado === "HISTORICA" ? "Histórica" :
          item.estado === "VIGENTE" ? "Vigente" : "Borrador"}</summary>
        <ul>{item.componentes.map((component) => <li key={component.tipoEvaluacion}>
          {component.nombre} · {TYPES[component.tipoEvaluacion]} · {component.peso} %
        </li>)}</ul>
      </details>)}
    </>}
  </section>;
}

type Field = Omit<Component, "peso"> & { peso: string; nivel: Level };
const defaultField: Field = { tipoEvaluacion: "EXAMEN", nombre: "Exámenes", peso: "100", nivel: "A" };
function RuleForm({ draft, active, idCurso, idGrado, onUpdate }: {
  draft?: Rule; active?: Rule; idCurso: number; idGrado: number; onUpdate: (rule: Rule) => void;
}) {
  const [rows, setRows] = useState<Field[]>(() => (draft ?? active)?.componentes.map((item) => ({
    ...item, peso: String(item.peso), nivel: "A",
  })) ?? [defaultField]);
  const [preview, setPreview] = useState<Preview | null>(null);
  const [error, setError] = useState("");
  const [saving, setSaving] = useState(false);
  const [dirty, setDirty] = useState(false);
  const total = rows.reduce((sum, row) => sum + Math.round(Number(row.peso) * 100), 0) / 100;
  const nextType = (Object.keys(TYPES) as EvaluationType[]).find((type) => !rows.some((row) => row.tipoEvaluacion === type));
  const componentes = rows.map(({ tipoEvaluacion, nombre, peso }) => ({ tipoEvaluacion, nombre: nombre.trim(), peso: Number(peso) }));
  const valid = componentes.every(({ nombre, peso }) => nombre && peso > 0 && peso <= 100);

  function change(next: Field[], edited = true) {
    setRows(next);
    if (edited) setDirty(true);
    setPreview(null);
    setError("");
  }

  async function action(operation: () => Promise<void>) {
    if (saving) return;
    setError("");
    setSaving(true);
    try { await operation(); }
    catch (reason) { setError(message(reason)); }
    finally { setSaving(false); }
  }

  function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    void action(async () => {
      const updated = await api<Rule>(`${PATH}/borrador`, {
        method: "PUT", body: JSON.stringify({ idCurso, idGrado, formula: FORMULA, componentes, version: draft?.version }),
      });
      onUpdate(updated);
      setDirty(false);
    });
  }

  return <form className={styles.editor} onSubmit={save}>
    <h4>{draft ? `Editar borrador · versión ${draft.numeroVersion}` : "Preparar una nueva versión"}</h4>
    <p>Promedio ponderado. Cada componente promedia las evaluaciones de su tipo. Los pesos deben sumar 100 %.</p>
    {rows.map((field, index) => <fieldset className={styles.component} key={field.tipoEvaluacion} disabled={saving} aria-label={`Componente ${index + 1}`}>
      <label className="field">Tipo de evaluación
        <select value={field.tipoEvaluacion} onChange={(e) => change(rows.map((row, i) => i === index ?
          { ...row, tipoEvaluacion: e.target.value as EvaluationType } : row))}>
          {Object.entries(TYPES).filter(([type]) => type === field.tipoEvaluacion || !rows.some((row) => row.tipoEvaluacion === type))
            .map(([type, label]) => <option key={type} value={type}>{label}</option>)}
        </select>
      </label>
      <label className="field">Nombre
        <input required maxLength={90} value={field.nombre}
          onChange={(e) => change(rows.map((row, i) => i === index ? { ...row, nombre: e.target.value } : row))} />
      </label>
      <label className="field">Peso (%)
        <input required type="number" min="0.01" max="100" step="0.01" value={field.peso}
          onChange={(e) => change(rows.map((row, i) => i === index ? { ...row, peso: e.target.value } : row))} />
      </label>
      <label className="field">Nivel de ejemplo
        <select value={field.nivel} onChange={(e) => change(rows.map((row, i) => i === index ?
          { ...row, nivel: e.target.value as Level } : row), false)}>
          {["AD", "A", "B", "C"].map((level) => <option key={level}>{level}</option>)}
        </select>
      </label>
      {rows.length > 1 && <button type="button" className="secondary-action"
        onClick={() => change(rows.filter((_, i) => i !== index))}>Quitar componente</button>}
    </fieldset>)}
    <p role="status" className={total === 100 ? styles.complete : styles.incomplete}>
      Peso total: {total} % · {total === 100 ? "Completo" : "Debe sumar 100 %"}
    </p>
    <div className="form-actions">
      <button type="button" className="secondary-action" disabled={!nextType || saving} onClick={() => {
        if (nextType) change([...rows, { tipoEvaluacion: nextType, nombre: TYPES[nextType], peso: "", nivel: "A" }]);
      }}>Agregar componente</button>
      <button type="button" className="secondary-action" disabled={!valid || total !== 100 || saving}
        onClick={() => void action(async () => setPreview(await api<Preview>(`${PATH}/vista-previa`, {
          method: "POST", body: JSON.stringify({ formula: FORMULA, componentes,
            muestra: rows.map(({ tipoEvaluacion, nivel }) => ({ tipoEvaluacion, nivel })) }),
        })))}>Vista previa</button>
      <button className="primary-action" disabled={!valid || saving}>Guardar borrador</button>
      {draft && <button type="button" className="primary-action" disabled={dirty || total !== 100 || saving}
        onClick={() => void action(async () => onUpdate(await api<Rule>(`${PATH}/${draft.id}/activar`, {
          method: "POST", body: JSON.stringify({ version: draft.version }),
        })))}>Activar regla</button>}
    </div>
    <Feedback error={error} />
    {preview && <p role="status">Resultado de ejemplo: {preview.nivelConsolidado} ({preview.promedioPonderado})</p>}
  </form>;
}
