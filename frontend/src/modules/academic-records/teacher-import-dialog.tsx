"use client";
import { useState, type FormEvent } from "react";
import { message } from "@/core/api/client";
import {
  DownloadIcon,
  ExcelFilePicker,
  ImportSummary,
  validateImportFile,
  type ImportResult,
} from "@/modules/students";
import { countLabel } from "@/shared/lib/formatters";
import { saveBlob } from "@/shared/lib/save-blob";
import { Dialog, Feedback } from "@/shared/ui";
import {
  descargarPlantillaDocentes,
  importarDocentes,
} from "./services/docenteImportService";
import styles from "./academic-records.module.css";

const TEMPLATE_FILE_NAME = "plantilla_docentes.xlsx";
/** Aviso según el análisis: las filas con error no se guardan, las válidas sí. */
function analysisNotice({ importedRows, rejectedRows }: ImportResult) {
  if (!importedRows)
    return { error: "Ninguna fila es válida: corrige el archivo y vuelve a validar." };
  if (rejectedRows)
    return {
      error: `Hay ${countLabel(rejectedRows, "fila con error", "filas con error")}. Si importas ahora, solo se registrarán las válidas.`,
    };
  return { success: "El archivo no tiene errores. Pulsa «Importar» para guardarlo." };
}
/**
 * Carga masiva de docentes (US-51): se descarga la plantilla, se analiza el archivo sin guardar
 * y, al confirmar, se crean las filas válidas. Las demás quedan listadas con su motivo.
 */
export function TeacherImportDialog({
  onClose,
  onImported,
}: {
  onClose: () => void;
  /** Se llama cuando se registró al menos un docente, para refrescar el listado. */
  onImported: () => void;
}) {
  const [file, setFile] = useState<File | null>(null);
  const [fileError, setFileError] = useState("");
  const [focusRequest, setFocusRequest] = useState(0);
  /** Resultado del último análisis del archivo elegido; null si hay que volver a validar. */
  const [analysis, setAnalysis] = useState<ImportResult | null>(null);
  const [saved, setSaved] = useState<ImportResult | null>(null);
  const [error, setError] = useState("");
  const [pending, setPending] = useState<"validate" | "import" | "template" | null>(null);
  const busy = pending !== null;

  async function downloadTemplate() {
    setError("");
    setPending("template");
    try {
      saveBlob(await descargarPlantillaDocentes(), TEMPLATE_FILE_NAME);
    } catch (cause) {
      setError(message(cause));
    } finally {
      setPending(null);
    }
  }
  function choose(next: File | null) {
    setFile(next);
    setFileError(next ? validateImportFile(next) : "");
    setAnalysis(null);
    setError("");
  }
  async function validate(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const invalid = validateImportFile(file);
    setFileError(invalid);
    if (invalid || !file) {
      setFocusRequest((request) => request + 1);
      return;
    }
    setPending("validate");
    setError("");
    setAnalysis(null);
    try {
      setAnalysis(await importarDocentes(file, false));
    } catch (cause) {
      setError(message(cause));
    } finally {
      setPending(null);
    }
  }
  async function confirmImport() {
    if (!file) return;
    setPending("import");
    setError("");
    try {
      const result = await importarDocentes(file, true);
      setSaved(result);
      if (result.importedRows) onImported();
    } catch (cause) {
      setError(message(cause));
    } finally {
      setPending(null);
    }
  }
  const validRows = analysis?.importedRows ?? 0;
  return (
    <Dialog
      title="Carga masiva de docentes"
      description="Registra varios docentes con una plantilla de Excel, con las mismas validaciones de la ficha."
      size="lg"
      busy={busy}
      onClose={onClose}
    >
      <form onSubmit={validate} noValidate aria-busy={busy}>
        <Feedback error={error} />
        {saved ? (
          <>
            <Feedback
              success={`Se ${saved.importedRows === 1 ? "registró" : "registraron"} ${countLabel(saved.importedRows, "docente", "docentes")}.`}
            />
            <ImportSummary result={saved} saved />
            <div className="form-actions">
              <button type="button" className="primary-action" onClick={onClose}>
                Listo
              </button>
            </div>
          </>
        ) : (
          <>
            {analysis && <Feedback {...analysisNotice(analysis)} />}
            <div className={styles.templateRow}>
              <button
                type="button"
                className={styles.templateDownload}
                disabled={busy}
                aria-label="Descargar plantilla de docentes"
                onClick={downloadTemplate}
              >
                <DownloadIcon />
                {pending === "template" ? "Descargando…" : "Descargar plantilla"}
              </button>
            </div>
            <ExcelFilePicker
              label="Archivo Excel de docentes"
              file={file}
              error={fileError}
              focusRequest={focusRequest}
              disabled={busy}
              onChange={choose}
            />
            {analysis && <ImportSummary result={analysis} saved={false} />}
            <div className="form-actions">
              <button type="button" disabled={busy} onClick={onClose}>
                Cancelar
              </button>
              <button disabled={busy}>
                {pending === "validate" ? "Validando…" : "Validar archivo"}
              </button>
              <button
                type="button"
                className="primary-action"
                disabled={busy || !validRows}
                title={validRows ? undefined : "Valida el archivo para poder importar"}
                onClick={confirmImport}
              >
                {pending === "import" ? "Importando…" : "Importar"}
              </button>
            </div>
          </>
        )}
      </form>
    </Dialog>
  );
}
