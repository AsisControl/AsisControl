"use client";

import { useEffect, useState, type FormEvent } from "react";
import { api, apiBlob, message } from "@/core/api/client";
import { usePaged } from "@/shared/hooks/use-paged";
import { saveBlob } from "@/shared/lib/save-blob";
import { Feedback, Loading, Pager, PanelHeading } from "@/shared/ui";
import type { AcademicYear } from "@/modules/academic-config";
import type { Course } from "./types";

type Plan = {
  editable: boolean;
  inicializacion: boolean;
  version: number;
  cursos: Pick<Course, "id" | "codigo" | "nombre">[];
};

export function StudyPlansPanel({ canWrite }: { canWrite: boolean }) {
  const years = usePaged<AcademicYear>("/api/configuracion-academica/anios");
  const [selectedYear, setSelectedYear] = useState("");
  const [selectedGrade, setSelectedGrade] = useState("");
  const [downloading, setDownloading] = useState(false);
  const [downloadError, setDownloadError] = useState("");
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
        actions={<button type="button" className="secondary-action" disabled={!year || downloading || years.loading}
          onClick={() => year && void downloadPlan(year.anio)}>
          Descargar plan de estudios (PDF)
        </button>} />
      <Feedback error={years.error || downloadError} />
      {downloading && <p role="status">Preparando el PDF del año académico…</p>}
      {years.loading ? <Loading skeleton="fields">Cargando años académicos…</Loading> : <label className="field">Año académico
        <select value={selectedYear} onChange={event => {
          setSelectedYear(event.target.value);
          setSelectedGrade("");
          setDownloadError("");
        }}>
          <option value="">Selecciona un año</option>
          {years.data?.content.map(item => <option key={item.id} value={item.anio}>{item.anio}</option>)}
        </select>
      </label>}
      <Pager data={years.data} loading={years.loading} setPage={page => {
        setSelectedYear("");
        setSelectedGrade("");
        setDownloadError("");
        years.setPage(page);
      }} />
      {year && <label className="field">Grado
        <select value={selectedGrade} onChange={event => setSelectedGrade(event.target.value)}>
          <option value="">Selecciona un grado</option>
          {year.grados.filter(item => item.activo).map(item =>
            <option key={item.id} value={item.numero}>{item.numero}.° grado</option>
          )}
        </select>
      </label>}
      {year && selectedGrade && <PlanEditor
        key={`${year.anio}-${selectedGrade}`}
        path={`/api/planes-estudios/${year.anio}/${selectedGrade}`}
        canWrite={canWrite}
      />}
    </section>
  );
}

function PlanEditor({ path, canWrite }: { path: string; canWrite: boolean }) {
  const [plan, setPlan] = useState<Plan | null>(null);
  const [selection, setSelection] = useState<Plan["cursos"]>([]);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");
  const [busy, setBusy] = useState(false);
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
    if (!plan) return;
    setBusy(true);
    setError("");
    setSuccess("");
    try {
      const data = await api<Plan>(path, {
        method: "PUT",
        body: JSON.stringify({ version: plan.version, cursosIds: selection.map(item => item.id) }),
      });
      setPlan(data);
      setSelection(data.cursos);
      setSuccess("Se guardó el plan de estudios.");
    } catch (cause) {
      setError(message(cause));
    } finally {
      setBusy(false);
    }
  }

  if (!plan) return error ? <Feedback error={error} /> : <Loading skeleton="table" columns={4} rows={5}>Cargando plan de estudios…</Loading>;
  const editable = canWrite && plan.editable;
  return (
    <form onSubmit={save}>
      <Feedback error={error} success={success} />
      {!plan.editable && <p>El plan del año activo o cerrado es de solo lectura.</p>}
      {plan.inicializacion && editable && <p>Registro inicial. Al guardar, el plan quedará en solo lectura.</p>}
      <fieldset disabled={busy}>
        <legend>Cursos del plan</legend>
        {!selection.length && <p>No hay cursos registrados en este plan.</p>}
        {selection.length > 0 && <ul>
          {selection.map(item => <li key={item.id}>
            {item.nombre} ({item.codigo}){" "}
            {editable && <button type="button" className="secondary-action" aria-label={`Quitar ${item.nombre}`}
              onClick={() => setSelection(current => current.filter(course => course.id !== item.id))}>Quitar</button>}
          </li>)}
        </ul>}
        {editable && <>
          <Catalog selection={selection} onAdd={item => setSelection(current => [...current, item])} />
          <button className="primary-action" type="submit" disabled={plan.inicializacion && !selection.length}>Guardar plan</button>
        </>}
      </fieldset>
    </form>
  );
}

function Catalog({ selection, onAdd }: { selection: Plan["cursos"]; onAdd: (course: Course) => void }) {
  const [filter, setFilter] = useState("");
  const catalog = usePaged<Course>("/api/cursos", "", filter ? `busqueda=${encodeURIComponent(filter)}` : "");
  const available = catalog.data?.content.filter(item => !selection.some(course => course.id === item.id));
  return (
    <>
      <label className="field">Buscar en el catálogo
        <input value={filter} onChange={event => { catalog.setPage(0); setFilter(event.target.value); }} />
      </label>
      <Feedback error={catalog.error} />
      {catalog.loading ? <Loading skeleton="fields">Buscando cursos…</Loading> : <>
        <label className="field">Agregar curso
          <select value="" onChange={event => {
            const course = available?.find(item => String(item.id) === event.target.value);
            if (course) onAdd(course);
          }}>
            <option value="">{available?.length ? "Selecciona un curso" : "Sin cursos disponibles"}</option>
            {available?.map(item => <option key={item.id} value={item.id}>{item.nombre} ({item.codigo})</option>)}
          </select>
        </label>
        <Pager data={catalog.data} loading={catalog.loading} setPage={catalog.setPage} />
      </>}
    </>
  );
}
