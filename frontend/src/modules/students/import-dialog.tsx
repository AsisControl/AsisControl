"use client";
import { useState, type FormEvent } from "react";
import { message } from "@/core/api/client";
import { Dialog, Feedback } from "@/shared/ui";
import { countLabel } from "@/shared/lib/formatters";
import type { ImportResult } from "./types";
import { downloadStudentTemplate, importStudents } from "./services/student-import-service";
import { StudentImportFile } from "./student-import-file";
import styles from "./students.module.css";
/** Mismos límites que ImportacionAlumnoService: solo .xlsx y hasta 5 MB. */
export const IMPORT_MAX_BYTES = 5 * 1024 * 1024;
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
  const [fileFocusRequest, setFileFocusRequest] = useState(0);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const [downloading, setDownloading] = useState(false);
  const [result, setResult] = useState<ImportResult | null>(null);
  async function downloadTemplate() {
    setError("");
    setDownloading(true);
    try {
      const blob = await downloadStudentTemplate();
      const url = URL.createObjectURL(blob);
      const link = document.createElement("a");
      link.href = url;
      link.download = TEMPLATE_NAME;
      link.click();
      // Se libera después: algunos navegadores aún no empezaron la descarga.
      setTimeout(() => URL.revokeObjectURL(url), 1000);
    } catch (cause) {
      setError(message(cause));
    } finally {
      setDownloading(false);
    }
  }
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const invalid = validateImportFile(file);
    setFileError(invalid);
    if (invalid || !file) {
      setFileFocusRequest((request) => request + 1);
      return;
    }
    setBusy(true);
    setError("");
    setResult(null);
    try {
      const imported = await importStudents(file);
      setResult(imported);
      if (imported.importedRows > 0) onImported();
    } catch (cause) {
      setError(message(cause));
    } finally {
      setBusy(false);
    }
  }
  return (
    <Dialog
      title="Carga masiva de estudiantes"
      description="Registra varios estudiantes con una plantilla de Excel."
      size="lg"
      busy={busy}
      onClose={onClose}
    >
      <form onSubmit={submit} noValidate aria-busy={busy}>
        <Feedback error={error} />
        <ol className={styles.importSteps}>
          <li>
            <h4>Completa la plantilla</h4>
            <p className={styles.helper}>
              Reemplaza la fila de ejemplo con tus estudiantes. Usa una fila por estudiante.
            </p>
            <button
              type="button"
              className={styles.importTemplateButton}
              disabled={busy || downloading}
              onClick={downloadTemplate}
            >
              {downloading ? "Descargando plantilla…" : "Descargar plantilla"}
            </button>
          </li>
          <li>
            <h4>Selecciona el archivo completado</h4>
            <StudentImportFile
              file={file}
              error={fileError}
              focusRequest={fileFocusRequest}
              disabled={busy}
              onChange={(selectedFile) => {
                setFile(selectedFile);
                setFileError(selectedFile ? validateImportFile(selectedFile) : "");
                setError("");
                setResult(null);
              }}
            />
          </li>
        </ol>
        <p className={styles.helper}>
          Se registrarán las filas correctas. Al terminar podrás revisar las que necesiten cambios.
        </p>
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
          <dt>Estudiantes en el archivo</dt>
          <dd>{result.totalRows}</dd>
        </div>
        <div>
          <dt>Registrados</dt>
          <dd>{result.importedRows}</dd>
        </div>
        <div>
          <dt>Por revisar</dt>
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
