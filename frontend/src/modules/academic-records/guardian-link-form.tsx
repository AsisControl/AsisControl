"use client";
import { useState, type FormEvent } from "react";
import { Feedback, Field, Loading, Pager } from "@/shared/ui";
import { usePaged } from "@/shared/hooks/use-paged";
import { stateLabel } from "@/shared/lib/formatters";
import type { GuardianLinkValues } from "./services/guardian-link-service";
import type { Guardian } from "./types";

const RELATIONSHIPS = [
  "PADRE",
  "MADRE",
  "TUTOR_LEGAL",
  "OTRO_FAMILIAR",
  "NO_DECLARADO",
];

/**
 * Búsqueda y selección de apoderados. El estado vive en quien usa el formulario para
 * que se conserve aunque este se oculte.
 */
export function useGuardianPicker() {
  const [search, setSearch] = useState("");
  const guardians = usePaged<Guardian>("/api/apoderados", search);
  const [choice, setChoice] = useState<Guardian | null>(null);
  return { setSearch, guardians, choice, setChoice };
}

/** Elige un apoderado existente y declara su parentesco con el estudiante. */
export function GuardianLinkForm({
  picker,
  linked,
  busy,
  onSave,
}: {
  picker: ReturnType<typeof useGuardianPicker>;
  /** Apoderados que el estudiante ya tiene: no se pueden volver a elegir. */
  linked?: { apoderadoId: number }[];
  busy: boolean;
  onSave: (values: GuardianLinkValues) => void;
}) {
  const { setSearch, guardians, choice, setChoice } = picker;
  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!choice) return;
    const form = new FormData(event.currentTarget);
    onSave({
      apoderadoId: choice.id,
      parentesco: String(form.get("parentesco")),
      principal: form.get("principal") === "on",
    });
  }
  return (
    <>
      <p className="hint">
        Selecciona un apoderado existente. Para registrar uno nuevo, utiliza
        Administración → Usuarios → Crear acceso.
      </p>
      <form
        className="toolbar"
        onSubmit={(event) => {
          event.preventDefault();
          setSearch(
            String(new FormData(event.currentTarget).get("search") || ""),
          );
          guardians.setPage(0);
        }}
      >
        <Field label="Buscar apoderado" name="search" />
        <button>Buscar</button>
      </form>
      <Feedback error={guardians.error} />
      {guardians.loading ? (
        <Loading>Cargando apoderados…</Loading>
      ) : (
        <ul className="choice-list">
          {guardians.data?.content.map((guardian) => {
            const alreadyLinked = linked?.some(
              (value) => value.apoderadoId === guardian.id,
            );
            return (
              <li key={guardian.id}>
                <span>
                  {guardian.persona.nombreCompleto}
                  <small>{guardian.persona.numeroDocumento}</small>
                </span>
                <button
                  disabled={
                    alreadyLinked ||
                    choice?.id === guardian.id ||
                    !guardian.activo
                  }
                  onClick={() => setChoice(guardian)}
                >
                  {alreadyLinked
                    ? "Ya asociado"
                    : choice?.id === guardian.id
                      ? "Seleccionado"
                      : "Elegir"}
                </button>
              </li>
            );
          })}
        </ul>
      )}
      <Pager
        data={guardians.data}
        loading={guardians.loading}
        setPage={guardians.setPage}
      />
      {choice && (
        <form onSubmit={submit}>
          <p>
            Apoderado seleccionado:{" "}
            <strong>{choice.persona.nombreCompleto}</strong>
          </p>
          <label className="field">
            Parentesco *
            <select name="parentesco" defaultValue="NO_DECLARADO">
              {RELATIONSHIPS.map((value) => (
                <option key={value} value={value}>
                  {stateLabel(value)}
                </option>
              ))}
            </select>
          </label>
          <label className="check-field">
            <input type="checkbox" name="principal" /> Apoderado principal
          </label>
          <div className="form-actions">
            <button className="primary-action" disabled={busy}>
              {busy ? "Asociando…" : "Guardar vínculo"}
            </button>
          </div>
        </form>
      )}
    </>
  );
}
