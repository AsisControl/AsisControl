/** Filtro de un listado: se aplica al elegir; la opción vacía muestra todos. */
export function FilterSelect({ label, value, onChange, options, allLabel = "Todos" }: {
  label: string;
  value: string;
  onChange: (value: string) => void;
  options: [value: string, text: string][];
  allLabel?: string;
}) {
  return (
    <label className="field">
      {label}
      <select value={value} onChange={(event) => onChange(event.target.value)}>
        <option value="">{allLabel}</option>
        {options.map(([option, text]) => <option key={option} value={option}>{text}</option>)}
      </select>
    </label>
  );
}
