"use client";
import { useState, type FormEvent } from "react";
import { api, apiBlob, message } from "@/core/api/client";
import { Dialog, Feedback } from "@/shared/ui";
import { countLabel } from "@/shared/lib/formatters";
import type { ImportResult } from "./types";
/** Mismos límites que ImportacionAlumnoService: solo .xlsx y hasta 5 MB. */
export const IMPORT_MAX_BYTES = 5 * 1024 * 1024;
const IMPORT_TIMEOUT_MS = 120000;
const TEMPLATE_NAME = "plantilla_alumnos.xlsx";
export function validateImportFile(file: File | null): string {
  if (!file) return "Selecciona el archivo Excel con los estudiantes.";
  if (!file.name.toLowerCase().endsWith(".xlsx"))
    return "El archivo debe tener formato .xlsx.";
  if (file.size > IMPORT_MAX_BYTES) return "El archivo no puede superar 5 MB.";
  return "";
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
        <p className="hint">
          1. Descarga la plantilla y completa una fila por estudiante. 2. Sube
          el archivo (.xlsx, máximo 5 MB). Las filas con errores no se
          registran; el resto sí.
        </p>
        <div className="form-actions">
          <button type="button" disabled={busy} onClick={downloadTemplate}>
            Descargar plantilla
          </button>
        </div>
        <div className="form-grid">
          <label className="field full-field">
            Archivo Excel
            <span className="field-required"> *</span>
            <input
              type="file"
              accept=".xlsx,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
              disabled={busy}
              aria-invalid={!!fileError}
              onChange={(e) => {
                setFile(e.target.files?.[0] ?? null);
                setFileError("");
                setResult(null);
              }}
            />
            {fileError && (
              <small className="field-error" role="alert">
                {fileError}
              </small>
            )}
          </label>
        </div>
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
