"use client";

import { useEffect, useState, type FormEvent } from "react";
import { api, message } from "@/core/api/client";
import { usePaged } from "@/shared/hooks/use-paged";
import { Dialog, Feedback, Loading, Pager, PanelHeading } from "@/shared/ui";
import type { AcademicYear, Grade } from "@/modules/academic-config";
import { InstitutionalScalePanel } from "./institutional-scale-panel";
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
type Preview = {
  promedioPonderado: number | null;
  notaRedondeada: number | null;
  nivelConsolidado: Level | null;
  numeroVersionEscala: number;
  componentesFaltantes: string[];
};
type Course = { id: number; nombre: string; codigo: string };
const STATES = { BORRADOR: "Borrador", VIGENTE: "Vigente", HISTORICA: "Histórica" };
const numberFormat = new Intl.NumberFormat("es-PE", { maximumFractionDigits: 2 });
const formatWeight = (peso: number) => `${numberFormat.format(peso)} %`;
const HISTORY_PAGE_SIZE = 5;

export function GradingRulesPanel({ canWrite }: { canWrite: boolean }) {
  const years = usePaged<AcademicYear>("/api/configuracion-academica/anios");
  const [selectedYear, setYear] = useState("");
  const year = years.data?.content.find((item) => String(item.anio) === selectedYear);
  return <section className="panel">
    <PanelHeading title="Reglas académicas"
      summary="Configura cómo se calcula el nivel de logro por competencia en cada curso." />
    <InstitutionalScalePanel canWrite={canWrite} />
    <header className={styles.courseHeading}>
      <h4>Reglas de evaluación por curso</h4>
      <p>Define el peso de los componentes utilizados para consolidar cada competencia.</p>
    </header>
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
      {(years.data?.totalPages ?? 0) > 1 && <div className={styles.fullWidth}>
        <Pager data={years.data} loading={years.loading} setPage={(page) => { setYear(""); years.setPage(page); }} />
      </div>}
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
  const [notice, setNotice] = useState("");
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
    setNotice(rule.estado === "VIGENTE" ? `Versión ${rule.numeroVersion} activada.` : `Borrador de la versión ${rule.numeroVersion} guardado.`);
    setHistory((rules) => [rule, ...(rules ?? []).filter((item) => item.id !== rule.id).map((item) =>
      rule.estado === "VIGENTE" && item.estado === "VIGENTE" ? { ...item, estado: "HISTORICA" as const } : item)]);
  }
  return <section className={`${styles.fullWidth} ${styles.workspace}`} aria-label="Fórmula del curso">
    {active && <section className={styles.card} aria-labelledby="regla-vigente">
      <div className={styles.sectionHeading}>
        <h4 id="regla-vigente">{active ? `Regla vigente · versión ${active.numeroVersion}` : "Regla vigente"}</h4>
        {active && <RuleStatus estado={active.estado} />}
      </div>
      <ComponentTable components={active.componentes} />
    </section>}
    {!active && !canWrite && <p>Este curso todavía no tiene una regla vigente.</p>}
    <Feedback success={notice} />
    {canWrite && <RuleForm key={active?.id ?? "nuevo"} draft={draft} active={active}
      idCurso={idCurso} idGrado={idGrado} onUpdate={update} bases={history.filter(item => item.estado === "HISTORICA")} />}
    {history.length > 0 && <RuleHistory history={history} />}
  </section>;
}

function RuleStatus({ estado }: { estado: Rule["estado"] }) {
  return <span className={styles.badge} data-state={estado}>{STATES[estado]}</span>;
}

function ComponentTable({ components }: { components: Component[] }) {
  return <table className={styles.componentsTable}>
    <thead><tr><th scope="col">Componente</th><th scope="col">Peso</th></tr></thead>
    <tbody>{components.map(({ tipoEvaluacion, nombre, peso }) => <tr key={tipoEvaluacion}>
      <th scope="row">{nombre}{nombre !== TYPES[tipoEvaluacion] && <small>{TYPES[tipoEvaluacion]}</small>}</th>
      <td>{formatWeight(peso)}</td>
    </tr>)}</tbody>
  </table>;
}

