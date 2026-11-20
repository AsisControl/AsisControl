"use client";
import { useState } from "react";
import { usePaged } from "@/shared/hooks/use-paged";
import type { AcademicYear } from "@/modules/academic-config";
import type { Course } from "../types";

import { Loading } from "@/shared/ui";
/** "1° Grado · 2027", como en Secciones; los cursos anteriores al cambio no lo tienen. */
export const courseContextLabel = (course: Course) =>
  course.grado === null || course.anioAcademico === null
    ? "Sin año ni grado"
    : `${course.grado}° Grado · ${course.anioAcademico}`;

/** La referencia a un año cerrado solo se conserva en cursos que ya la tienen. */
const openYears = (years: AcademicYear[], course?: Course) =>
  years
    .filter((year) => year.estado !== "CERRADO" || year.anio === course?.anioAcademico)
    .sort((a, b) => a.anio - b.anio);

/** Año sugerido: el del curso, si no el año activo y si no el primero disponible. */
function initialYear(years: AcademicYear[], course?: Course) {
  return (
    years.find((year) => year.anio === course?.anioAcademico) ??
    years.find((year) => year.estado === "ACTIVO") ??
    years[0]
  );
}

/**
 * Selección del contexto académico del curso: el año y uno de sus grados habilitados.
 * El valor que se envía es el id del grado (name="gradoAcademicoId").
 */
export function CourseContextFields({ course }: { course?: Course }) {
  const years = usePaged<AcademicYear>("/api/configuracion-academica/anios");
  const available = openYears(years.data?.content ?? [], course);
  const [yearId, setYearId] = useState<number | null>(null);
  const year =
    available.find((item) => item.id === yearId) ??
    initialYear(available, course);
  const grades = (year?.grados ?? [])
    .filter((grade) => grade.activo || grade.id === course?.gradoAcademicoId)
    .filter((grade) => year?.estado !== "CERRADO" || grade.id === course?.gradoAcademicoId)
    .sort((a, b) => a.numero - b.numero);
  if (years.loading)
    return (
      <>
        <input type="hidden" name="gradoAcademicoId" value={course?.gradoAcademicoId ?? ""} />
        <Loading variant="inline" className="full-field">Cargando años académicos…</Loading>
      </>
    );
  if (years.error)
    return (
      <>
        <input type="hidden" name="gradoAcademicoId" value={course?.gradoAcademicoId ?? ""} />
        <p className="full-field field-error" role="alert">No se pudo cargar la referencia académica.</p>
      </>
    );
  if (!available.length) return <>
    <input type="hidden" name="gradoAcademicoId" value={course?.gradoAcademicoId ?? ""} />
    <p className="full-field field-hint">Puedes registrar el curso sin un año ni grado de referencia.</p>
  </>;
  return (
    <>
      <label className="field">
        Año académico
        <select
          name="anio"
          value={year?.id ?? ""}
          onChange={(event) => setYearId(Number(event.target.value))}
        >
          {available.map((item) => (
            <option key={item.id} value={item.id}>
              {item.anio} · {item.estado === "ACTIVO" ? "Activo" : item.estado === "CERRADO" ? "Cerrado" : "Borrador"}
            </option>
          ))}
        </select>
      </label>
      <label className="field">
        Grado de referencia
        <select
          key={year?.id}
          name="gradoAcademicoId"
          defaultValue={
            grades.find((grade) => grade.id === course?.gradoAcademicoId)?.id ??
            ""
          }
        >
          <option value="">
            Sin referencia académica
          </option>
          {grades.map((grade) => (
            <option key={grade.id} value={grade.id}>
              {grade.numero}° Grado
            </option>
          ))}
        </select>
        {!grades.length && (
          <small className="field-hint">
            Habilita grados para {year?.anio} en Configuración académica.
          </small>
        )}
      </label>
    </>
  );
}
