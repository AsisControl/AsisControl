"use client";

import { useState } from "react";
import { api, apiBlob, message } from "@/core/api/client";
import { DownloadIcon, ExcelFilePicker, validateImportFile } from "@/modules/students";
import { countLabel } from "@/shared/lib/formatters";
import { saveBlob } from "@/shared/lib/save-blob";
import { Dialog, Feedback } from "@/shared/ui";
import styles from "./bulk-import.module.css";

/** Problema de una fila: `field` es la columna, o "fila" cuando afecta a toda la fila. */
export type BulkImportIssue = { row: number; field?: string; message: string };

/** Respuesta común de la carga masiva (OBS-05). */
export type BulkImportResult = {
  total: number;
  validas: number;
  creadas: number;
  omitidas: BulkImportIssue[];
  errores: BulkImportIssue[];
};

export type BulkImportDialogProps = {
  title: string;
  description: string;
  /** Ruta base del módulo, por ejemplo "/api/aulas/importaciones". */
  path: string;
  /** Nombre con el que se guarda la plantilla descargada. */
  templateName: string;
  fileLabel: string;
  onClose: () => void;
  onImported: (result: BulkImportResult) => void;
};

function issueText(issue: BulkImportIssue) {
  const column = issue.field && issue.field !== "fila" ? ` · ${issue.field}` : "";
  return `Fila ${issue.row}${column} · ${issue.message}`;
}

/**
 * OBS-05: carga masiva en dos pasos. «Revisar archivo» valida con las reglas reales sin guardar;
 * «Importar filas válidas» registra solo las filas sin errores. Las filas ya registradas o
 * repetidas se omiten, así que volver a cargar el mismo archivo no duplica datos.
 */
export function BulkImportDialog({
  title,
  description,
  path,
  templateName,
  fileLabel,
  onClose,
  onImported,
}: BulkImportDialogProps) {
  const [file, setFile] = useState<File | null>(null);
  const [result, setResult] = useState<BulkImportResult | null>(null);
  const [finished, setFinished] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [fileError, setFileError] = useState("");

  function selectFile(selected: File | null) {
    setFile(selected);
    setFileError(selected ? validateImportFile(selected) : "");
    setResult(null);
    setFinished(false);
    setError("");
  }

  async function run(confirm: boolean) {
    if (!file || fileError) return;
    setBusy(true);
    setError("");
    try {
      const body = new FormData();
      body.append("archivo", file);
      const next = await api<BulkImportResult>(`${path}?confirmar=${confirm}`, { method: "POST", body });
      setResult(next);
      setFinished(confirm);
      if (confirm) onImported(next);
    } catch (cause) {
      setError(message(cause));
    } finally {
      setBusy(false);
    }
  }

  async function download() {
    setBusy(true);
    setError("");
    try {
      saveBlob(await apiBlob(`${path}/plantilla`), templateName);
    } catch (cause) {
      setError(message(cause));
    } finally {
      setBusy(false);
    }
  }

  return <Dialog title={title} description={description} onClose={onClose} busy={busy}>
    <Feedback error={error} />
    <div className={styles.template}>
      <button className="secondary-action" type="button" onClick={download} disabled={busy}>
        <DownloadIcon /> Descargar plantilla Excel
      </button>
    </div>
    <ExcelFilePicker label={fileLabel} file={file} error={fileError} disabled={busy} onChange={selectFile} />
    {result && <section className={styles.result} aria-label="Resultado de la importación">
      <p role="status">
        {countLabel(result.total, "fila", "filas")} ·{" "}
        {finished ? countLabel(result.creadas, "creada", "creadas")
          : countLabel(result.validas, "lista para importar", "listas para importar")} ·{" "}
        {countLabel(result.omitidas.length, "omitida", "omitidas")} · {result.errores.length} con errores
      </p>
      {result.errores.length > 0 && <ul aria-label="Filas con errores">
        {result.errores.map(issue => <li key={`e${issue.row}`}>{issueText(issue)}</li>)}
      </ul>}
      {result.omitidas.length > 0 && <ul aria-label="Filas omitidas" className={styles.skipped}>
        {result.omitidas.map(issue => <li key={`o${issue.row}`}>{issueText(issue)}</li>)}
      </ul>}
    </section>}
    <div className="form-actions">
      <button className="secondary-action" type="button" disabled={busy} onClick={onClose}>Cerrar</button>
      {!finished && <button className="primary-action" type="button"
        disabled={!file || !!fileError || busy || result?.validas === 0}
        onClick={() => void run(result !== null)}>
        {busy ? "Procesando…" : result ? "Importar filas válidas" : "Revisar archivo"}
      </button>}
    </div>
  </Dialog>;
}
