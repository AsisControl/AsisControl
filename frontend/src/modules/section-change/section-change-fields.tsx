import type { AcademicYear } from "@/modules/academic-config";
import type { SectionEnrollment } from "./types";

/** Campos del cambio de sección: otra sección activa del mismo grado y año, con su motivo. */
export function SectionChangeFields({ year, enrollment }: {
  year: AcademicYear | null;
  enrollment: SectionEnrollment;
}) {
  const sections = year?.secciones.filter((section) => section.activo
    && section.grado === enrollment.grado && section.id !== enrollment.seccionId) ?? [];
  return (
    <>
      <label className="field">
        Nueva sección del mismo grado y año *
        <select name="seccionId" required defaultValue="">
          <option value="" disabled>Seleccionar sección</option>
          {sections.map((section) => (
            <option key={section.id} value={section.id} disabled={section.vacantes <= 0}>
              {section.nombre} · {section.vacantes} vacantes
            </option>
          ))}
        </select>
      </label>
      {year && !sections.some((section) => section.vacantes > 0) && (
        <p role="alert">No hay otra sección de {enrollment.grado}° con vacantes en {enrollment.anioAcademico}.</p>
      )}
      <label className="field">
        Motivo del cambio *
        <textarea name="motivo" required maxLength={700} rows={3} />
      </label>
    </>
  );
}
