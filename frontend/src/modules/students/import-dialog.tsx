"use client";
import { useEffect, useRef, useState, type FormEvent } from "react";
import { message } from "@/core/api/client";
import { Dialog, Feedback } from "@/shared/ui";
import { countLabel } from "@/shared/lib/formatters";
import type {
  FamilyImportResult,
  FamilyImportSlot,
  ImportResult,
} from "./types";
import { downloadStudentTemplate } from "./services/student-import-service";
import {
  downloadGuardianTemplate,
  downloadRelationshipTemplate,
  importFamilies,
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

export type ImportSlotId = FamilyImportSlot;
type ImportSlot = {
  id: ImportSlotId;
  title: string;
  description: string;
  template: { fileName: string; download: () => Promise<Blob> };
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
  },
];
type PerSlot<T> = Partial<Record<ImportSlotId, T>>;

/**
 * Carga masiva de estudiantes y familias, todo o nada. Primero se descargan las tres
 * plantillas; luego se eligen los archivos en orden, se validan juntos sin guardar nada y,
 * solo si ninguno tiene errores, se importan en una sola operación.
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
  /** Resultado de la última validación o importación de los archivos elegidos. */
  const [report, setReport] = useState<FamilyImportResult | null>(null);
  const [focusRequest, setFocusRequest] = useState(0);
  const [error, setError] = useState("");
  const [pending, setPending] = useState<"validate" | "import" | null>(null);
  const [downloading, setDownloading] = useState<ImportSlotId | null>(null);
  const busy = pending !== null;
  const saved = report?.saved ?? false;

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
  /** Un paso se habilita cuando el anterior tiene un archivo válido. */
  const isReady = (slot: ImportSlotId) => {
    const file = files[slot];
    return Boolean(file) && !validateImportFile(file ?? null);
  };
  const isUnlocked = (index: number) =>
    index === 0 || isReady(IMPORT_SLOTS[index - 1].id);
  function choose(slot: ImportSlotId, file: File | null) {
    const index = IMPORT_SLOTS.findIndex(({ id }) => id === slot);
    const invalid = file ? validateImportFile(file) : "";
    // Sin un archivo válido, los pasos siguientes vuelven a bloquearse y se vacían.
    const later =
      !file || invalid ? IMPORT_SLOTS.slice(index + 1).map(({ id }) => id) : [];
    const cleared = Object.fromEntries(later.map((id) => [id, undefined]));
    setFiles((current) => ({ ...current, ...cleared, [slot]: file }));
    setFileErrors((current) => ({ ...current, ...cleared, [slot]: invalid }));
    // Otro archivo invalida la validación anterior: hay que volver a validar.
    setReport(null);
    setError("");
  }
  function chosenFiles() {
    return Object.fromEntries(
      IMPORT_SLOTS.filter((slot) => files[slot.id]).map((slot) => [
        slot.id,
        files[slot.id]!,
      ]),
    ) as PerSlot<File>;
  }
  /** Valida los tres archivos juntos en el servidor sin guardar ninguna fila. */
  async function validate(event: FormEvent<HTMLFormElement>) {
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
    await send("validate");
  }
  async function send(kind: "validate" | "import") {
    setPending(kind);
    setError("");
    try {
      const response = await importFamilies(chosenFiles(), kind === "import");
      setReport(response);
      if (response.saved) onImported();
    } catch (cause) {
      setError(message(cause));
    } finally {
      setPending(null);
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
      <form onSubmit={validate} noValidate aria-busy={busy}>
        <Feedback error={error} />
        {report && <ReportNotice report={report} />}
        {saved ? (
          <h4 className={styles.sectionTitle}>Detalle por archivo</h4>
        ) : (
          <>
            <section
              className={styles.templates}
              aria-labelledby="import-templates-title"
            >
              <h4 id="import-templates-title" className={styles.sectionTitle}>
                1. Prepara las plantillas
              </h4>
              <p className={styles.slotText}>
                Descarga las tres y complétalas juntas. En parentescos usa el
                mismo código del estudiante y el mismo documento del apoderado.
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
            <h4 className={styles.sectionTitle}>
              2. Sube los archivos en orden
            </h4>
            <p className={styles.slotText}>
              Cada paso se habilita al elegir el archivo del anterior: primero
              estudiantes, luego apoderados y al final parentescos. Antes de
              importar se validan los tres juntos; si alguno tiene errores no se
              guarda ninguna fila.
            </p>
          </>
        )}
        <ol className={styles.slots}>
          {IMPORT_SLOTS.map((slot, index) => {
            const fileReport = report?.files[slot.id];
            const unlocked = isUnlocked(index);
            return (
              <li
                key={slot.id}
                className={
                  unlocked ? styles.slot : `${styles.slot} ${styles.slotLocked}`
                }
                aria-disabled={!unlocked || undefined}
              >
                <h5 className={styles.slotTitle}>{slot.title}</h5>
                <p className={styles.slotText}>{slot.description}</p>
                <div className={styles.slotBody}>
                  {!unlocked && (
                    <p className={styles.lockedHint}>
                      Primero elige el archivo de{" "}
                      {IMPORT_SLOTS[index - 1].title.toLowerCase()}.
                    </p>
                  )}
                  <ExcelFilePicker
                    label={`Archivo Excel de ${slot.title.toLowerCase()}`}
                    file={files[slot.id] ?? null}
                    error={fileErrors[slot.id] ?? ""}
                    focusRequest={focusRequest}
                    disabled={busy || saved || !unlocked}
                    onChange={(file) => choose(slot.id, file)}
                  />
                  {fileReport?.error && (
                    <p className={styles.fileError} role="alert">
                      {fileReport.error}
                    </p>
                  )}
                  {fileReport?.result && (
                    <ImportSummary result={fileReport.result} saved={saved} />
                  )}
                </div>
              </li>
            );
          })}
        </ol>
        {saved ? (
          <div className="form-actions">
            <button type="button" className="primary-action" onClick={onClose}>
              Listo
            </button>
          </div>
        ) : (
          <div className="form-actions">
            <button type="button" disabled={busy} onClick={onClose}>
              Cancelar
            </button>
            <button disabled={busy}>
              {pending === "validate" ? "Validando…" : "Validar archivos"}
            </button>
            <button
              type="button"
              className="primary-action"
              disabled={busy || !report?.valid}
              title={
                report?.valid
                  ? undefined
                  : "Valida los archivos sin errores para poder importar"
              }
              onClick={() => send("import")}
            >
              {pending === "import" ? "Importando…" : "Importar"}
            </button>
          </div>
        )}
      </form>
    </Dialog>
  );
}
const SAVED_NOUNS: Record<ImportSlotId, [string, string]> = {
  estudiantes: ["estudiante", "estudiantes"],
  apoderados: ["apoderado", "apoderados"],
  parentescos: ["parentesco", "parentescos"],
};
/** "150 estudiantes, 120 apoderados y 230 parentescos". */
function savedSummary(report: FamilyImportResult) {
  const parts = IMPORT_SLOTS.flatMap((slot) => {
    const rows = report.files[slot.id]?.result?.importedRows;
    return rows == null ? [] : [countLabel(rows, ...SAVED_NOUNS[slot.id])];
  });
  return parts.length > 1
    ? `${parts.slice(0, -1).join(", ")} y ${parts.at(-1)}`
    : parts[0];
}
/** Cierre de la carga: se muestra arriba y recibe el foco para que no pase desapercibido. */
function ImportCompleted({ report }: { report: FamilyImportResult }) {
  const ref = useRef<HTMLDivElement>(null);
  useEffect(() => {
    ref.current?.focus();
    ref.current?.scrollIntoView?.({ block: "start" });
  }, []);
  return (
    <div ref={ref} className={styles.completed} role="status" tabIndex={-1}>
      <svg
        className={styles.completedIcon}
        viewBox="0 0 24 24"
        width="40"
        height="40"
        fill="none"
        stroke="currentColor"
        strokeWidth="2.2"
        strokeLinecap="round"
        strokeLinejoin="round"
        aria-hidden="true"
      >
        <circle cx="12" cy="12" r="10" />
        <path d="m7.5 12.5 3 3 6-6.5" />
      </svg>
      <div>
        <h4 className={styles.completedTitle}>¡Carga completada!</h4>
        <p className={styles.completedText}>
          Se registraron {savedSummary(report)}. Ya puedes verlos en Fichas
          académicas.
        </p>
      </div>
    </div>
  );
}
/** Estado de la carga antes de guardar: con errores o lista para importar. */
function ReportNotice({ report }: { report: FamilyImportResult }) {
  if (report.saved) return <ImportCompleted report={report} />;
  if (report.valid)
    return (
      <Feedback success="Los archivos no tienen errores. Pulsa «Importar» para guardarlos." />
    );
  return (
    <Feedback error="Hay errores en los archivos. No se guardó ninguna fila: corrígelos y vuelve a validar." />
  );
}
function ImportSummary({
  result,
  saved,
}: {
  result: ImportResult;
  saved: boolean;
}) {
  return (
    <section aria-live="polite">
      <dl className="detail-grid">
        <div>
          <dt>Filas en el archivo</dt>
          <dd>{result.totalRows}</dd>
        </div>
        <div>
          <dt>{saved ? "Registradas" : "Válidas"}</dt>
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
