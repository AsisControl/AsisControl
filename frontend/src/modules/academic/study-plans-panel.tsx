"use client";

import { useEffect, useId, useRef, useState, type FormEvent } from "react";
import { api, apiBlob, message } from "@/core/api/client";
import { usePaged } from "@/shared/hooks/use-paged";
import { saveBlob } from "@/shared/lib/save-blob";
import { countLabel } from "@/shared/lib/formatters";
import { Feedback, Loading, Pager, PanelHeading, StatusPill } from "@/shared/ui";
import { BulkImportDialog } from "@/modules/bulk-import";
import type { AcademicYear } from "@/modules/academic-config";
import { PencilIcon } from "./components/pencil-icon";
import { ExceededSections, type ExceededSection } from "./exceeded-sections";
import { PlanCourseCompetencies } from "./plan-course-competencies";
import { PlanCourseList, type DraftCourse, type PlanCourse } from "./plan-course-list";
import { PreviousPlanImport } from "./previous-plan-import";
import styles from "./study-plans.module.css";

type Plan = {
  anio: number;
  grado: number;
  estadoAnio: "BORRADOR" | "ACTIVO" | "CERRADO";
  definido: boolean;
  editable: boolean;
  version: number;
  cursos: PlanCourse[];
  /** OBS-01: secciones cuyo horario supera las horas del plan; se informan sin bloquear el guardado. */
  seccionesExcedidas?: ExceededSection[];
};

export function StudyPlansPanel({ canWrite }: { canWrite: boolean }) {
  const years = usePaged<AcademicYear>("/api/configuracion-academica/anios");
  const [selectedYear, setSelectedYear] = useState("");
  const [selectedGrade, setSelectedGrade] = useState("");
  const [downloading, setDownloading] = useState(false);
  const [downloadError, setDownloadError] = useState("");
  const [importing, setImporting] = useState(false);
  const [imports, setImports] = useState(0);
  const year = years.data?.content.find(item => String(item.anio) === selectedYear);

  async function downloadPlan(anio: number) {
    setDownloading(true);
    setDownloadError("");
    try {
      const pdf = await apiBlob(`/api/planes-estudios/${anio}/pdf`, { timeoutMs: 60000 });
      saveBlob(pdf, `Plan_de_Estudios_AsisControl_${anio}.pdf`);
    } catch (cause) {
      setDownloadError(message(cause));
    } finally {
      setDownloading(false);
    }
  }

  return (
    <section className="panel">
      <PanelHeading title="Plan de estudios" summary="Define los cursos que se dictan por grado y año académico."
        actions={<>
          {canWrite && <button type="button" className="secondary-action" onClick={() => setImporting(true)}>
            Carga masiva
          </button>}
          <button type="button" className="secondary-action" disabled={!year || downloading || years.loading}
            onClick={() => year && void downloadPlan(year.anio)}>
            Descargar plan de estudios (PDF)
          </button>
        </>} />
      {importing && <BulkImportDialog
        title="Carga masiva del plan de estudios"
        description="Descarga la plantilla y completa una fila por curso, año y grado. Los cursos nuevos se registran en el catálogo."
        path="/api/planes-estudios/importaciones"
        templateName="plantilla_plan_estudios.xlsx"
        fileLabel="Archivo Excel del plan de estudios"
        onClose={() => setImporting(false)}
        onImported={() => setImports(value => value + 1)} />}
      <Feedback error={years.error || downloadError} />
      {downloading && <p role="status">Preparando el PDF del año académico…</p>}
      {years.loading ? <Loading skeleton="fields">Cargando años académicos…</Loading> : <div className={styles.context}>
        <label className="field">Año académico
          <select value={selectedYear} onChange={event => {
            setSelectedYear(event.target.value);
            setSelectedGrade("");
            setDownloadError("");
          }}>
            <option value="">Selecciona un año</option>
            {years.data?.content.map(item => <option key={item.id} value={item.anio}>{item.anio}</option>)}
          </select>
        </label>
        {year && <label className="field">Grado
          <select value={selectedGrade} onChange={event => setSelectedGrade(event.target.value)}>
            <option value="">Selecciona un grado</option>
            {year.grados.filter(item => item.activo).map(item =>
              <option key={item.id} value={item.numero}>{item.numero}.° grado</option>
            )}
          </select>
        </label>}
      </div>}
      {!!years.data && years.data.totalPages > 1 && <Pager data={years.data} loading={years.loading} setPage={page => {
        setSelectedYear("");
        setSelectedGrade("");
        setDownloadError("");
        years.setPage(page);
      }} />}
      {year && <PlanName key={year.anio} year={year.anio} canWrite={canWrite} />}
      {year && selectedGrade && <PlanEditor
        key={`${year.anio}-${selectedGrade}-${imports}`}
        path={`/api/planes-estudios/${year.anio}/${selectedGrade}`}
        canWrite={canWrite}
      />}
    </section>
  );
}

