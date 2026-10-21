"use client";
import { Feedback, Pager } from "@/shared/ui";
import { PersonFields } from "@/shared/forms/person-fields";
import { usePaged } from "@/shared/hooks/use-paged";
import { roleLabel } from "@/shared/lib/formatters";
import type { PersonChoice } from "../types";
import { initialsOf, rolePillClass } from "../users/user-format";
import {
  CheckIcon,
  SearchInput,
  StepCard,
  useDebouncedSearch,
} from "./step-parts";
import styles from "./new-user.module.css";
export type PersonMode = "existing" | "new";
/** Paso 1: elegir una persona registrada o registrar sus datos. */
export function PersonStep({
  mode,
  onModeChange,
  person,
  onPersonChange,
}: {
  mode: PersonMode;
  onModeChange: (mode: PersonMode) => void;
  person: PersonChoice | null;
  onPersonChange: (person: PersonChoice) => void;
}) {
  const search = useDebouncedSearch();
  const people = usePaged<PersonChoice>("/api/usuarios/personas", search.query);
  const done = mode === "new" || Boolean(person);
  return (
    <StepCard
      number={1}
      title="Persona"
      description="Cada acceso pertenece a una persona. Una persona puede tener un acceso por rol."
      done={done}
    >
      <div
        className={styles.segmented}
        role="group"
        aria-label="Origen de la persona"
      >
        <button
          type="button"
          aria-pressed={mode === "existing"}
          onClick={() => onModeChange("existing")}
        >
          Persona registrada
        </button>
        <button
          type="button"
          aria-pressed={mode === "new"}
          onClick={() => onModeChange("new")}
        >
          Persona nueva
        </button>
      </div>
      {mode === "new" ? (
        <PersonFields />
      ) : (
        <>
          <SearchInput
            label="Buscar persona"
            placeholder="Buscar por nombre o documento…"
            value={search.text}
            onChange={(value) => {
              search.setText(value);
              people.setPage(0);
            }}
          />
          <Feedback error={people.error} />
          {people.loading ? (
            <p role="status">Cargando personas…</p>
          ) : people.data?.content.length ? (
            <div className={styles.choiceList}>
              {people.data.content.map((choice) => {
                const selected = person?.id === choice.id;
                return (
                  <button
                    key={choice.id}
                    type="button"
                    className={styles.choice}
                    aria-pressed={selected}
                    onClick={() => onPersonChange(choice)}
                  >
                    <span className={styles.initials} aria-hidden="true">
                      {initialsOf(choice.nombreCompleto)}
                    </span>
                    <span className={styles.choiceText}>
                      <strong>{choice.nombreCompleto}</strong>
                      <small>Documento: {choice.numeroDocumento}</small>
                      <span className={styles.pillRow}>
                        {choice.perfiles.length ? (
                          choice.perfiles.map((profile) => (
                            <span
                              key={profile}
                              className={rolePillClass(profile)}
                            >
                              {roleLabel(profile)}
                            </span>
                          ))
                        ) : (
                          <small>Sin perfil institucional</small>
                        )}
                      </span>
                    </span>
                    {selected && (
                      <span className={styles.check}>
                        <CheckIcon size={18} />
                      </span>
                    )}
                  </button>
                );
              })}
            </div>
          ) : (
            !people.error && (
              <p className="empty">No hay personas para esta búsqueda.</p>
            )
          )}
          <Pager
            data={people.data}
            loading={people.loading}
            setPage={people.setPage}
          />
        </>
      )}
    </StepCard>
  );
}