function RuleHistory({ history }: { history: Rule[] }) {
  const [index, setIndex] = useState(0);
  const totalPages = Math.ceil(history.length / HISTORY_PAGE_SIZE);
  const page = Math.min(index, totalPages - 1);
  const content = history.slice(page * HISTORY_PAGE_SIZE, (page + 1) * HISTORY_PAGE_SIZE);
  return <section className={styles.card} aria-labelledby="historial-reglas">
    <h4 id="historial-reglas">Historial de reglas</h4>
    <div className={styles.history}>{content.map((item) => <details key={item.id}>
      <summary><strong>Versión {item.numeroVersion}</strong><RuleStatus estado={item.estado} /></summary>
      <ComponentTable components={item.componentes} />
    </details>)}</div>
    {totalPages > 1 && <Pager data={{ content, page, totalPages, totalElements: history.length,
      first: page === 0, last: page + 1 === totalPages }} loading={false} setPage={setIndex} />}
  </section>;
}

type Field = Omit<Component, "peso"> & { peso: string };
const defaultField: Field = { tipoEvaluacion: "EXAMEN", nombre: "Exámenes", peso: "100" };
const toFields = (components: Component[]) => components.map((item) => ({ ...item, peso: String(item.peso) }));
function RuleForm({ draft, active, idCurso, idGrado, onUpdate, bases }: {
  draft?: Rule; active?: Rule; bases: Rule[]; idCurso: number; idGrado: number; onUpdate: (rule: Rule) => void;
}) {
  const [rows, setRows] = useState<Field[]>(() => draft ? toFields(draft.componentes) : [defaultField]);
  const [started, setStarted] = useState(Boolean(draft));
  const [discard, setDiscard] = useState(false);
  const [base, setBase] = useState<Rule>();
  function closeEditor() {
    setRows(draft ? toFields(draft.componentes) : [defaultField]);
    setDirty(false);
    setError("");
    setDiscard(false);
    setStarted(false);
  }
  const [activationReason, setActivationReason] = useState("");
  const [modal, setModal] = useState<"preview" | "activate" | null>(null);
  const [error, setError] = useState("");
  const [saving, setSaving] = useState(false);
  const [dirty, setDirty] = useState(false);
  const cents = rows.reduce((sum, row) => sum + Math.round(Number(row.peso) * 100), 0);
  const total = cents / 100;
  const nextType = (Object.keys(TYPES) as EvaluationType[]).find((type) => !rows.some((row) => row.tipoEvaluacion === type));
  const componentes = rows.map(({ tipoEvaluacion, nombre, peso }) => ({ tipoEvaluacion, nombre: nombre.trim(), peso: Number(peso) }));
  const valid = componentes.every(({ nombre, peso }) =>
    nombre && peso >= 0.01 && peso <= 100 && Number(peso.toFixed(2)) === peso);
  const ready = valid && cents === 10000;
  const canActivate = Boolean(draft) && !dirty && ready && !saving;

  function change(next: Field[]) {
    setRows(next);
    setDirty(true);
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
    if (!valid) return;
    void action(async () => {
      const updated = await api<Rule>(`${PATH}/borrador`, {
        method: "PUT", body: JSON.stringify({ idCurso, idGrado, formula: FORMULA, componentes, version: draft?.version }),
      });
      onUpdate(updated);
      setRows(toFields(updated.componentes));
      setDirty(false);
    });
  }

  function copyHistorical(rule: Rule) {
    change(toFields(rule.componentes));
    setBase(undefined);
    setStarted(true);
  }

  if (!started) return <section className={styles.card} aria-label="Nueva versión">
    <h4>{active ? "Preparar una nueva versión" : "Este curso todavía no tiene una regla vigente"}</h4>
    <div className={styles.actions}>
      <button type="button" className="secondary-action" onClick={() => setStarted(true)}>{draft ? "Continuar borrador" : active ? "Empezar desde cero" : "Crear regla"}</button>
      {active && !draft && <button type="button" className="secondary-action" onClick={() => {
        change(toFields(active.componentes));
        setStarted(true);
      }}>Copiar regla vigente</button>}
    </div>
    {!!bases.length && <div className={styles.actions}>
      {bases.map(rule => <button type="button" key={rule.id} className="secondary-action"
        onClick={() => draft ? setBase(rule) : copyHistorical(rule)}>Usar versión {rule.numeroVersion} como base</button>)}
    </div>}
    {base && <Dialog title="¿Reemplazar los componentes del borrador?" role="alertdialog" onClose={() => setBase(undefined)}>
      <p>El borrador guardado se actualizará cuando guardes la copia.</p>
      <div className={styles.confirmActions}>
        <button type="button" onClick={() => setBase(undefined)}>Cancelar</button>
        <button type="button" className="primary-action" onClick={() => copyHistorical(base)}>Usar como base</button>
      </div>
    </Dialog>}
  </section>;

  const totalMessage = !valid ? "Completa los componentes y revisa sus pesos." : cents === 10000 ? ""
    : `${cents < 10000 ? "Falta" : "Excede en"} ${formatWeight(Math.abs(cents - 10000) / 100)}.`;

  return <section className={styles.card} aria-label="Editor de borrador">
    <div className={styles.sectionHeading}>
      <h4>{draft ? `Versión ${draft.numeroVersion} en edición` : "Nueva versión"}</h4>
      {draft ? <RuleStatus estado={draft.estado} /> : <span className={styles.badge}>Sin guardar</span>}
    </div>
    <form className={styles.editor} onSubmit={save}>
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
      {rows.length > 1 && <button type="button" className={styles.removeComponent}
        aria-label={`Quitar componente ${field.nombre || TYPES[field.tipoEvaluacion]}`} title="Quitar componente"
        onClick={() => change(rows.filter((_, i) => i !== index))}>
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden="true">
          <path d="M6 6l12 12M18 6L6 18" />
        </svg>
      </button>}
    </fieldset>)}
    <div className={styles.weight} data-total={ready ? "correcto" : cents > 10000 ? "exceso" : "faltante"}>
      <p role="status"><strong>Peso total: {formatWeight(total)} de 100 %</strong>{totalMessage && <span>{totalMessage}</span>}</p>
      <progress aria-label="Peso acumulado" aria-valuetext={`${formatWeight(total)} de 100 %`}
        value={Math.min(100, Math.max(0, total))} max={100} />
    </div>
    <div className={styles.actions}>
      <button type="button" className="secondary-action" disabled={saving} onClick={() => dirty ? setDiscard(true) : closeEditor()}>Cerrar editor</button>
      <button type="button" className="secondary-action" disabled={!nextType || saving} onClick={() => {
        if (nextType) change([...rows, { tipoEvaluacion: nextType, nombre: TYPES[nextType], peso: "" }]);
      }}>Agregar componente</button>
      <button type="button" className="secondary-action" disabled={!ready || saving}
        onClick={() => setModal("preview")}>Vista previa</button>
      <button className="primary-action" disabled={!valid || saving}>Guardar borrador</button>
      {draft && <button type="button" className="primary-action" disabled={!canActivate}
        onClick={() => { setError(""); setActivationReason(""); setModal("activate"); }}>Activar regla</button>}
    </div>
    {draft && dirty && <p className="hint">Guarda los cambios antes de activar la regla.</p>}
    {modal !== "activate" && <Feedback error={error} />}
    </form>
    {modal === "preview" && <RulePreview components={componentes} onClose={() => setModal(null)} />}
    {discard && <Dialog title="¿Descartar cambios de la regla?" role="alertdialog" onClose={() => setDiscard(false)}>
      <div className={styles.confirmActions}>
        <button type="button" onClick={() => setDiscard(false)}>Seguir editando</button>
        <button type="button" className="primary-action" onClick={closeEditor}>Descartar cambios</button>
      </div>
    </Dialog>}
    {modal === "activate" && draft && <Dialog title="Activar regla de calificación" role="alertdialog"
      description={`La versión ${draft.numeroVersion} se aplicará a los próximos resultados de este curso. Las libretas ya publicadas conservarán sus pesos.`}
      busy={saving} onClose={() => { setError(""); setModal(null); }}>
      <label className="field">Motivo de activación
        <textarea required rows={2} maxLength={300} value={activationReason} disabled={saving}
          onChange={event => setActivationReason(event.target.value)} />
      </label>
      <Feedback error={error} />
      <div className={styles.confirmActions}>
        <button type="button" disabled={saving} onClick={() => { setError(""); setModal(null); }}>Cancelar</button>
        <button type="button" className="primary-action" disabled={!canActivate || !activationReason.trim()} onClick={() => void action(async () => {
          const updated = await api<Rule>(`${PATH}/${draft.id}/activar`, {
            method: "POST", body: JSON.stringify({ version: draft.version, motivo: activationReason.trim() }),
          });
          setModal(null);
          onUpdate(updated);
        })}>{saving ? "Activando…" : "Confirmar activación"}</button>
      </div>
    </Dialog>}
  </section>;
}

