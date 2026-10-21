"use client";
import { useState, type FormEvent } from "react";
import { api, message } from "@/core/api/client";
import { Field, FormDialog, ReasonField } from "@/shared/ui";
import type { AcademicYear, Grade, YearPanelProps } from "../types";
type GradeAction = { kind: "grade" } | { kind: "grade-edit"; target: Grade };
/** US-06: grados habilitados del año y su capacidad sugerida. */
export function GradesPanel({
  year,
  editable,
  onYearChange,
  onSuccess,
}: YearPanelProps) {
  const [action, setAction] = useState<GradeAction | null>(null);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const open = (value: GradeAction) => {
    setError("");
    onSuccess("");
    setAction(value);
  };
  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!action) return;
    const form = new FormData(event.currentTarget);
    const capacity = Number(form.get("capacidad"));
    setBusy(true);
    setError("");
    try {
      const result =
        action.kind === "grade"
          ? await api<AcademicYear>(
              `/api/configuracion-academica/anios/${year.anio}/grados`,
              {
                method: "POST",
                body: JSON.stringify({
                  numero: Number(form.get("numero")),
                  capacidadDefault: capacity,
                }),
              },
            )
          : await api<AcademicYear>(
              `/api/configuracion-academica/grados/${action.target.id}`,
              {
                method: "PUT",
                body: JSON.stringify({
                  activo: form.get("activo") === "true",
                  capacidadDefault: capacity,
                  version: action.target.version,
                  motivo: String(form.get("motivo") || "").trim(),
                }),
              },
            );
      onYearChange(result);
      setAction(null);
      onSuccess("Configuración guardada.");
    } catch (error) {
      setError(message(error));
    } finally {
      setBusy(false);
    }
  }
  return (
    <>
      <div className="section-heading">
        <h4>Grados de secundaria</h4>
        {editable && (
          <button onClick={() => open({ kind: "grade" })}>
            Habilitar grado
          </button>
        )}
      </div>
      <div className="user-table-wrap">
        <table className="user-table">
          <thead>
            <tr>
              <th>Grado</th>
              <th>Capacidad sugerida por sección</th>
              <th>Capacidad total habilitada</th>
              <th>Estado</th>
              <th>Acción</th>
            </tr>
          </thead>
          <tbody>
            {year.grados.map((grade) => (
              <tr key={grade.id}>
                <td data-label="Grado">{grade.numero}°</td>
                <td data-label="Capacidad sugerida">
                  {grade.capacidadDefault}
                </td>
                <td data-label="Capacidad total">
                  {year.secciones
                    .filter((s) => s.activo && s.grado === grade.numero)
                    .reduce((total, s) => total + s.capacidadMaxima, 0)}
                </td>
                <td data-label="Estado">
                  {grade.activo ? "Habilitado" : "Inhabilitado"}
                </td>
                <td data-label="Acción">
                  {editable && (
                    <button
                      onClick={() => open({ kind: "grade-edit", target: grade })}
                    >
                      Editar
                    </button>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      {action && (
        <FormDialog
          title={action.kind === "grade" ? "Habilitar grado" : "Editar grado"}
          busy={busy}
          error={error}
          onClose={() => setAction(null)}
          onSubmit={save}
        >
          {action.kind === "grade" && (
            <label className="field">
              Grado *
              <select name="numero" required defaultValue="">
                <option value="" disabled>
                  Seleccionar grado
                </option>
                {[1, 2, 3, 4, 5]
                  .filter((n) => !year.grados.some((g) => g.numero === n))
                  .map((n) => (
                    <option key={n} value={n}>
                      {n}° de secundaria
                    </option>
                  ))}
              </select>
            </label>
          )}
          <Field
            label="Capacidad sugerida por sección"
            name="capacidad"
            type="number"
            min={1}
            max={100}
            required
            defaultValue={
              action.kind === "grade-edit"
                ? action.target.capacidadDefault
                : 30
            }
          />
          {action.kind === "grade-edit" && (
            <>
              <label className="field">
                Estado *
                <select
                  name="activo"
                  defaultValue={String(action.target.activo)}
                >
                  <option value="true">Habilitado</option>
                  <option value="false">Inhabilitado</option>
                </select>
              </label>
              <ReasonField />
            </>
          )}
        </FormDialog>
      )}
    </>
  );
}
