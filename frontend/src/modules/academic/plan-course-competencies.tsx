import { useEffect, useId, useRef, useState } from "react";
import { api, message } from "@/core/api/client";
import { usePaged } from "@/shared/hooks/use-paged";
import { Feedback, Loading, Pager } from "@/shared/ui";
import { ASOCIACIONES_PATH } from "./associations/services/asociacionService";
import { COMPETENCIAS_PATH } from "./competencies/services/competenciaService";
import type { DraftCourse, PlanCompetency } from "./plan-course-list";
import type { CourseCompetency, Competency } from "./types";
import styles from "./study-plans.module.css";

export function PlanCourseCompetencies({ course, onChange, onClose }: {
  course: DraftCourse;
  onChange?: (selection: PlanCompetency[]) => void;
  onClose: () => void;
}) {
  const titleId = useId();
  const title = useRef<HTMLHeadingElement>(null);
  const [defaults, setDefaults] = useState<PlanCompetency[] | null>(null);
  const [error, setError] = useState("");
  const [retry, setRetry] = useState(0);
  const needsDefaults = course.competencias === undefined && !!onChange;
  useEffect(() => { title.current?.focus(); }, []);
  useEffect(() => {
    if (!needsDefaults) return;
    let active = true;
    api<CourseCompetency[]>(`${ASOCIACIONES_PATH}/curso/${course.id}`)
      .then(items => {
        if (active) setDefaults(items.map(item => ({
          id: item.competenciaId, codigo: item.competenciaCodigo, nombre: item.competenciaNombre, activa: true,
        })));
      })
      .catch(cause => { if (active) setError(message(cause)); });
    return () => { active = false; };
  }, [course.id, needsDefaults, retry]);
  const selected = course.competencias ?? (needsDefaults ? defaults : []);

  return (
    <section className={`${styles.catalog} ${styles.competencyEditor}`} aria-labelledby={titleId}
      onKeyDown={event => { if (event.key === "Escape") onClose(); }}>
      <div className={styles.sectionHeader}>
        <h5 id={titleId} ref={title} tabIndex={-1}>Competencias de {course.nombre || course.codigo}</h5>
        <button type="button" className="secondary-action" onClick={onClose}>Cerrar competencias</button>
      </div>
      <Feedback error={error} />
      {error && <button type="button" className="secondary-action" onClick={() => {
        setError("");
        setRetry(value => value + 1);
      }}>Volver a consultar</button>}
      {selected === null && !error && <Loading variant="inline">Cargando competencias…</Loading>}
      {selected !== null && <>
        {selected.length ? <ul className={styles.competencyList} aria-label="Competencias seleccionadas">
          {selected.map(item => <li key={item.id}>
            <span><strong>{item.nombre}</strong><small>{item.codigo}{item.activa ? "" : " · Inactiva"}</small></span>
            {onChange && <button type="button" className="secondary-action" aria-label={`Quitar competencia ${item.nombre}`}
              onClick={() => onChange(selected.filter(value => value.id !== item.id))}>Quitar</button>}
          </li>)}
        </ul> : <p className={styles.note}>Este curso no tiene competencias seleccionadas para el plan.</p>}
        {onChange && <CompetencyCatalog selected={selected} onChange={onChange} />}
      </>}
    </section>
  );
}

function CompetencyCatalog({ selected, onChange }: { selected: PlanCompetency[]; onChange: (items: PlanCompetency[]) => void }) {
  const [search, setSearch] = useState("");
  const [query, setQuery] = useState("");
  const filters = query ? new URLSearchParams({ busqueda: query }).toString() : "";
  const catalog = usePaged<Competency>(COMPETENCIAS_PATH, "", filters, 5);
  const { setPage } = catalog;
  useEffect(() => {
    if (search.trim() === query) return;
    const timer = setTimeout(() => { setPage(0); setQuery(search.trim()); }, 250);
    return () => clearTimeout(timer);
  }, [search, query, setPage]);
  const loading = catalog.loading || search.trim() !== query;
  const selectedIds = new Set(selected.map(item => item.id));

  return <div className={styles.competencyCatalog}>
    <label className="field">Buscar competencias en el catálogo
      <input type="search" value={search} onChange={event => setSearch(event.target.value)}
        onKeyDown={event => { if (event.key === "Enter") event.preventDefault(); }} />
    </label>
    <Feedback error={catalog.error} />
    {catalog.error && <button type="button" className="secondary-action" onClick={catalog.refresh}>Volver a buscar</button>}
    {loading ? <Loading variant="inline">Buscando competencias…</Loading> : !catalog.error && (
      catalog.data?.content.length ? <>
        <ul className={styles.competencyList} aria-label="Resultados de competencias">
          {catalog.data.content.map(item => <li key={item.id}>
            <span><strong>{item.nombre}</strong><small>{item.codigo}</small></span>
            {selectedIds.has(item.id) ? <span className={styles.hint}>En el plan</span>
              : <button type="button" className="secondary-action" aria-label={`Agregar competencia ${item.nombre}`}
                  onClick={() => onChange([...selected, { ...item, activa: true }])}>Agregar</button>}
          </li>)}
        </ul>
        <Pager data={catalog.data} loading={loading} setPage={setPage} />
      </> : <p className={styles.note}>No se encontraron competencias con esa búsqueda.</p>
    )}
  </div>;
}
