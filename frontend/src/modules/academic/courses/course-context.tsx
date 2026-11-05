"use client";
import { useState } from "react";
import { usePaged } from "@/shared/hooks/use-paged";
import type { AcademicYear } from "@/modules/academic-config";
import type { Course } from "../types";

/** "1° Grado · 2027", como en Secciones; los cursos anteriores al cambio no lo tienen. */
export const courseContextLabel = (course: Course) =>
  course.grado === null || course.anioAcademico === null
    ? "Sin año ni grado"
    : `${course.grado}° Grado · ${course.anioAcademico}`;

/** Los cursos solo se registran en años que aún no se cerraron. */
const openYears = (years: AcademicYear[]) =>
  years
    .filter((year) => year.estado !== "CERRADO")
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
  const available = openYears(years.data?.content ?? []);
  const [yearId, setYearId] = useState<number | null>(null);
  const year =
    available.find((item) => item.id === yearId) ??
    initialYear(available, course);
  const grades = (year?.grados ?? [])
    .filter((grade) => grade.activo)
    .sort((a, b) => a.numero - b.numero);
  if (years.loading)
    return (
      <p className="full-field hint" role="status">
        Cargando años académicos…
      </p>
    );
  if (years.error)
    return (
      <p className="full-field field-error" role="alert">
        No se pudieron consultar los años académicos. Cierra la ventana e
        inténtalo de nuevo.
      </p>
    );
  if (!available.length)
    return (
      <p className="full-field field-error" role="alert">
        No hay años académicos abiertos. Crea uno en Configuración académica
        antes de registrar cursos.
      </p>
    );
  return (
    <>
      <label className="field">
        Año académico
        <span className="field-required"> *</span>
        <select
          name="anio"
          required
          value={year?.id ?? ""}
          onChange={(event) => setYearId(Number(event.target.value))}
        >
          {available.map((item) => (
            <option key={item.id} value={item.id}>
              {item.anio} · {item.estado === "ACTIVO" ? "Activo" : "Borrador"}
            </option>
          ))}
        </select>
      </label>
      <label className="field">
        Grado
        <span className="field-required"> *</span>
        <select
          key={year?.id}
          name="gradoAcademicoId"
          required
          defaultValue={
            grades.find((grade) => grade.id === course?.gradoAcademicoId)?.id ??
            ""
          }
        >
          <option value="" disabled>
            {grades.length ? "Selecciona el grado" : "Sin grados habilitados"}
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
