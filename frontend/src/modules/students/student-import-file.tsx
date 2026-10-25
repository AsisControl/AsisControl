"use client";
import { useEffect, useId, useRef } from "react";
import styles from "./students.module.css";

type StudentImportFileProps = {
  file: File | null;
  error: string;
  focusRequest: number;
  disabled: boolean;
  onChange: (file: File | null) => void;
};

export function StudentImportFile({
  file,
  error,
  focusRequest,
  disabled,
  onChange,
}: StudentImportFileProps) {
  const fieldId = useId();
  const inputRef = useRef<HTMLInputElement>(null);
  const buttonRef = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    if (error) buttonRef.current?.focus();
  }, [error, focusRequest]);

  return (
    <div className={styles.importFile}>
      <label htmlFor={fieldId} className={styles.importFileLabel}>
        Archivo Excel <span className="field-required">*</span>
      </label>
      <p id={`${fieldId}-hint`} className={styles.helper}>
        Formato .xlsx · Hasta 5 MB.
      </p>
      <div className={styles.importFileControl}>
        <input
          ref={inputRef}
          id={fieldId}
          type="file"
          accept=".xlsx,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
          className={styles.importFileInput}
          disabled={disabled}
          tabIndex={-1}
          aria-invalid={!!error}
          aria-describedby={`${fieldId}-hint${error ? ` ${fieldId}-error` : ""}`}
          onChange={(event) => onChange(event.target.files?.[0] ?? null)}
        />
        <button
          ref={buttonRef}
          type="button"
          className={styles.importFileButton}
          disabled={disabled}
          aria-describedby={`${fieldId}-hint${error ? ` ${fieldId}-error` : ""}`}
          onClick={() => inputRef.current?.click()}
        >
          {file ? "Cambiar archivo" : "Seleccionar archivo Excel"}
        </button>
        <p className={styles.importFileName} role="status">
          {file?.name ?? "Ningún archivo seleccionado"}
        </p>
      </div>
      {error && (
        <p id={`${fieldId}-error`} className={styles.fieldError} role="alert">
          {error}
        </p>
      )}
    </div>
  );
}