function RulePreview({ components, onClose }: { components: Component[]; onClose: () => void }) {
  const [grades, setGrades] = useState<Partial<Record<EvaluationType, string>>>({});
  const [result, setResult] = useState<Preview | null>(null);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  async function simulate(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (busy) return;
    setBusy(true);
    setError("");
    setResult(null);
    try {
      setResult(await api<Preview>(`${PATH}/vista-previa-numerica`, {
        method: "POST", body: JSON.stringify({ formula: FORMULA, componentes: components,
          muestra: components.map(({ tipoEvaluacion }) => ({ tipoEvaluacion, nota: grades[tipoEvaluacion] || null })) }),
      }));
    } catch (reason) { setError(message(reason)); }
    finally { setBusy(false); }
  }

  return <Dialog title="Vista previa de la regla"
    description="Usa notas hipotéticas de 0 a 20 para simular el resultado. La simulación no modifica las calificaciones reales."
    busy={busy} onClose={onClose}>
    <form onSubmit={simulate} className={styles.editor}>
      <fieldset disabled={busy} className={styles.sample}>
        <legend>Notas hipotéticas por componente</legend>
        {components.map(({ tipoEvaluacion, nombre, peso }) => <div key={tipoEvaluacion} className={styles.sampleRow}>
          <div><strong>{nombre}</strong><span>{formatWeight(peso)}</span></div>
          <label className="field">Nota hipotética
            <input type="number" min="0" max="20" step="any" aria-label={`Nota hipotética de ${nombre}`}
              value={grades[tipoEvaluacion] ?? ""} onChange={(event) => {
              setGrades({ ...grades, [tipoEvaluacion]: event.target.value });
              setResult(null);
              setError("");
            }} />
          </label>
        </div>)}
      </fieldset>
      <Feedback error={error} />
      {result && <section className={styles.result} aria-label="Resultado simulado" aria-live="polite">
        <h4>Resultado simulado</h4>
        <p>Escala institucional v{result.numeroVersionEscala}</p>
        {result.promedioPonderado === null ? <p>Resultado pendiente. Faltan notas en {result.componentesFaltantes.join(", ")}.</p> : <dl>
          <div><dt>Nivel de logro</dt><dd>{result.nivelConsolidado}</dd></div>
          <div><dt>Promedio ponderado</dt><dd>{numberFormat.format(result.promedioPonderado)}</dd></div>
          <div><dt>Nota redondeada</dt><dd>{result.notaRedondeada}</dd></div>
        </dl>}
      </section>}
      <div className={styles.confirmActions}>
        <button type="button" disabled={busy} onClick={onClose}>Cerrar</button>
        <button className="primary-action" disabled={busy}>{busy ? "Simulando…" : "Simular resultado"}</button>
      </div>
    </form>
  </Dialog>;
}
