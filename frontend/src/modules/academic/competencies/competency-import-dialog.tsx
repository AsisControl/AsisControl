"use client";

import { useState } from "react";
import { api, apiBlob, message } from "@/core/api/client";
import { DownloadIcon, ExcelFilePicker, validateImportFile } from "@/modules/students";
import { countLabel } from "@/shared/lib/formatters";
import { saveBlob } from "@/shared/lib/save-blob";
import { Dialog, Feedback } from "@/shared/ui";
import styles from "../academic.module.css";

type RowIssue = { row: number; message: string };
type Result = { total: number; validas: number; creadas: number; omitidas: RowIssue[]; errores: RowIssue[] };
const path = "/api/competencias/importaciones";

export function CompetencyImportDialog({ onClose, onImported }: { onClose: () => void; onImported: () => void }) {
  const [file, setFile] = useState<File | null>(null);
  const [result, setResult] = useState<Result | null>(null);
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
      const next = await api<Result>(`${path}?confirmar=${confirm}`, { method: "POST", body });
      setResult(next);
      setFinished(confirm);
      if (confirm) onImported();
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
      saveBlob(await apiBlob(path + "/plantilla"), "competencias.xlsx");
    } catch (cause) {
      setError(message(cause));
    } finally {
      setBusy(false);
    }
  }

  return <Dialog title="Importar competencias" description="Descarga la plantilla y completa una fila por competencia." onClose={onClose} busy={busy}>
    <Feedback error={error} />
    <div className={styles.importTemplate}>
      <button className="secondary-action" type="button" onClick={download} disabled={busy}>
        <DownloadIcon /> Descargar plantilla Excel
      </button>
    </div>
    <ExcelFilePicker label="Archivo Excel de competencias" file={file} error={fileError}
      disabled={busy} onChange={selectFile} />
    {result && <section className={styles.importResult} aria-label="Resultado de la importación">
      <p role="status">
        {countLabel(result.total, "fila", "filas")} ·{" "}
        {finished ? countLabel(result.creadas, "creada", "creadas")
          : countLabel(result.validas, "lista para importar", "listas para importar")} ·{" "}
        {countLabel(result.omitidas.length, "omitida", "omitidas")} · {result.errores.length} con errores
      </p>
      {(result.omitidas.length > 0 || result.errores.length > 0) && <ul>
        {[...result.omitidas, ...result.errores].map(issue => <li key={issue.row}>Fila {issue.row} · {issue.message}</li>)}
      </ul>}
    </section>}
    <div className="form-actions">
      <button className="secondary-action" type="button" disabled={busy} onClick={onClose}>Cerrar</button>
      {!finished && <button className="primary-action" type="button" disabled={!file || !!fileError || busy || result?.validas === 0}
        onClick={() => void run(result !== null)}>
        {busy ? "Procesando…" : result ? "Importar filas válidas" : "Revisar archivo"}
      </button>}
    </div>
  </Dialog>;
}