function PlanName({ year, canWrite }: { year: number; canWrite: boolean }) {
  type Name = { nombre: string; version: number };
  const path = `/api/planes-estudios/${year}/nombre`;
  const [record, setRecord] = useState<Name | null>(null);
  const [value, setValue] = useState("");
  const [error, setError] = useState("");
  const [saved, setSaved] = useState(false);
  const [busy, setBusy] = useState(false);
  useEffect(() => {
    let active = true;
    api<Name>(path).then(item => {
      if (active) { setRecord(item); setValue(item.nombre); }
    }).catch(cause => { if (active) setError(message(cause)); });
    return () => { active = false; };
  }, [path]);

  async function rename(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!record || busy) return;
    setBusy(true);
    setError("");
    setSaved(false);
    try {
      const updated = await api<Name>(path, { method: "PUT", body: JSON.stringify({ version: record.version, nombre: value.trim() }) });
      setRecord(updated);
      setValue(updated.nombre);
      setSaved(true);
    } catch (cause) {
      setError(message(cause));
    } finally {
      setBusy(false);
    }
  }

  return <section className={styles.planName} aria-label="Identificación del plan anual">
    <Feedback error={error} success={saved ? "Se guardó el nombre del plan." : ""} />
    {record && (canWrite ? <form className={styles.nameForm} onSubmit={rename}>
      <label className="field">Nombre del plan anual
        <input required maxLength={100} value={value} disabled={busy} onChange={event => {
          setValue(event.target.value);
          setSaved(false);
        }} />
      </label>
      <button className="secondary-action" disabled={busy || !value.trim() || value.trim() === record.nombre}>Guardar nombre</button>
    </form> : <p>{record.nombre}</p>)}
  </section>;
}

