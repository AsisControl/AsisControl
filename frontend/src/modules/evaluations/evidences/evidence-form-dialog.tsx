"use client";
import { useRef, useState, type ChangeEvent, type FormEvent } from "react";
import { FILE_ACCEPT, fileError, subirArchivo } from "@/modules/tasks";
import { FormDialog } from "@/shared/ui";
import { sectionOf } from "../lib/labels";
import type { CourseCompetency, OwnAssignment } from "../types";
import { evidenceError } from "./lib/messages";
import { crearEvidencia } from "./services/evidence-service";
import type { Evidence, EvidencePeriod, EvidenceStudent } from "./types";
import styles from "./evidences.module.css";

export const MAX_DESCRIPTION = 1000;

/** Registro de una evidencia: se sube el archivo y luego se crea la evidencia con su id. */
export function EvidenceFormDialog({
  assignment,
  student,
  competencies,
  periods,
  competenciaId = "",
  periodoId = "",
  onClose,
  onSaved,
}: {
  assignment: OwnAssignment;
  student: EvidenceStudent;
  competencies: CourseCompetency[];
  periods: EvidencePeriod[];
  /** Filtros activos del listado, para no volver a elegirlos. */
  competenciaId?: string;
  periodoId?: string;
  onClose: () => void;
  onSaved: (evidence: Evidence) => void;
}) {
  const selector = useRef<HTMLInputElement>(null);
  // Un archivo ya subido se conserva: reintentar no lo vuelve a cargar.
  const uploaded = useRef<number | null>(null);
  const [competencia, setCompetencia] = useState(competenciaId);
  const [periodo, setPeriodo] = useState(periodoId);
  const [descripcion, setDescripcion] = useState("");
  const [file, setFile] = useState<File | null>(null);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const choose = (event: ChangeEvent<HTMLInputElement>) => {
    const chosen = event.target.files?.[0] ?? null;
    event.target.value = "";
    const problem = chosen ? fileError(chosen) : "";
    setError(problem);
    uploaded.current = null;
    setFile(problem ? null : chosen);
  };
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const text = descripcion.trim();
    if (!text && !file) {
      setError("Escribe una descripción o adjunta un archivo.");
      return;
    }
    setBusy(true);
    setError("");
    try {
      if (file && uploaded.current === null) uploaded.current = (await subirArchivo(file)).id;
      onSaved(
        await crearEvidencia({
          asignacionId: assignment.id,
          alumnoId: student.id,
          competenciaId: Number(competencia),
          periodoId: Number(periodo),
          descripcion: text || null,
          archivoId: uploaded.current,
        }),
      );
    } catch (e) {
      setError(evidenceError(e));
      setBusy(false);
    }
  }
  return (
    <FormDialog
      title="Nueva evidencia"
      description={`${student.nombre} · ${assignment.curso} · ${sectionOf(assignment)}`}
      busy={busy}
      error={error}
      submitLabel="Guardar evidencia"
      onClose={onClose}
      onSubmit={submit}
    >
      <label className="field">
        Competencia <span className="field-required">*</span>
        <select required value={competencia} onChange={(event) => setCompetencia(event.target.value)}>
          <option value="">Selecciona una competencia</option>
          {competencies.map((item) => (
            <option key={item.competenciaId} value={item.competenciaId}>
              {item.competenciaNombre}
            </option>
          ))}
        </select>
      </label>
      <label className="field">
        Período <span className="field-required">*</span>
        <select required value={periodo} onChange={(event) => setPeriodo(event.target.value)}>
          <option value="">Selecciona un período</option>
          {periods.map((item) => (
            <option key={item.id} value={item.id}>
              {item.nombre}
            </option>
          ))}
        </select>
      </label>
      <label className="field full-field">
        Descripción
        <textarea
          rows={4}
          maxLength={MAX_DESCRIPTION}
          value={descripcion}
          placeholder="Describe qué muestra la evidencia…"
          onChange={(event) => setDescripcion(event.target.value)}
        />
        <small className="field-hint">
          Obligatoria si no adjuntas archivo. Máximo {MAX_DESCRIPTION} caracteres.
        </small>
      </label>
      <div className={`full-field ${styles.attachment}`}>
        <strong>Archivo (opcional)</strong>
        <p>PDF, imagen, Word, Excel o PowerPoint de hasta 10 MiB.</p>
        <input
          ref={selector}
          hidden
          type="file"
          accept={FILE_ACCEPT}
          aria-label="Archivo de evidencia"
          onChange={choose}
        />
        {file ? (
          <p className={styles.fileName}>
            {file.name}
            <button
              type="button"
              onClick={() => {
                uploaded.current = null;
                setFile(null);
              }}
            >
              Quitar
            </button>
          </p>
        ) : (
          <button type="button" onClick={() => selector.current?.click()}>
            Seleccionar archivo
          </button>
        )}
      </div>
    </FormDialog>
  );
}
