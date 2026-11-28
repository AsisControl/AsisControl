/** Filtro principal de Evaluaciones: año académico, con el vigente señalado. */
export function AcademicYearFilter({
  value,
  years,
  activeYear,
  loading,
  required = false,
  onChange,
}: {
  value: string;
  years: number[];
  activeYear: number | null;
  loading: boolean;
  /** Con un año elegido no se puede volver a la opción vacía. */
  required?: boolean;
  onChange: (year: string) => void;
}) {
  return (
    <label className="field">
      Año académico
      <select value={value} disabled={loading} onChange={(event) => onChange(event.target.value)}>
        <option value="" disabled={required && value !== ""}>
          {loading ? "Cargando años…" : "Selecciona un año"}
        </option>
        {years.map((year) => (
          <option key={year} value={year}>
            {year}
            {year === activeYear ? " · Vigente" : ""}
          </option>
        ))}
      </select>
    </label>
  );
}
