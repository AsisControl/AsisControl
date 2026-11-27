import { FilterSelect } from "./filter-select";

/** Orden de un listado de fichas por su fecha de registro; el valor es la consulta al backend. */
export function RecordOrderSelect({ value, onChange }: {
  value: string;
  onChange: (value: string) => void;
}) {
  return (
    <FilterSelect
      label="Ordenar por"
      allLabel="Apellido (A-Z)"
      value={value}
      onChange={onChange}
      options={[
        ["sortBy=fechaCreacion&direction=DESC", "Registro más reciente"],
        ["sortBy=fechaCreacion&direction=ASC", "Registro más antiguo"],
      ]}
    />
  );
}
