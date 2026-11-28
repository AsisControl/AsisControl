import { FilterSelect } from "@/shared/ui";

export type StudentFilterValues = { conMatricula: string; grado: string; conApoderado: string };

export const NO_STUDENT_FILTERS: StudentFilterValues = { conMatricula: "", grado: "", conApoderado: "" };

/** Parámetros de consulta con los filtros elegidos. */
export const studentFilterQuery = (values: StudentFilterValues) =>
  new URLSearchParams(Object.entries(values).filter(([, value]) => value)).toString();

export function StudentFilters({ values, onChange }: {
  values: StudentFilterValues;
  onChange: (values: StudentFilterValues) => void;
}) {
  return (
    <div className="toolbar" role="group" aria-label="Filtros de estudiantes">
      <FilterSelect
        label="Matrícula"
        value={values.conMatricula}
        // Sin matrícula activa no hay grado por el cual filtrar.
        onChange={(conMatricula) => onChange({
          ...values, conMatricula, grado: conMatricula === "false" ? "" : values.grado,
        })}
        options={[["true", "Con matrícula activa"], ["false", "Sin matrícula activa"]]}
      />
      <FilterSelect
        label="Grado matriculado"
        value={values.grado}
        onChange={(grado) => onChange({
          ...values, grado, conMatricula: grado && values.conMatricula === "false" ? "" : values.conMatricula,
        })}
        options={[1, 2, 3, 4, 5].map((grade) => [String(grade), `${grade}° de secundaria`])}
      />
      <FilterSelect
        label="Apoderado"
        value={values.conApoderado}
        onChange={(conApoderado) => onChange({ ...values, conApoderado })}
        options={[["true", "Con apoderado"], ["false", "Sin apoderado"]]}
      />
      <button type="button" disabled={!studentFilterQuery(values)} onClick={() => onChange(NO_STUDENT_FILTERS)}>
        Limpiar filtros
      </button>
    </div>
  );
}
