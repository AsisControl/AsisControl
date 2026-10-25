"use client";
import { useState, type FormEvent } from "react";
import { message } from "@/core/api/client";
import { Dialog, Feedback } from "@/shared/ui";
import { countLabel } from "@/shared/lib/formatters";
import type { ImportResult } from "./types";
import {
  downloadStudentTemplate,
  importStudents,
} from "./services/student-import-service";
import {
  downloadGuardianTemplate,
  downloadRelationshipTemplate,
  importGuardians,
  importRelationships,
} from "./services/family-import-service";
import { DownloadIcon, ExcelFilePicker } from "./excel-file-picker";
import styles from "./import-dialog.module.css";
/** Mismos límites que ImportacionAlumnoService: solo .xlsx y hasta 5 MB. */
export const IMPORT_MAX_BYTES = 5 * 1024 * 1024;
export function validateImportFile(
  file: File | null,
  missing = "Selecciona al menos un archivo Excel para importar.",
): string {
  if (!file) return missing;
  if (!file.name.toLowerCase().endsWith(".xlsx"))
    return "El archivo debe tener formato .xlsx.";
  if (file.size > IMPORT_MAX_BYTES) return "El archivo no puede superar 5 MB.";
  return "";
}

export type ImportSlotId = "estudiantes" | "apoderados" | "parentescos";
type ImportSlot = {
  id: ImportSlotId;
  title: string;
  description: string;
  template: { fileName: string; download: () => Promise<Blob> };
  importFile: (file: File) => Promise<ImportResult>;
};
/** En este orden: los parentescos unen estudiantes y apoderados ya registrados. */
const IMPORT_SLOTS: ImportSlot[] = [
  {
    id: "estudiantes",
    title: "Estudiantes",
    description: "Una fila por estudiante, con su código.",
    template: {
      fileName: "plantilla_alumnos.xlsx",
      download: downloadStudentTemplate,
    },
    importFile: importStudents,
  },
  {
    id: "apoderados",
    title: "Apoderados",
    description:
      "Una fila por apoderado, aunque tenga varios hijos. Si su documento ya está registrado, por ejemplo como docente, se reutiliza.",
    template: {
      fileName: "plantilla_apoderados.xlsx",
      download: downloadGuardianTemplate,
    },
    importFile: importGuardians,
  },
  {
    id: "parentescos",
    title: "Parentescos",
    description:
      "Une a cada estudiante con su apoderado: código del estudiante, documento del apoderado, parentesco (padre, madre, tutor legal u otro familiar) y si es el principal.",
    template: {
      fileName: "plantilla_parentescos.xlsx",
      download: downloadRelationshipTemplate,
    },
    importFile: importRelationships,
  },
];
type PerSlot<T> = Partial<Record<ImportSlotId, T>>;

/**
 * Carga masiva de estudiantes y familias. Primero se descargan las tres plantillas para
 * completarlas juntas; después se sube cada archivo en orden y se ve su resultado por fila.
 */
