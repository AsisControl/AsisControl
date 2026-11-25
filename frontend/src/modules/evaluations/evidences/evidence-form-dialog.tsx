"use client";
import { useEffect, useRef, useState, type ChangeEvent, type FormEvent } from "react";
import { ApiError } from "@/core/api/client";
import { FILE_ACCEPT, fileError, subirArchivo } from "@/modules/tasks";
import { FormDialog } from "@/shared/ui";
import { sectionOf } from "../lib/labels";
import { EvidenceFileCard } from "../evidence-file-card";
import { listProgressEvaluations } from "../services/progress-service";
import type { Evaluation, OwnAssignment } from "../types";
import { competencyLabel } from "./lib/format";
import { evidenceError } from "./lib/messages";
import { crearEvidencia, editarEvidencia, vincularEvidencia } from "./services/evidence-service";
import type { Evidence, EvidenceCompetency, EvidencePeriod, EvidenceStudent } from "./types";
import styles from "./evidences.module.css";

export const MAX_DESCRIPTION = 1000;

/** Registro o edición de una evidencia: se sube el archivo y luego se guarda con su id. */
export function EvidenceFormDialog({
  assignment,
  student,
  competencies,
  periods,
  competenciaId = "",
  periodoId = "",
  evidence,
  onClose,
  onSaved,
}: {
  assignment: OwnAssignment;
  student: EvidenceStudent;
  competencies: EvidenceCompetency[];
  periods: EvidencePeriod[];
  /** Filtros activos del listado, para no volver a elegirlos. */
  competenciaId?: string;
  periodoId?: string;
  /** Con una evidencia existente el contexto se conserva. */
  evidence?: Evidence;
  onClose: (refresh: boolean) => void;
  onSaved: (evidence: Evidence) => void;
}) {
  const selector = useRef<HTMLInputElement>(null);
  // Un archivo ya subido se conserva: reintentar no lo vuelve a cargar.
  const uploaded = useRef<number | null>(null);
  const [competencia, setCompetencia] = useState(evidence ? String(evidence.competenciaId) : competenciaId);
  const [periodo, setPeriodo] = useState(evidence ? String(evidence.periodoId) : periodoId);
  const [descripcion, setDescripcion] = useState(evidence?.descripcion ?? "");
  const [keep, setKeep] = useState(evidence?.archivo ?? null);
  const [file, setFile] = useState<File | null>(null);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const [saved, setSaved] = useState<Evidence | null>(null);
  const [evaluations, setEvaluations] = useState<Evaluation[] | null>(null);
  const [evaluationsError, setEvaluationsError] = useState("");
  const [evaluationId, setEvaluationId] = useState("");
  const linked = (saved ?? evidence)?.evaluaciones ?? [];
  const lockedContent = !!saved || linked.length > 0;
  const selectedPeriod = periods.find((item) => String(item.id) === periodo);
  // El alumno se elige de la planilla de la sección de esta asignación.
  const compatible = evaluations?.filter((item) => selectedPeriod && item.idAsignacionCurso === assignment.id
    && item.fechaEvaluacion >= selectedPeriod.fechaInicio && item.fechaEvaluacion <= selectedPeriod.fechaFin
    && item.competencias.some((competency) => String(competency.id) === competencia)
    && !linked.some((evaluation) => evaluation.id === item.id)) ?? [];
  useEffect(() => {
    let active = true;
    listProgressEvaluations(assignment.id).then(
      (items) => { if (active) setEvaluations(items); },
      (failure) => { if (active) setEvaluationsError(evidenceError(failure)); },
    );
    return () => { active = false; };
  }, [assignment.id]);
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
    const selected = compatible.find((item) => String(item.id) === evaluationId);
    if ((evaluationId || saved) && !selected) {
      setError("Selecciona una evaluación compatible para vincular la evidencia.");
      return;
    }
    if (linked.length && !selected) {
      onClose(false);
      return;
    }
    const text = descripcion.trim();
    if (!lockedContent && !text && !file && !keep) {
      setError("Escribe una descripción o adjunta un archivo.");
      return;
    }
    setBusy(true);
    setError("");
    let record = saved ?? (linked.length ? evidence : undefined);
    try {
      if (!record) {
        if (file && uploaded.current === null) uploaded.current = (await subirArchivo(file)).id;
        const archivoId = uploaded.current ?? keep?.id ?? null;
        record = evidence
          ? await editarEvidencia(evidence.id, { descripcion: text || null, archivoId })
          : await crearEvidencia({
              asignacionId: assignment.id,
              alumnoId: student.id,
              competenciaId: Number(competencia),
              periodoId: Number(periodo),
              descripcion: text || null,
              archivoId,
            });
      }
      if (selected) {
        setSaved(record);
        await vincularEvidencia(record.id, selected.id);
      }
      onSaved(record);
    } catch (e) {
      const rejected = e instanceof ApiError && e.status >= 400 && e.status < 500 && e.status !== 408;
      const linkFailure = rejected && !linked.length
        ? "La evidencia está guardada y quedó sin vincular."
        : "La evidencia está guardada. No se pudo confirmar el nuevo vínculo.";
      setError(record && selected ? `${linkFailure} ${evidenceError(e)} Puedes reintentar la vinculación.` : evidenceError(e));
      setBusy(false);
    }
  }
  return (
    <FormDialog
      title={evidence ? "Editar evidencia" : "Nueva evidencia"}
      description={`${student.nombre} · ${assignment.curso} · ${sectionOf(assignment)}`}
      busy={busy}
      error={error}
      submitLabel={saved ? "Reintentar vinculación" : linked.length && !evaluationId ? "Cerrar" : evidence ? "Guardar cambios" : "Guardar evidencia"}
      onClose={() => onClose(!!saved)}
      onSubmit={submit}
    >
      <label className="field">
        Competencia <span className="field-required">*</span>
        <select required disabled={!!evidence || !!saved} value={competencia} onChange={(event) => {
          setCompetencia(event.target.value);
          setEvaluationId("");
        }}>
          <option value="">Selecciona una competencia</option>
          {evidence?.competenciaNombre && !competencies.some(item => item.competenciaId === evidence.competenciaId) && (
            <option value={evidence.competenciaId}>{competencyLabel({
              competenciaNombre: evidence.competenciaNombre, competenciaCodigo: evidence.competenciaCodigo,
            })}</option>
          )}
          {competencies.map((item) => (
            <option key={item.competenciaId} value={item.competenciaId}>
              {competencyLabel(item)}
            </option>
          ))}
        </select>
      </label>
      <label className="field">
        Período <span className="field-required">*</span>
        <select required disabled={!!evidence || !!saved} value={periodo} onChange={(event) => {
          setPeriodo(event.target.value);
          setEvaluationId("");
        }}>
          <option value="">Selecciona un período</option>
          {periods.map((item) => (
            <option key={item.id} value={item.id}>
              {item.nombre}
            </option>
          ))}
        </select>
      </label>
      {linked.length > 0 && <div className="full-field">
        <strong>Evaluaciones vinculadas</strong>
        <ul>{linked.map((item) => <li key={item.id}>{item.titulo}</li>)}</ul>
        <p className="hint">El contenido de una evidencia vinculada se conserva.</p>
      </div>}
      <label className="field full-field">
        <span>{linked.length ? "Agregar evaluación" : "Evaluación"}</span>
        <select disabled={!compatible.length} value={evaluationId} onChange={(event) => setEvaluationId(event.target.value)}>
          <option value="">{linked.length ? "Conservar vínculos existentes" : "Sin vincular"}</option>
          {compatible.map((item) => <option key={item.id} value={item.id}>{item.titulo} · {item.fechaEvaluacion}</option>)}
        </select>
      </label>
      {evaluationsError ? <p className="full-field" role="alert">No se pudieron cargar las evaluaciones. {evaluationsError}</p>
        : competencia && periodo && <p className="full-field hint">{evaluations === null ? "Cargando evaluaciones…"
          : !compatible.length ? "No hay evaluaciones compatibles para agregar."
          : "Vincula una evaluación para mostrar esta evidencia como sustento en su fila de la libreta."}</p>}
      <label className="field full-field">
        Descripción
        <textarea
          rows={4}
          maxLength={MAX_DESCRIPTION}
          value={descripcion}
          readOnly={lockedContent}
          placeholder="Describe qué muestra la evidencia…"
          onChange={(event) => setDescripcion(event.target.value)}
        />
        <small className="field-hint">
          Obligatoria si no adjuntas archivo. Máximo {MAX_DESCRIPTION} caracteres.
        </small>
      </label>
      <div className={`full-field ${styles.attachment}`}>
        <div className={styles.attachmentHeading}>
          <span className={styles.attachmentIcon} aria-hidden="true">📎</span>
          <div><strong>Adjuntar archivo</strong> <span className="hint">(opcional)</span>
            <p>PDF, imagen, Word, Excel o PowerPoint · Máx. 10 MiB</p>
          </div>
        </div>
        <input
          ref={selector}
          hidden
          type="file"
          accept={FILE_ACCEPT}
          aria-label="Archivo de evidencia"
          onChange={choose}
        />
        {!file && keep && (
          <EvidenceFileCard name={keep.nombreOriginal} mime={keep.tipoMime} bytes={keep.tamanioBytes}>
            <button type="button" className="secondary-action" disabled={lockedContent} onClick={() => setKeep(null)}>
              Quitar
            </button>
          </EvidenceFileCard>
        )}
        {file ? (
          <EvidenceFileCard name={file.name} mime={file.type} bytes={file.size}>
            <button
              type="button"
              className="secondary-action"
              disabled={lockedContent}
              onClick={() => {
                uploaded.current = null;
                setFile(null);
              }}
            >
              Quitar
            </button>
          </EvidenceFileCard>
        ) : (
          <button type="button" className="secondary-action" disabled={lockedContent} onClick={() => selector.current?.click()}>
            {keep ? "Reemplazar archivo" : "Seleccionar archivo"}
          </button>
        )}
      </div>
    </FormDialog>
  );
}
