"use client";
import { useState, type FormEvent } from "react";
import { api, message } from "@/core/api/client";
import { Field, FormDialog, ReasonField } from "@/shared/ui";
import type { Section, YearPanelProps } from "../types";
type SectionAction =
  | { kind: "section" }
  | { kind: "section-edit"; target: Section }
  | { kind: "section-disable"; target: Section; next: boolean };
const titles: Record<SectionAction["kind"], string> = {
  section: "Crear sección",
  "section-edit": "Editar sección",
  "section-disable": "Cambiar estado de sección",
};
/** US-06: secciones del año, capacidad máxima y vacantes. */
export function SectionsPanel({
  year,
  editable,
  onReload,
  onSuccess,
}: YearPanelProps) {
  const [action, setAction] = useState<SectionAction | null>(null);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const [proposedGrade, setProposedGrade] = useState(0);
  const open = (value: SectionAction) => {
    setError("");
    onSuccess("");
    setAction(value);
    setProposedGrade(0);
  };
  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!action) return;
    const form = new FormData(event.currentTarget);
    const str = (key: string) => String(form.get(key) || "").trim();
    setBusy(true);
    setError("");
    try {
      if (action.kind === "section-disable") {
        await api(`/api/secciones/${action.target.id}/estado`, {
          method: "PUT",
          body: JSON.stringify({
            activo: action.next,
            version: action.target.version,
            motivo: str("motivo"),
          }),
        });
      } else {
        const original = action.kind === "section-edit" ? action.target : null;
        let path = "/api/secciones";
        let tutor: number | null = null;
        if (original) {
          const detail = await api<{ docenteTutorId: number | null }>(
            `${path}/${original.id}`,
          );
          tutor = detail.docenteTutorId;
          path += `/${original.id}`;
        }
        await api(path, {
          method: original ? "PUT" : "POST",
          body: JSON.stringify({
            nombre: str("nombre"),
            grado: original?.grado || Number(form.get("numero")),
            anioAcademico: year.anio,
            capacidadMaxima: Number(form.get("capacidad")),
            docenteTutorId: tutor,
            motivo: str("motivo") || null,
            ...(original ? { version: original.version } : {}),
          }),
        });
      }
      await onReload();
      setAction(null);
      onSuccess(
        action.kind === "section-disable"
          ? "Estado de sección actualizado. Su información se conserva."
          : "Configuración guardada.",
      );
    } catch (error) {
      setError(message(error));
    } finally {
      setBusy(false);
    }
  }
  const target = action && action.kind !== "section" ? action.target : null;
  return (
    <>
      <div className="section-heading">
        <h4>Secciones y vacantes</h4>
        {editable && (
          <button
            disabled={!year.grados.some((g) => g.activo)}
            onClick={() => open({ kind: "section" })}
          >
            Crear sección
          </button>
        )}
      </div>
      <p className="hint">
        La capacidad de cada sección es independiente. Las solicitudes no
        reservan vacantes; se ocupan al finalizar la matrícula.
      </p>
      <div className="user-table-wrap">
        <table className="user-table">
          <thead>
            <tr>
              <th>Grado / Sección</th>
              <th>Capacidad</th>
              <th>Matriculados</th>
              <th>Vacantes</th>
              <th>Estado</th>
              <th>Acciones</th>
            </tr>
          </thead>
          <tbody>
            {year.secciones.map((section) => (
              <tr key={section.id}>
                <td data-label="Sección">
                  {section.grado}° {section.nombre}
                </td>
                <td data-label="Capacidad">{section.capacidadMaxima}</td>
                <td data-label="Matriculados">{section.ocupacion}</td>
                <td data-label="Vacantes">{section.vacantes}</td>
                <td data-label="Estado">
                  {section.activo ? "Habilitada" : "Inhabilitada"}
                </td>
                <td data-label="Acciones">
                  {editable && (
                    <div className="row-actions">
                      {section.activo && (
                        <button
                          onClick={() =>
                            open({ kind: "section-edit", target: section })
                          }
                        >
                          Editar
                        </button>
                      )}
                      <button
                        onClick={() =>
                          open({
                            kind: "section-disable",
                            target: section,
                            next: !section.activo,
                          })
                        }
                      >
                        {section.activo ? "Inhabilitar" : "Habilitar"}
                      </button>
                    </div>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      {!year.secciones.length && (
        <p className="empty">Sin secciones configuradas.</p>
      )}
      {action && (
        <FormDialog
          title={titles[action.kind]}
          busy={busy}
          error={error}
          onClose={() => setAction(null)}
          onSubmit={save}
          notes={
            action.kind === "section-disable" && (
              <p>
                Solo pueden inhabilitarse secciones sin solicitudes, matrículas
                ni asignaciones. Su registro se conserva.
              </p>
            )
          }
        >
          {action.kind === "section" && (
            <label className="field">
              Grado *
              <select
                name="numero"
                required
                defaultValue=""
                onChange={(event) =>
                  setProposedGrade(Number(event.target.value))
                }
              >
                <option value="" disabled>
                  Seleccionar grado
                </option>
                {year.grados
                  .filter((g) => g.activo)
                  .map((g) => (
                    <option key={g.numero} value={g.numero}>
                      {g.numero}° de secundaria
                    </option>
                  ))}
              </select>
            </label>
          )}
          {action.kind !== "section-disable" && (
            <>
              <Field
                label="Nombre de sección"
                name="nombre"
                required
                maxLength={20}
                defaultValue={target?.nombre || ""}
                placeholder="Ej. A"
              />
              <Field
                label="Capacidad máxima"
                name="capacidad"
                key={action.kind === "section" ? proposedGrade : "capacity"}
                type="number"
                min={1}
                max={100}
                required
                defaultValue={
                  target
                    ? target.capacidadMaxima
                    : year.grados.find((grade) => grade.numero === proposedGrade)
                        ?.capacidadDefault || 30
                }
              />
            </>
          )}
          {(action.kind !== "section" || year.estado === "ACTIVO") && (
            <ReasonField />
          )}
        </FormDialog>
      )}
    </>
  );
}
