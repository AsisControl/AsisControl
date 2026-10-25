"use client";
import { useId, useRef, useState, type DragEvent, type FormEvent } from "react";
import { api, apiBlob, message } from "@/core/api/client";
import { Dialog, Feedback } from "@/shared/ui";
import { countLabel } from "@/shared/lib/formatters";
import type { ImportResult } from "./types";
import styles from "./import-dialog.module.css";
/** Mismos límites que ImportacionAlumnoService: solo .xlsx y hasta 5 MB. */
export const IMPORT_MAX_BYTES = 5 * 1024 * 1024;
const IMPORT_TIMEOUT_MS = 120000;
const TEMPLATE_NAME = "plantilla_alumnos.xlsx";
const XLSX_ACCEPT =
  ".xlsx,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
export function validateImportFile(file: File | null): string {
  if (!file) return "Selecciona el archivo Excel con los estudiantes.";
  if (!file.name.toLowerCase().endsWith(".xlsx"))
    return "El archivo debe tener formato .xlsx.";
  if (file.size > IMPORT_MAX_BYTES) return "El archivo no puede superar 5 MB.";
  return "";
}
/** "850 B", "24 KB", "1.2 MB" (formato es-PE). */
export function fileSizeLabel(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${Math.round(bytes / 1024)} KB`;
  return `${(bytes / (1024 * 1024)).toLocaleString("es-PE", { maximumFractionDigits: 1 })} MB`;
}
/** Carga masiva de fichas: plantilla, subida del Excel y resultado por fila. */
export function StudentImportDialog({
  onClose,
  onImported,
}: {
  onClose: () => void;
  /** Se llama cuando se importó al menos una fila, para refrescar el listado. */
  onImported: () => void;
}) {
  const [file, setFile] = useState<File | null>(null);
  const [fileError, setFileError] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const [result, setResult] = useState<ImportResult | null>(null);
  /** Archivo elegido con el explorador o soltado en la zona; null al quitarlo. */
  function choose(next: File | null) {
    setFile(next);
    // Se avisa al elegir, no recién al importar.
    setFileError(next ? validateImportFile(next) : "");
    setResult(null);
  }
  async function downloadTemplate() {
    setError("");
    try {
      const blob = await apiBlob("/api/alumnos/importaciones/plantilla");
      const url = URL.createObjectURL(blob);
      const link = document.createElement("a");
      link.href = url;
      link.download = TEMPLATE_NAME;
      link.click();
      // Se libera después: algunos navegadores aún no empezaron la descarga.
      setTimeout(() => URL.revokeObjectURL(url), 1000);
    } catch (e) {
      setError(message(e));
    }
  }
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const invalid = validateImportFile(file);
    setFileError(invalid);
    if (invalid || !file) return;
    const body = new FormData();
    body.append("archivo", file);
    setBusy(true);
    setError("");
    setResult(null);
    try {
      const imported = await api<ImportResult>("/api/alumnos/importaciones", {
        method: "POST",
        body,
        timeoutMs: IMPORT_TIMEOUT_MS,
      });
      setResult(imported);
      if (imported.importedRows > 0) onImported();
    } catch (e) {
      setError(message(e));
    } finally {
      setBusy(false);
    }
  }
  return (
    <Dialog
      title="Carga masiva de estudiantes"
      description="Registra varias fichas a partir de un archivo Excel."
      size="lg"
      busy={busy}
      onClose={onClose}
    >
      <form onSubmit={submit} noValidate>
        <Feedback error={error} />
        <ol className={styles.steps}>
          <li className={styles.step}>
            <p className={styles.stepTitle}>Descarga la plantilla</p>
            <p className={styles.stepText}>
              Completa una fila por estudiante sin cambiar los encabezados.
            </p>
            <div className={styles.stepBody}>
              <button
                type="button"
                className={styles.templateButton}
                disabled={busy}
                onClick={downloadTemplate}
              >
                <DownloadIcon />
                Descargar plantilla
              </button>
            </div>
          </li>
          <li className={styles.step}>
            <p className={styles.stepTitle}>Sube el archivo completado</p>
            <p className={styles.stepText}>
              Las filas con errores no se registran; el resto sí.
            </p>
            <div className={styles.stepBody}>
              <FilePicker
                file={file}
                error={fileError}
                disabled={busy}
                onChoose={choose}
              />
            </div>
          </li>
        </ol>
        {result && <ImportSummary result={result} />}
        <div className="form-actions">
          <button type="button" disabled={busy} onClick={onClose}>
            {result ? "Cerrar" : "Cancelar"}
          </button>
          <button className="primary-action" disabled={busy}>
            {busy ? "Importando…" : "Importar archivo"}
          </button>
        </div>
      </form>
    </Dialog>
  );
}
/** Zona para soltar o elegir el Excel; con archivo, muestra su nombre y tamaño. */
function FilePicker({
  file,
  error,
  disabled,
  onChoose,
}: {
  file: File | null;
  error: string;
  disabled: boolean;
  onChoose: (file: File | null) => void;
}) {
  const input = useRef<HTMLInputElement>(null);
  const hintId = useId();
  const [dragging, setDragging] = useState(false);
  function drop(event: DragEvent<HTMLLabelElement>) {
    event.preventDefault();
    setDragging(false);
    if (!disabled) onChoose(event.dataTransfer.files[0] ?? null);
  }
  function clear() {
    // Sin limpiar el valor, elegir de nuevo el mismo archivo no dispararía onChange.
    if (input.current) input.current.value = "";
    onChoose(null);
  }
  const fileInput = (
    <input
      ref={input}
      type="file"
      className={styles.fileInput}
      accept={XLSX_ACCEPT}
      disabled={disabled}
      aria-invalid={!!error}
      aria-describedby={hintId}
      onChange={(e) => onChoose(e.target.files?.[0] ?? null)}
    />
  );
  return (
    <>
      {file ? (
        <div
          className={`${styles.fileCard} ${error ? styles.fileCardInvalid : ""}`}
        >
          <span className={styles.fileIcon} aria-hidden="true">
            <SheetIcon />
          </span>
          <span className={styles.fileInfo}>
            <span className={styles.fileName} title={file.name}>
              {file.name}
            </span>
            <span className={styles.fileSize} id={hintId}>
              {fileSizeLabel(file.size)}
            </span>
          </span>
          <span className={styles.fileActions}>
            <label className={styles.fileAction}>
              Cambiar
              <span className={styles.srOnly}> archivo {file.name}</span>
              {fileInput}
            </label>
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
          {fileInput}
          <span className={styles.dropzoneIcon} aria-hidden="true">
            <UploadIcon />
          </span>
          <span className={styles.dropzoneTitle}>
            Arrastra el archivo aquí o{" "}
            <span className={styles.browse}>selecciónalo</span>
          </span>
          <span className={styles.dropzoneHint} id={hintId}>
            Solo Excel (.xlsx), máximo 5 MB
          </span>
        </label>
      )}
      {error && (
        <small className={styles.fileError} role="alert">
          {error}
        </small>
      )}
    </>
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
const DownloadIcon = () => (
  <svg {...iconProps} width="16" height="16" aria-hidden="true">
    <path d="M12 4v11m0 0-4-4m4 4 4-4M5 19h14" />
  </svg>
);
const UploadIcon = () => (
  <svg {...iconProps} width="32" height="32">
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
function ImportSummary({ result }: { result: ImportResult }) {
  return (
    <section aria-live="polite">
      <dl className="detail-grid">
        <div>
          <dt>Filas leídas</dt>
          <dd>{result.totalRows}</dd>
        </div>
        <div>
          <dt>Importadas</dt>
          <dd>{result.importedRows}</dd>
        </div>
        <div>
          <dt>Fallidas</dt>
          <dd>{result.rejectedRows}</dd>
        </div>
      </dl>
      {result.issues.length > 0 && (
        <>
          <h4>
            {countLabel(result.issues.length, "fila con error", "filas con error")}
          </h4>
          <div className="user-table-wrap">
            <table className="user-table">
              <thead>
                <tr>
                  <th>Fila</th>
                  <th>Campo</th>
                  <th>Problema</th>
                </tr>
              </thead>
              <tbody>
                {result.issues.map((issue, index) => (
                  <tr key={`${issue.row}-${issue.field}-${index}`}>
                    <td data-label="Fila">{issue.row}</td>
                    <td data-label="Campo">{issue.field || "—"}</td>
                    <td data-label="Problema">{issue.message}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </>
      )}
    </section>
  );
}