export function FamilyImportDialog({
  onClose,
  onImported,
}: {
  onClose: () => void;
  /** Se llama cuando se importó al menos una fila, para refrescar el listado. */
  onImported: () => void;
}) {
  const [files, setFiles] = useState<PerSlot<File | null>>({});
  const [fileErrors, setFileErrors] = useState<PerSlot<string>>({});
  const [results, setResults] = useState<PerSlot<ImportResult>>({});
  const [focusRequest, setFocusRequest] = useState(0);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const [downloading, setDownloading] = useState<ImportSlotId | null>(null);
  const hasResults = Object.values(results).some(Boolean);

  async function downloadTemplate(slot: ImportSlot) {
    setError("");
    setDownloading(slot.id);
    try {
      const blob = await slot.template.download();
      const url = URL.createObjectURL(blob);
      const link = document.createElement("a");
      link.href = url;
      link.download = slot.template.fileName;
      link.click();
      // Se libera después: algunos navegadores aún no empezaron la descarga.
      setTimeout(() => URL.revokeObjectURL(url), 1000);
    } catch (cause) {
      setError(message(cause));
    } finally {
      setDownloading(null);
    }
  }
  function choose(slot: ImportSlotId, file: File | null) {
    setFiles((current) => ({ ...current, [slot]: file }));
    setFileErrors((current) => ({
      ...current,
      [slot]: file ? validateImportFile(file) : "",
    }));
    setResults((current) => ({ ...current, [slot]: undefined }));
    setError("");
  }
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const chosen = IMPORT_SLOTS.filter((slot) => files[slot.id]);
    const errors: PerSlot<string> = chosen.length
      ? Object.fromEntries(
          chosen.map((slot) => [
            slot.id,
            validateImportFile(files[slot.id] ?? null),
          ]),
        )
      : { [IMPORT_SLOTS[0].id]: validateImportFile(null) };
    setFileErrors(errors);
    if (Object.values(errors).some(Boolean)) {
      setFocusRequest((request) => request + 1);
      return;
    }
    setBusy(true);
    setError("");
    setResults({});
    try {
      for (const slot of chosen) {
        const imported = await slot.importFile(files[slot.id]!);
        setResults((current) => ({ ...current, [slot.id]: imported }));
        if (imported.importedRows > 0) onImported();
      }
    } catch (cause) {
      setError(message(cause));
    } finally {
      setBusy(false);
    }
  }
  return (
    <Dialog
      title="Carga masiva de estudiantes y familias"
      description="Registra estudiantes, apoderados y sus parentescos con plantillas de Excel."
      size="lg"
      busy={busy}
      onClose={onClose}
    >
      <form onSubmit={submit} noValidate aria-busy={busy}>
        <Feedback error={error} />
        <section
          className={styles.templates}
          aria-labelledby="import-templates-title"
        >
          <h4 id="import-templates-title" className={styles.sectionTitle}>
            1. Prepara las plantillas
          </h4>
          <p className={styles.slotText}>
            Descarga las tres y complétalas juntas. En parentescos usa el mismo
            código del estudiante y el mismo documento del apoderado.
          </p>
          <div className={styles.templateList}>
            {IMPORT_SLOTS.map((slot) => (
              <button
                key={slot.id}
                type="button"
                className={styles.templateButton}
                disabled={busy || downloading !== null}
                aria-label={`Descargar plantilla de ${slot.title.toLowerCase()}`}
                onClick={() => downloadTemplate(slot)}
              >
                <DownloadIcon />
                <span>
                  {downloading === slot.id ? "Descargando…" : slot.title}
                  <span className={styles.templateFile}>
                    {slot.template.fileName}
                  </span>
                </span>
              </button>
            ))}
          </div>
        </section>
        <h4 className={styles.sectionTitle}>2. Sube los archivos en orden</h4>
        <p className={styles.slotText}>
          Sube solo los que necesites. Los parentescos usan a los estudiantes y
          apoderados ya registrados.
        </p>
        <ol className={styles.slots}>
          {IMPORT_SLOTS.map((slot) => {
            const result = results[slot.id];
            return (
              <li key={slot.id} className={styles.slot}>
                <h5 className={styles.slotTitle}>{slot.title}</h5>
                <p className={styles.slotText}>{slot.description}</p>
                <div className={styles.slotBody}>
                  <ExcelFilePicker
                    label={`Archivo Excel de ${slot.title.toLowerCase()}`}
                    file={files[slot.id] ?? null}
                    error={fileErrors[slot.id] ?? ""}
                    focusRequest={focusRequest}
                    disabled={busy}
                    onChange={(file) => choose(slot.id, file)}
                  />
                  {result && <ImportSummary result={result} />}
                </div>
              </li>
            );
          })}
        </ol>
        <div className="form-actions">
          <button type="button" disabled={busy} onClick={onClose}>
            {hasResults ? "Cerrar" : "Cancelar"}
          </button>
          <button className="primary-action" disabled={busy}>
            {busy ? "Importando…" : "Importar archivos"}
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
          <dt>Filas en el archivo</dt>
          <dd>{result.totalRows}</dd>
        </div>
        <div>
          <dt>Registradas</dt>
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
            {countLabel(
              result.issues.length,
              "fila con error",
              "filas con error",
            )}
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
