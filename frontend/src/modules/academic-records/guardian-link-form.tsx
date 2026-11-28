"use client";
import { useState, type FormEvent } from "react";
import { Feedback, Field, Loading, Pager } from "@/shared/ui";
import { usePaged } from "@/shared/hooks/use-paged";
import { readRelationshipFields, RelationshipFields } from "./relationship-fields";
import { GUARDIAN_RECORDS_PATH, type GuardianLinkValues } from "./services/guardian-link-service";
import type { Guardian } from "./types";

/**
 * Búsqueda y selección de apoderados. El estado vive en quien usa el formulario para
 * que se conserve aunque este se oculte.
 */
export function useGuardianPicker() {
  const [search, setSearch] = useState("");
  const guardians = usePaged<Guardian>(GUARDIAN_RECORDS_PATH, search);
  const [choice, setChoice] = useState<Guardian | null>(null);
  return { setSearch, guardians, choice, setChoice };
}

/** Elige un apoderado existente y declara su parentesco con el estudiante. */
export function GuardianLinkForm({
  picker,
  linked,
  busy,
  submitLabel = "Guardar vínculo",
  onSave,
}: {
  picker: ReturnType<typeof useGuardianPicker>;
  /** Apoderados que el estudiante ya tiene: no se pueden volver a elegir. */
  linked?: { apoderadoId: number }[];
  busy: boolean;
  submitLabel?: string;
  onSave: (values: GuardianLinkValues) => void;
}) {
  const { setSearch, guardians, choice, setChoice } = picker;
  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!choice?.activo || choice.persona.activo === false || busy
      || linked?.some(item => item.apoderadoId === choice.id)) return;
    onSave({
      ...readRelationshipFields(new FormData(event.currentTarget)),
      apoderadoId: choice.id,
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
          guardians.refresh();
        }}
      >
        <Field label="Buscar apoderado" name="search" />
        <button>Buscar</button>
      </form>
      <Feedback error={guardians.error} />
      {guardians.error && <button type="button" onClick={guardians.refresh}>Reintentar búsqueda</button>}
      {guardians.loading ? (
        <Loading>Cargando apoderados…</Loading>
      ) : !guardians.error && (guardians.data?.content.length ? (
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
                    !guardian.activo ||
                    guardian.persona.activo === false
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
      ) : <p className="empty">No se encontraron apoderados para esta búsqueda.</p>)}
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
          <RelationshipFields key={choice.id} />
          <div className="form-actions">
            <button className="primary-action" disabled={busy}>
              {busy ? "Asociando…" : submitLabel}
            </button>
          </div>
        </form>
      )}
    </>
  );
}