function PlanEditor({ path, canWrite }: { path: string; canWrite: boolean }) {
  const [plan, setPlan] = useState<Plan | null>(null);
  const [selection, setSelection] = useState<DraftCourse[]>([]);
  const [editing, setEditing] = useState(false);
  const [showCatalog, setShowCatalog] = useState(false);
  const [showPrevious, setShowPrevious] = useState(false);
  const [competencyCourseId, setCompetencyCourseId] = useState<number | null>(null);
  const [reason, setReason] = useState("");
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");
  const [busy, setBusy] = useState(false);
  const catalogId = useId();
  const previousId = useId();
  const reasonHintId = useId();
  const courseTriggerPrefix = useId();
  const catalogButton = useRef<HTMLButtonElement>(null);
  const previousButton = useRef<HTMLButtonElement>(null);
  useEffect(() => { if (editing) catalogButton.current?.focus(); }, [editing]);
  useEffect(() => {
    let active = true;
    api<Plan>(path).then(data => {
      if (!active) return;
      setPlan(data);
      setSelection(data.cursos);
    }).catch(cause => { if (active) setError(message(cause)); });
    return () => { active = false; };
  }, [path]);

  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!plan || !editable || busy || !canSave) return;
    setBusy(true);
    setError("");
    setSuccess("");
    try {
      const data = await api<Plan>(path, {
        method: "PUT",
        body: JSON.stringify({
          version: plan.version,
          cursos: selection.map(({ id, nombre, horasSemanales, competencias }) => ({
            id, nombre: nombre.trim(), horasSemanales, competenciasIds: competencias?.map(item => item.id),
          })),
          ...(plan.definido ? { motivo: reason.trim() } : {}),
        }),
      });
      setPlan(data);
      setSelection(data.cursos);
      setEditing(false);
      setShowCatalog(false);
      setShowPrevious(false);
      setCompetencyCourseId(null);
      setReason("");
      setSuccess(plan.definido ? "Se guardaron los cambios del plan." : "Se guardó el plan de estudios.");
    } catch (cause) {
      setError(message(cause));
    } finally {
      setBusy(false);
    }
  }

  if (!plan) return error ? <Feedback error={error} /> : <Loading skeleton="table" columns={4} rows={5}>Cargando plan de estudios…</Loading>;
  const permitted = canWrite && plan.editable;
  const editable = permitted && (editing || !plan.definido);
  // inscripcion cursos: en el año activo el grado conserva sus cursos; solo cambian nombre, horas y competencias
  const coursesLocked = plan.estadoAnio === "ACTIVO" && plan.definido;
  const selectedIds = new Set(selection.map(course => course.id));
  const original = new Map(plan.cursos.map(course => [course.id, course]));
  const changed = selection.length !== plan.cursos.length || selection.some(course => {
    const previous = original.get(course.id);
    return !previous || previous.nombre !== course.nombre.trim() || previous.horasSemanales !== course.horasSemanales
      || (course.competencias?.length ?? 0) !== (previous.competencias?.length ?? 0)
      || course.competencias?.some(item => !previous.competencias?.some(value => value.id === item.id));
  });
  const validCourses = selection.every(course => course.nombre.trim()
    && typeof course.horasSemanales === "number" && Number.isInteger(course.horasSemanales)
    && course.horasSemanales >= 1 && course.horasSemanales <= 40);
  const canSave = validCourses && (plan.estadoAnio !== "ACTIVO" || selection.length > 0)
    && (!plan.definido || (changed && !!reason.trim()));
  const status = plan.estadoAnio === "CERRADO" ? "Año cerrado" : plan.definido ? "Plan guardado" : "Pendiente de guardar";
  const totalHours = selection.reduce((total, course) => total + (Number(course.horasSemanales) || 0), 0);
  const competencyCourse = selection.find(course => course.id === competencyCourseId);

  function closeCompetencies() {
    setCompetencyCourseId(null);
    document.getElementById(`${courseTriggerPrefix}-${competencyCourseId}`)?.focus();
  }

  function cancelEdit() {
    setEditing(false);
    setShowCatalog(false);
    setShowPrevious(false);
    setCompetencyCourseId(null);
    setSelection(plan?.cursos ?? []);
    setReason("");
    setError("");
  }

  function addCourses(courses: PlanCourse[]) {
    setSelection(current => {
      const existing = new Set(current.map(course => course.id));
      return [...current, ...courses.filter(course => !existing.has(course.id))];
    });
  }

  return (
    <form className={styles.plan} onSubmit={save}>
      <fieldset className={styles.editor} disabled={busy} aria-label="Cursos del plan">
        <div className={styles.sectionHeader}>
          <div>
            <div className={styles.sectionTitle}>
              <h4>Cursos del plan</h4>
              <StatusPill state={plan.estadoAnio} label={status} tone={plan.definido && plan.estadoAnio !== "CERRADO" ? "success" : "neutral"} />
            </div>
            <p>{countLabel(selection.length, "curso", "cursos")} · {countLabel(totalHours, "hora semanal", "horas semanales")}</p>
          </div>
          {editable && !coursesLocked && (
            <div className={styles.sectionActions}>
              <button ref={previousButton} type="button" className="secondary-action" aria-expanded={showPrevious}
                aria-controls={showPrevious ? previousId : undefined} onClick={() => {
                  setShowCatalog(false);
                  setShowPrevious(open => !open);
                }}>
                {showPrevious ? "Ocultar plan anterior" : "Importar plan del año anterior"}
              </button>
              <button ref={catalogButton} type="button" className="secondary-action" aria-expanded={showCatalog}
                aria-controls={showCatalog ? catalogId : undefined} onClick={() => {
                  setShowPrevious(false);
                  setShowCatalog(open => !open);
                }}>
                {showCatalog ? "Ocultar catálogo" : "Agregar curso"}
              </button>
            </div>
          )}
          {permitted && !editable && (
            <button type="button" className="secondary-action" onClick={() => {
              setEditing(true);
              setSuccess("");
            }}>
              <PencilIcon /> Editar plan
            </button>
          )}
        </div>
        {plan.estadoAnio === "CERRADO" && <p className={styles.note}>El contenido de este plan histórico es de solo lectura.</p>}
        {coursesLocked && permitted && <p className={styles.note}>Los cursos del plan del año en curso no se modifican, porque todos los alumnos del grado llevan los mismos cursos durante el año. Para agregar o quitar cursos, edita el plan del próximo año.</p>}
        <Feedback error={error} success={success} />
        <ExceededSections sections={plan.seccionesExcedidas ?? []} />
        {showPrevious && editable && <PreviousPlanImport id={previousId} path={path} grade={plan.grado}
          selected={selectedIds} onAdd={items => {
            addCourses(items);
            setShowPrevious(false);
            previousButton.current?.focus();
            setSuccess(`${countLabel(items.length, "curso agregado", "cursos agregados")} a la selección. Guarda el plan para aplicar los cambios.`);
          }} />}
        {showCatalog && editable && (
          <Catalog id={catalogId} anio={plan.anio} grado={plan.grado} selected={selectedIds}
            onClose={() => { setShowCatalog(false); catalogButton.current?.focus(); }}
            onAdd={course => addCourses([course])} />
        )}
        {selection.length === 0
          ? <p className={styles.empty}>{editable ? "Agrega cursos del catálogo para armar el plan del grado." : "No hay cursos registrados en este plan."}</p>
          : <PlanCourseList courses={selection} label="Cursos seleccionados" onEdit={editable ? updated =>
            setSelection(current => current.map(course => course.id === updated.id ? updated : course)) : undefined}
            action={course => <div className={styles.courseActions}>
              <button type="button" id={`${courseTriggerPrefix}-${course.id}`} className="secondary-action"
                aria-expanded={competencyCourseId === course.id} aria-label={`Competencias de ${course.nombre || course.codigo}`}
                onClick={() => competencyCourseId === course.id ? closeCompetencies() : setCompetencyCourseId(course.id)}>
                {course.competencias ? countLabel(course.competencias.length, "competencia", "competencias") : "Competencias"}
              </button>
              {editable && !coursesLocked && <button type="button" className="secondary-action" aria-label={`Quitar ${course.nombre || course.codigo}`}
                onClick={() => {
                  if (course.id === competencyCourseId) setCompetencyCourseId(null);
                  setSelection(current => current.filter(item => item.id !== course.id));
                }}>Quitar</button>}
            </div>} />}
        {competencyCourse && <PlanCourseCompetencies key={competencyCourse.id} course={competencyCourse} onClose={closeCompetencies}
          onChange={editable ? competencias => setSelection(current => current.map(course =>
            course.id === competencyCourse.id ? { ...course, competencias } : course)) : undefined} />}
        {editable && <div className={styles.saveArea}>
          {selection.length > 0 && <p className={styles.hint}>El nombre, las horas y las competencias de cada curso se guardan para este año y grado.</p>}
          {!validCourses && <p className="field-error">Cada curso necesita un nombre y entre 1 y 40 horas por semana.</p>}
          {plan.definido && <div>
            <label className="field">Motivo de la modificación
              <textarea value={reason} onChange={event => setReason(event.target.value)} maxLength={500} required
                rows={2} placeholder="Ej. Se corrigió el curso asignado al grado." aria-describedby={reasonHintId} />
            </label>
            <span id={reasonHintId} className={styles.hint}>El motivo se registrará en Administración → Auditoría.</span>
          </div>}
          <div className={styles.saveActions}>
            {plan.definido && <button className="secondary-action" type="button" onClick={cancelEdit}>Cancelar</button>}
            <button className="primary-action" type="submit" disabled={!canSave}>
              {busy ? "Guardando…" : plan.definido ? "Guardar cambios" : "Guardar plan"}
            </button>
          </div>
        </div>}
      </fieldset>
    </form>
  );
}

