"use client";
import { useEffect, useId, useRef, useState, type DragEvent } from "react";
import styles from "./import-dialog.module.css";

const XLSX_ACCEPT =
  ".xlsx,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

/** "850 B", "24 KB", "1.3 MB" (formato es-PE). */
export function fileSizeLabel(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${Math.round(bytes / 1024)} KB`;
  return `${(bytes / (1024 * 1024)).toLocaleString("es-PE", { maximumFractionDigits: 1 })} MB`;
}

type ExcelFilePickerProps = {
  /** Nombre accesible del campo: "Archivo Excel de estudiantes". */
  label: string;
  file: File | null;
  error: string;
  /** Cada incremento devuelve el foco al selector si hay error (al enviar sin archivo válido). */
  focusRequest: number;
  disabled: boolean;
  onChange: (file: File | null) => void;
};

/**
 * Zona para soltar o elegir un Excel. Con archivo, muestra una tarjeta con su nombre,
 * tamaño y las opciones de cambiarlo o quitarlo.
 */
export function ExcelFilePicker({
  label,
  file,
  error,
  focusRequest,
  disabled,
  onChange,
}: ExcelFilePickerProps) {
  const fieldId = useId();
  const inputRef = useRef<HTMLInputElement>(null);
  const buttonRef = useRef<HTMLButtonElement>(null);
  const [dragging, setDragging] = useState(false);
  const hintId = `${fieldId}-hint`;
  const errorId = `${fieldId}-error`;
  const describedBy = error ? `${hintId} ${errorId}` : hintId;

  useEffect(() => {
    if (error) buttonRef.current?.focus();
  }, [error, focusRequest]);

  function choose() {
    inputRef.current?.click();
  }
  function clear() {
    // Sin limpiar el valor, elegir de nuevo el mismo archivo no dispararía onChange.
    if (inputRef.current) inputRef.current.value = "";
    onChange(null);
  }
  function drop(event: DragEvent<HTMLElement>) {
    event.preventDefault();
    setDragging(false);
    if (!disabled) onChange(event.dataTransfer.files[0] ?? null);
  }

  return (
    <div className={styles.picker}>
      <input
        ref={inputRef}
        id={fieldId}
        type="file"
        accept={XLSX_ACCEPT}
        className={styles.srOnly}
        disabled={disabled}
        tabIndex={-1}
        aria-label={label}
        aria-invalid={!!error}
        aria-describedby={describedBy}
        onChange={(event) => onChange(event.target.files?.[0] ?? null)}
      />
      {file ? (
        <div
          className={`${styles.fileCard} ${error ? styles.fileCardInvalid : ""}`}
        >
          <span className={styles.fileIcon} aria-hidden="true">
            <SheetIcon />
          </span>
          <span className={styles.fileInfo}>
            <span className={styles.fileName} title={file.name} role="status">
              {file.name}
            </span>
            <span className={styles.fileSize} id={hintId}>
              {fileSizeLabel(file.size)}
            </span>
          </span>
          <span className={styles.fileActions}>
            <button
              ref={buttonRef}
              type="button"
              className={styles.fileAction}
              disabled={disabled}
              aria-describedby={describedBy}
              onClick={choose}
            >
              Cambiar archivo
            </button>
            <button
              type="button"
              className={styles.fileAction}
              disabled={disabled}
              aria-label={`Quitar ${file.name}`}
              onClick={clear}
            >
              Quitar
            </button>
          </span>
        </div>
      ) : (
        <label
          htmlFor={fieldId}
          className={[
            styles.dropzone,
            dragging && styles.dropzoneActive,
            error && styles.dropzoneInvalid,
            disabled && styles.dropzoneDisabled,
          ]
            .filter(Boolean)
            .join(" ")}
          onDragOver={(event) => {
            event.preventDefault();
            if (!disabled) setDragging(true);
          }}
          onDragLeave={() => setDragging(false)}
          onDrop={drop}
        >
          <span className={styles.dropzoneIcon} aria-hidden="true">
            <UploadIcon />
          </span>
          <span className={styles.dropzoneTitle}>Arrastra el archivo aquí o</span>
          <button
            ref={buttonRef}
            type="button"
            className={styles.browse}
            disabled={disabled}
            aria-describedby={describedBy}
            onClick={(event) => {
              // Evita que la etiqueta abra el selector por segunda vez.
              event.preventDefault();
              choose();
            }}
          >
            Seleccionar archivo Excel
          </button>
          <span className={styles.dropzoneHint} id={hintId}>
            Solo Excel (.xlsx), máximo 5 MB
          </span>
          <span className={styles.srOnly} role="status">
            Ningún archivo seleccionado
          </span>
        </label>
      )}
      {error && (
        <p id={errorId} className={styles.fileError} role="alert">
          {error}
        </p>
      )}
    </div>
  );
}

const iconProps = {
  viewBox: "0 0 24 24",
  fill: "none",
  stroke: "currentColor",
  strokeWidth: 2,
  strokeLinecap: "round",
  strokeLinejoin: "round",
} as const;
export const DownloadIcon = () => (
  <svg {...iconProps} width="16" height="16" aria-hidden="true">
    <path d="M12 4v11m0 0-4-4m4 4 4-4M5 19h14" />
  </svg>
);
const UploadIcon = () => (
  <svg {...iconProps} width="28" height="28">
    <path d="M12 16V5m0 0-4 4m4-4 4 4" />
    <path d="M5 15v3a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2v-3" />
  </svg>
);
const SheetIcon = () => (
  <svg {...iconProps} width="22" height="22">
    <path d="M14 3H7a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V8z" />
    <path d="M14 3v5h5M9 13l3 4m0-4-3 4" />
  </svg>
);
