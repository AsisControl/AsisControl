"use client";
import { useEffect, useState, type FormEvent } from "react";
import { Field } from "@/shared/ui";
import { financialLabels } from "./commitment-format";
import { getSectionsOfYear } from "./services/commitment-service";
import type { SectionChoice } from "./types";
import styles from "./finance.module.css";

const FILTER_NAMES = [
  "busqueda",
  "anioAcademico",
  "grado",
  "idSeccion",
  "estado",
  "vencimientoDesde",
  "vencimientoHasta",
] as const;

/**
 * Filtros de compromisos por año, grado, sección, estudiante, estado y vencimiento.
 * Las secciones se cargan del año indicado; si el rol no puede consultarlas, el grado se escribe a mano.
 */
export function CommitmentFilters({ applied, onApply }: {
  /** Filtros vigentes del listado, para mostrarlos al volver a la pestaña. */
  applied: string;
  onApply: (query: string) => void;
}) {
  const initial = new URLSearchParams(applied);
  const [year, setYear] = useState(initial.get("anioAcademico") ?? "");
  const [grade, setGrade] = useState(initial.get("grado") ?? "");
  const [loaded, setLoaded] = useState<{ year: number; sections: SectionChoice[] } | null>(null);
  const [error, setError] = useState("");

  const validYear = /^\d{4}$/.test(year) ? Number(year) : null;
  const sections = loaded && loaded.year === validYear ? loaded.sections : [];

  useEffect(() => {
    if (validYear === null) return;
    let active = true;
    getSectionsOfYear(validYear)
      .then((found) => {
        if (active) setLoaded({ year: validYear, sections: found });
      })
      .catch(() => {
        if (active) setLoaded({ year: validYear, sections: [] });
      });
    return () => { active = false; };
  }, [validYear]);

  const grades = [...new Set(sections.map((section) => section.grado))].sort((a, b) => a - b);
  const sectionsOfGrade = sections
    .filter((section) => grade && section.grado === Number(grade))
    .sort((a, b) => a.nombre.localeCompare(b.nombre));

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const data = new FormData(event.currentTarget);
    const desde = String(data.get("vencimientoDesde") || "");
    const hasta = String(data.get("vencimientoHasta") || "");
    if (desde && hasta && desde > hasta) {
      setError("La fecha inicial del vencimiento no puede ser posterior a la final.");
      return;
    }
    setError("");
    const query = new URLSearchParams();
    for (const name of FILTER_NAMES) {
      const value = String(data.get(name) || "").trim();
      if (value) query.set(name, value);
    }
    onApply(query.toString());
  }

  return (
    <form className={styles.filters} onSubmit={submit} aria-label="Filtros de compromisos">
      <Field
        label="Buscar estudiante o compromiso"
        name="busqueda"
        placeholder="Nombre, código o concepto"
        defaultValue={initial.get("busqueda") ?? ""}
      />
      <Field
        label="Año académico"
        name="anioAcademico"
        type="number"
        min={1900}
        max={9999}
        placeholder="Ej. 2026"
        value={year}
        onChange={(event) => {
          setYear(event.target.value);
          setGrade("");
        }}
      />
      {grades.length ? (
        <label className="field">
          Grado
          <select name="grado" value={grade} onChange={(event) => setGrade(event.target.value)}>
            <option value="">Todos</option>
            {grades.map((value) => <option key={value} value={value}>{value}.° grado</option>)}
          </select>
        </label>
      ) : (
        <Field
          label="Grado"
          name="grado"
          type="number"
          min={1}
          max={20}
          placeholder="Ej. 3"
          value={grade}
          onChange={(event) => setGrade(event.target.value)}
        />
      )}
      {sectionsOfGrade.length > 0 && (
        <label className="field">
          Sección
          <select name="idSeccion" defaultValue={initial.get("grado") === grade ? initial.get("idSeccion") ?? "" : ""} key={grade}>
            <option value="">Todas</option>
            {sectionsOfGrade.map((section) => (
              <option key={section.id} value={section.id}>Sección {section.nombre}</option>
            ))}
          </select>
        </label>
      )}
      <label className="field">
        Estado del compromiso
        <select name="estado" defaultValue={initial.get("estado") ?? ""}>
          <option value="">Todos</option>
          {Object.entries(financialLabels).map(([key, label]) => (
            <option key={key} value={key}>{label}</option>
          ))}
        </select>
      </label>
      <Field label="Vence desde" name="vencimientoDesde" type="date" defaultValue={initial.get("vencimientoDesde") ?? ""} />
      <Field label="Vence hasta" name="vencimientoHasta" type="date" defaultValue={initial.get("vencimientoHasta") ?? ""} error={error || undefined} />
      <button type="submit" className="primary-action">Buscar</button>
    </form>
  );
}