function Catalog({ id, anio, grado, selected, onAdd, onClose }: {
  id: string;
  anio: number;
  grado: number;
  selected: Set<number>;
  onAdd: (course: PlanCourse) => void;
  onClose: () => void;
}) {
  const [filter, setFilter] = useState("");
  const [search, setSearch] = useState("");
  const [scope, setScope] = useState("all");
  const searchBox = useRef<HTMLInputElement>(null);
  const filters = new URLSearchParams();
  if (search) filters.set("busqueda", search);
  if (scope === "grade") { filters.set("anio", String(anio)); filters.set("grado", String(grado)); }
  const catalog = usePaged<PlanCourse>("/api/cursos", "", filters.toString(), 5);
  const { setPage } = catalog;
  const searching = catalog.loading || filter.trim() !== search;
  useEffect(() => { searchBox.current?.focus(); }, []);
  useEffect(() => {
    const next = filter.trim();
    if (next === search) return;
    const timer = setTimeout(() => { setPage(0); setSearch(next); }, 250);
    return () => clearTimeout(timer);
  }, [filter, search, setPage]);
  return (
    <section id={id} className={styles.catalog} aria-label="Catálogo de cursos"
      onKeyDown={event => { if (event.key === "Escape") onClose(); }}>
      <h5>Catálogo de cursos</h5>
      <div className={styles.filters}>
        <label className="field">Buscar por nombre o código
          <input type="search" ref={searchBox} value={filter} onChange={event => setFilter(event.target.value)}
            onKeyDown={event => { if (event.key === "Enter") event.preventDefault(); }} />
        </label>
        <label className="field">Mostrar cursos
          <select value={scope} onChange={event => { setPage(0); setScope(event.target.value); }}>
            <option value="all">Todo el catálogo</option>
            <option value="grade">Registrados para este año y grado</option>
          </select>
        </label>
      </div>
      <Feedback error={catalog.error} />
      <div className={styles.catalogResults} aria-busy={searching}>
        {searching ? <Loading skeleton="table" columns={3} rows={3}>Buscando cursos…</Loading> : !catalog.error && <>
          {catalog.data?.content.length ? <>
            <PlanCourseList courses={catalog.data.content} label="Resultados del catálogo" action={course => {
              const added = selected.has(course.id);
              return <button type="button" className="secondary-action" aria-disabled={added}
                aria-label={added ? `${course.nombre} ya está en el plan` : `Agregar ${course.nombre}`}
                onClick={() => { if (!added) onAdd(course); }}>
                {added ? "En el plan" : "Agregar"}
              </button>;
            }} />
            <Pager data={catalog.data} loading={searching} setPage={setPage} />
          </> : <p className={styles.empty}>No hay cursos que coincidan con estos filtros.</p>}
        </>}
      </div>
    </section>
  );
}
