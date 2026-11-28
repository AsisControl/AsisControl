"use client";
import { useState, type FormEvent } from "react";
import { Feedback, Field } from "@/shared/ui";
import { usePaged } from "@/shared/hooks/use-paged";
import type { AcademicYear } from "@/modules/academic-config";

/** Búsqueda de matrículas por estudiante, año, grado y sección. */
export function EnrollmentFilters({ onApply }: { onApply: (query: string) => void }) {
  const years = usePaged<AcademicYear>("/api/configuracion-academica/anios");
  const [yearId, setYearId] = useState(0);
  const [grade, setGrade] = useState(0);
  const [sectionId, setSectionId] = useState(0);
  const year = years.data?.content.find((value) => value.id === yearId);
  const grades = year?.grados.filter((value) => value.activo) ?? [];
  const sections = year?.secciones.filter((value) => value.grado === grade) ?? [];

  function apply(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const search = String(new FormData(event.currentTarget).get("search") || "").trim();
    const query = new URLSearchParams();
    if (search) query.set("search", search);
    if (year) query.set("anioAcademico", String(year.anio));
    if (grade) query.set("grado", String(grade));
    if (sectionId) query.set("seccionId", String(sectionId));
    onApply(query.toString());
  }

  return (
    <form
      className="toolbar"
      aria-label="Buscar matrículas"
      onSubmit={apply}
      onReset={() => {
        setYearId(0);
        setGrade(0);
        setSectionId(0);
        onApply("");
      }}
    >
      <Field label="Buscar por estudiante, documento o código" name="search" />
      <label className="field">
        Año
        <select value={yearId || ""} onChange={(event) => {
          setYearId(Number(event.target.value));
          setGrade(0);
          setSectionId(0);
        }}>
          <option value="">Todos</option>
          {years.data?.content.map((value) => <option key={value.id} value={value.id}>{value.anio}</option>)}
        </select>
      </label>
      <label className="field">
        Grado
        <select value={grade || ""} disabled={!year} onChange={(event) => {
          setGrade(Number(event.target.value));
          setSectionId(0);
        }}>
          <option value="">Todos</option>
          {grades.map((value) => <option key={value.id} value={value.numero}>{value.numero}°</option>)}
        </select>
      </label>
      <label className="field">
        Sección
        <select value={sectionId || ""} disabled={!grade} onChange={(event) => setSectionId(Number(event.target.value))}>
          <option value="">Todas</option>
          {sections.map((value) => <option key={value.id} value={value.id}>{value.nombre}</option>)}
        </select>
      </label>
      <button>Buscar</button>
      <button type="reset">Limpiar</button>
      <Feedback error={years.error} />
    </form>
  );
}
