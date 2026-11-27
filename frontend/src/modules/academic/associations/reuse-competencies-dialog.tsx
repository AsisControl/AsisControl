"use client";

import { useEffect, useState } from "react";
import { message } from "@/core/api/client";
import { Dialog, Feedback, Loading } from "@/shared/ui";
import type { CourseCompetency } from "../types";
import type { Option } from "./association-form-dialog";
import { listarCompetenciasDeCurso, reutilizarCompetencias } from "./services/asociacionService";

function courseLabel(course: Option) {
  return [course.nombre, course.codigo, course.anioAcademico, course.grado ? `${course.grado}.° grado` : null]
    .filter(Boolean).join(" · ");
}

export function ReuseCompetenciesDialog({ courses, initialDestination, onClose, onSaved }: {
  courses: Option[];
  initialDestination: number;
  onClose: () => void;
  onSaved: (added: number) => void;
}) {
  const [source, setSource] = useState(0);
  const [destination, setDestination] = useState(initialDestination);
  const [preview, setPreview] = useState<{ origin: CourseCompetency[]; existing: Set<number> } | null>(null);
  const [excluded, setExcluded] = useState<number[]>([]);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  useEffect(() => {
    if (!source || !destination || source === destination) return;
    let active = true;
    Promise.all([listarCompetenciasDeCurso(source), listarCompetenciasDeCurso(destination)]).then(([origin, current]) => {
      if (active) setPreview({ origin, existing: new Set(current.map(item => item.competenciaId)) });
    }).catch(cause => { if (active) setError(message(cause)); });
    return () => { active = false; };
  }, [source, destination]);

  const available = preview?.origin.filter(item => !preview.existing.has(item.competenciaId)) ?? [];
  const selected = available.filter(item => !excluded.includes(item.competenciaId));
  const ready = source > 0 && destination > 0 && source !== destination;

  function changeCourse(next: number, from: boolean) {
    setPreview(null);
    setExcluded([]);
    setError("");
    if (from) setSource(next);
    else setDestination(next);
  }

  async function copy() {
    if (!ready || busy || selected.length === 0) return;
    setBusy(true);
    setError("");
    try {
      const result = await reutilizarCompetencias(source, destination, selected.map(item => item.competenciaId));
      onSaved(result.agregadas);
    } catch (cause) {
      setError(message(cause));
    } finally {
      setBusy(false);
    }
  }

  return <Dialog title="Reutilizar competencias" description="Copia las competencias base que elijas de un curso a otro del catálogo." onClose={onClose} busy={busy}>
    <Feedback error={error} />
    <label className="field">Curso de origen
      <select value={source || ""} disabled={busy} onChange={event => changeCourse(Number(event.target.value), true)}>
        <option value="">Selecciona el curso de origen</option>
        {courses.filter(item => item.id !== destination).map(item => <option key={item.id} value={item.id}>{courseLabel(item)}</option>)}
      </select>
    </label>
    <label className="field">Curso de destino
      <select value={destination || ""} disabled={busy} onChange={event => changeCourse(Number(event.target.value), false)}>
        <option value="">Selecciona el curso de destino</option>
        {courses.filter(item => item.id !== source).map(item => <option key={item.id} value={item.id}>{courseLabel(item)}</option>)}
      </select>
    </label>
    {ready && !preview && !error && <Loading>Consultando competencias…</Loading>}
    {preview && <fieldset disabled={busy}>
      <legend>Competencias del curso de origen</legend>
      {preview.origin.length === 0 && <p>El curso de origen todavía no tiene competencias.</p>}
      {preview.origin.map(item => {
        const id = item.competenciaId;
        const existing = preview.existing.has(id);
        return <div key={id}><label>
          <input type="checkbox" checked={existing || !excluded.includes(id)} disabled={existing}
            onChange={event => {
              const checked = event.target.checked;
              setExcluded(current => checked ? current.filter(value => value !== id) : [...current, id]);
            }} /> {item.competenciaNombre} · {item.competenciaCodigo}{existing ? " (ya asociada)" : ""}
        </label></div>;
      })}
    </fieldset>}
    <div className="form-actions">
      <button className="secondary-action" type="button" onClick={onClose} disabled={busy}>Cancelar</button>
      <button className="primary-action" type="button" onClick={() => void copy()} disabled={busy || !selected.length}>Agregar seleccionadas ({selected.length})</button>
    </div>
  </Dialog>;
}
