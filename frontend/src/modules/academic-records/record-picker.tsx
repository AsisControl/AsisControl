"use client";
import { useState } from "react";
import { message } from "@/core/api/client";
import type { PersonaOpcion } from "./services/registro-service";
import styles from "./academic-records.module.css";

/** Busca un registro por nombre o documento y deja elegir uno de los resultados. */
export function RecordPicker({
  label,
  search,
  selected,
  onSelect,
  disabled = false,
}: {
  label: string;
  search: (texto: string) => Promise<PersonaOpcion[]>;
  selected: PersonaOpcion | null;
  onSelect: (option: PersonaOpcion | null) => void;
  disabled?: boolean;
}) {
  const [text, setText] = useState("");
  const [results, setResults] = useState<PersonaOpcion[] | null>(null);
  const [searching, setSearching] = useState(false);
  const [error, setError] = useState("");

  async function run() {
    const query = text.trim();
    if (!query || searching) return;
    setSearching(true);
    setError("");
    try {
      setResults(await search(query));
    } catch (problem) {
      setError(message(problem));
    } finally {
      setSearching(false);
    }
  }

  if (selected)
    return (
      <div className="field full-field">
        <span>{label}</span>
        <div className={styles.picked}>
          <strong>{selected.etiqueta}</strong>
          <button
            type="button"
            disabled={disabled}
            aria-label={`Cambiar ${label.toLowerCase()}`}
            onClick={() => {
              onSelect(null);
              setResults(null);
            }}
          >
            Cambiar
          </button>
        </div>
      </div>
    );

  return (
    <div className="field full-field">
      <label htmlFor={`picker-${label}`}>{label} *</label>
      <div className={styles.pickerSearch}>
        <input
          id={`picker-${label}`}
          value={text}
          disabled={disabled}
          placeholder="Nombre o documento"
          onChange={(event) => setText(event.target.value)}
          onKeyDown={(event) => {
            if (event.key !== "Enter") return;
            event.preventDefault();
            void run();
          }}
        />
        <button
          type="button"
          aria-label={`Buscar ${label.toLowerCase()}`}
          disabled={disabled || searching || !text.trim()}
          onClick={() => void run()}
        >
          {searching ? "Buscando…" : "Buscar"}
        </button>
      </div>
      {error && (
        <small className="field-error" role="alert">
          {error}
        </small>
      )}
      {results?.length === 0 && <small className="field-hint">No se encontraron resultados.</small>}
      {results && results.length > 0 && (
        <ul className={styles.pickerResults}>
          {results.map((option) => (
            <li key={option.id}>
              <button type="button" onClick={() => onSelect(option)}>
                {option.etiqueta}
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
