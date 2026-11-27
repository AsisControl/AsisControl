import type { Section } from "@/modules/academic-config";

/** Sección con la que se aprueba la renovación: la solicitada u otra del mismo grado con vacantes. */
export function RenewalSectionField({ sections, requestedId, value, onChange, disabled }: {
  sections: Section[];
  requestedId: number;
  value: string;
  onChange: (value: string) => void;
  disabled: boolean;
}) {
  const hasVacancies = sections.some((section) => section.vacantes > 0);
  const requestedAvailable = sections.some((section) => section.id === requestedId && section.vacantes > 0);
  return (
    <>
      {!requestedAvailable && (
        <p className="dialog-note">
          {hasVacancies
            ? "La sección solicitada ya no tiene vacantes. Elige otra del mismo grado."
            : "Ninguna sección del grado tiene vacantes. Al confirmar, la renovación se rechazará."}
        </p>
      )}
      <label className="field">
        Sección asignada{hasVacancies ? " *" : ""}
        <select
          required={hasVacancies}
          value={value}
          onChange={(event) => onChange(event.target.value)}
          disabled={disabled}
        >
          <option value="" disabled>Seleccionar sección</option>
          {sections.map((section) => (
            <option key={section.id} value={section.id} disabled={section.vacantes <= 0}>
              {section.nombre} · {section.vacantes} vacantes{section.id === requestedId ? " · solicitada" : ""}
            </option>
          ))}
        </select>
      </label>
    </>
  );
}
