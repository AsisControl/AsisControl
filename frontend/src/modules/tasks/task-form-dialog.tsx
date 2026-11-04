"use client";
import { useEffect, useState, type FormEvent } from "react";
import { ApiError, fieldErrorsOf, message } from "@/core/api/client";
import { Dialog, Feedback, Field, FormDialog } from "@/shared/ui";
import {
  actualizarTarea, contarEntregas, crearTarea, listarAsignacionesPropias, obtenerTarea, obtenerContextoAsignacion,
} from "./services/tarea-service";
import { subirArchivo } from "./services/archivo-service";
import { fileError, validateTask, type TaskFields } from "./task-form-rules";
import { TaskAttachments, type NewAttachment } from "./task-attachments";
import type { ArchivoResponse, TareaResponse, AsignacionCursoResponse } from "./types";

export const REASON_MAX = 500;

/**
 * Edición (taskId), alta con asignación ya elegida (asignacionCursoId) o alta desde "Nueva tarea",
 * que ofrece solo las asignaciones activas del docente autenticado (/api/asignaciones-cursos/mias).
 */
type Props = {
  onClose: () => void;
  onSaved: (task: TareaResponse) => void;
  canUpload: boolean;
} & (
  | { taskId: number; asignacionCursoId?: never }
  | { taskId?: never; asignacionCursoId: number }
  | { taskId?: never; asignacionCursoId?: never }
);

const assignmentLabel = (a: AsignacionCursoResponse) => `${a.curso} · ${a.seccion} · ${a.anioAcademico}`;
const editable = (task: TareaResponse) => task.estado === "BORRADOR" || task.estado === "PUBLICADA";

export function TaskFormDialog({ taskId, asignacionCursoId, canUpload, onClose, onSaved }: Props) {
  const choosing = taskId === undefined && asignacionCursoId === undefined;
  const [task, setTask] = useState<TareaResponse | null>(null);
  const [fields, setFields] = useState<TaskFields>({ nombre: "", descripcion: "", fechaPublicacion: "", fechaLimite: "", puntajeMaximo: "20", maxArchivosEntrega: "0" });
  const [existing, setExisting] = useState<ArchivoResponse[]>([]);
  const [pending, setPending] = useState<NewAttachment[]>([]);
  const [loading, setLoading] = useState(taskId !== undefined);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [conflict, setConflict] = useState(false);
  const [reload, setReload] = useState(0);
  const [context, setContext] = useState<{ source: TareaResponse; value: AsignacionCursoResponse | null } | null>(null);
  const [ownAssignments, setOwnAssignments] = useState<AsignacionCursoResponse[] | null>(null);
  const [selected, setSelected] = useState("");
  const [deliveries, setDeliveries] = useState<{ source: TareaResponse; count: number | null } | null>(null);
  const [motivo, setMotivo] = useState("");

  useEffect(() => {
    if (!task) return;
    let active = true;
    obtenerContextoAsignacion(task.asignacionCursoId).then((value) => {
      if (active) setContext({ source: task, value: value.id === task.asignacionCursoId ? value : null });
    }).catch(() => { if (active) setContext({ source: task, value: null }); });
    return () => { active = false; };
  }, [task]);

  useEffect(() => {
    // Solo una tarea publicada puede tener entregas: de ellas depende que el motivo sea obligatorio.
    if (!task || task.estado !== "PUBLICADA") return;
    let active = true;
    contarEntregas(task.id).then((count) => { if (active) setDeliveries({ source: task, count }); })
      .catch(() => { if (active) setDeliveries({ source: task, count: null }); });
    return () => { active = false; };
  }, [task]);

  useEffect(() => {
    if (!choosing) return;
    let active = true;
    listarAsignacionesPropias().then((items) => {
      if (!active) return;
      setOwnAssignments(items);
      if (items.length === 1) setSelected(String(items[0].id));
    }).catch((e) => { if (active) { setOwnAssignments([]); setError(message(e)); } });
    return () => { active = false; };
  }, [choosing]);

  useEffect(() => {
    if (taskId === undefined) return;
    let active = true;
    obtenerTarea(taskId).then((value) => {
      if (!active) return;
      setTask(value);
      setFields({ nombre: value.nombre, descripcion: value.descripcion, fechaPublicacion: value.fechaPublicacion ?? "", fechaLimite: value.fechaLimite, puntajeMaximo: String(value.puntajeMaximo), maxArchivosEntrega: String(value.maxArchivosEntrega) });
      setExisting(value.archivos);
      setPending([]);
      setConflict(false);
      setErrors({});
      setMotivo("");
    }).catch((e) => { if (active) setError(message(e)); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [taskId, reload]);

  function reloadCurrent() {
    setLoading(true);
    setError("");
    setReload((value) => value + 1);
  }

  const published = task?.estado === "PUBLICADA";
  // Se comparan instantes: el control datetime-local puede normalizar "18:30:00" a "18:30".
  const limitChanged = published && new Date(fields.fechaLimite).getTime() !== new Date(task.fechaLimite).getTime();
  const deliveryCount = deliveries?.source === task ? deliveries.count : undefined;
  // Si no se pudo contar, se pide igual: el backend lo exige solo si hay entregas.
  const reasonRequired = limitChanged && deliveryCount !== 0;

  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (busy || conflict || (taskId !== undefined && (!task || !editable(task)))) return;
    const validation = validateTask(fields, existing.length + pending.length);
    if (choosing && !selected) validation.asignacionCursoId = "Elige el curso y la sección de la tarea.";
    if (limitChanged && new Date(fields.fechaLimite) <= new Date())
      validation.fechaLimite = "La nueva fecha límite de una tarea publicada debe ser futura.";
    if (reasonRequired && deliveryCount && !motivo.trim())
      validation.motivo = "La tarea ya tiene entregas: indica el motivo del cambio de fecha límite.";
    setErrors(validation);
    if (Object.keys(validation).length) return;
    setBusy(true);
    setError("");
    const uploaded = [...pending];
    try {
      for (let index = 0; index < uploaded.length; index++) {
        if (!uploaded[index].uploaded) {
          if (!canUpload) throw new Error("No tienes permiso para subir archivos nuevos.");
          uploaded[index] = { ...uploaded[index], uploaded: await subirArchivo(uploaded[index].file) };
          setPending([...uploaded]);
        }
      }
      const input = {
        nombre: fields.nombre.trim(), descripcion: fields.descripcion.trim(),
        fechaPublicacion: fields.fechaPublicacion || null, fechaLimite: fields.fechaLimite,
        puntajeMaximo: Number(fields.puntajeMaximo), maxArchivosEntrega: Number(fields.maxArchivosEntrega),
        archivoIds: [...new Set([...existing.map((file) => file.id), ...uploaded.map((item) => item.uploaded!.id)])],
      };
      onSaved(taskId !== undefined && task
        ? await actualizarTarea(taskId, {
          ...input,
          // Publicada: la publicación no se reprograma; se reenvía el valor exacto del servidor.
          fechaPublicacion: published ? task.fechaPublicacion : input.fechaPublicacion,
          version: task.version,
          motivo: limitChanged ? motivo.trim() || null : null,
        })
        : await crearTarea({ ...input, asignacionCursoId: asignacionCursoId ?? Number(selected) }));
    } catch (e) {
      setError(message(e));
      setErrors(fieldErrorsOf(e));
      if (e instanceof ApiError && e.code === "VERSION_DESACTUALIZADA") setConflict(true);
    } finally { setBusy(false); }
  }

  if (loading || (taskId !== undefined && !task) || conflict || (task && !editable(task))) {
    return <Dialog title="Editar tarea" onClose={onClose} busy={loading}>
      <Feedback error={error} />
      {loading ? <p role="status">Cargando tarea…</p> : conflict ? <>
        <p>La tarea cambió en el servidor. Tus cambios no se han guardado. Recargar descarta los cambios locales y los adjuntos nuevos seleccionados; los archivos ya subidos pueden quedar sin vincular.</p>
        <button type="button" onClick={reloadCurrent}>Descartar cambios y recargar versión actual</button>
      </> : task ? <p>La tarea está en estado {task.estado}. Solo se pueden editar borradores o tareas publicadas.</p>
        : <button type="button" onClick={reloadCurrent}>Reintentar carga</button>}
      <div className="form-actions"><button type="button" disabled={loading} onClick={onClose}>Cerrar</button></div>
    </Dialog>;
  }

  const field = (key: keyof TaskFields) => ({ value: fields[key], onChange: (event: React.ChangeEvent<HTMLInputElement>) => setFields({ ...fields, [key]: event.target.value }), error: errors[key] });
  return <FormDialog title={taskId !== undefined ? "Editar tarea" : "Nueva tarea"}
    description={published
      ? "La tarea seguirá publicada y los estudiantes verán los cambios. La asignación de curso y sección se conserva."
      : "Guarda los cambios como borrador. La asignación de curso y sección se conserva."}
    busy={busy} error={error} onClose={onClose} onSubmit={save} noValidate
    submitLabel={taskId !== undefined ? "Guardar cambios" : "Guardar borrador"}>
    {task && <div className="full-field" role="group" aria-label="Curso y sección asociados">
      {context?.source !== task ? <p role="status">Cargando curso y sección…</p>
        : context.value ? <p>Curso: <strong>{context.value.curso}</strong> · Sección: <strong>{context.value.seccion}</strong> · {context.value.anioAcademico}</p>
          : <p className="hint">Curso y sección no disponibles. No se pudo consultar el contexto; la asignación original se conserva.</p>}
      <p className="hint">Información de solo lectura. La asignación no se puede cambiar al editar.</p>
    </div>}
    {choosing && <label className="field full-field">Curso y sección *
      <select value={selected} disabled={!ownAssignments?.length} aria-invalid={!!errors.asignacionCursoId}
        onChange={(event) => { setSelected(event.target.value); setErrors({ ...errors, asignacionCursoId: "" }); }}>
        <option value="">{ownAssignments === null ? "Cargando tus cursos…" : "Selecciona un curso"}</option>
        {ownAssignments?.map((item) => <option key={item.id} value={item.id}>{assignmentLabel(item)}</option>)}
      </select>
      {errors.asignacionCursoId ? <small className="field-error" role="alert">{errors.asignacionCursoId}</small>
        : ownAssignments?.length === 0 && <small className="field-hint">No tienes cursos asignados activos. Solicita la asignación a la coordinación académica.</small>}
    </label>}
    <Field label="Nombre" required maxLength={150} fullWidth {...field("nombre")} />
    <label className="field full-field">Descripción *
      <textarea required maxLength={3000} rows={4} value={fields.descripcion} aria-invalid={!!errors.descripcion}
        onChange={(event) => setFields({ ...fields, descripcion: event.target.value })} />
      {errors.descripcion && <small className="field-error" role="alert">{errors.descripcion}</small>}
    </label>
    <Field label="Fecha de publicación" type="datetime-local" step="any" {...field("fechaPublicacion")} readOnly={published}
      hint={published ? "La tarea ya está publicada: esta fecha no se puede cambiar." : "Opcional. Se completa al publicar si la dejas vacía."} />
    <Field label="Fecha límite" type="datetime-local" step="any" required {...field("fechaLimite")}
      hint={published && deliveryCount ? `La tarea tiene ${deliveryCount} ${deliveryCount === 1 ? "entrega" : "entregas"}: cambiar esta fecha exige un motivo.` : undefined} />
    <Field label="Puntaje máximo" type="number" min={1} max={1000} step={1} required {...field("puntajeMaximo")} />
    <Field label="Máximo de archivos por entrega" type="number" min={0} max={20} step={1} required {...field("maxArchivosEntrega")} />
    {limitChanged && <label className="field full-field">Motivo del cambio de fecha límite{reasonRequired && <span className="field-required"> *</span>}
      <textarea name="motivo" rows={2} maxLength={REASON_MAX} value={motivo} aria-invalid={!!errors.motivo}
        placeholder="Ej. Se amplía el plazo por un feriado"
        onChange={(event) => { setMotivo(event.target.value); setErrors({ ...errors, motivo: "" }); }} />
      {errors.motivo ? <small className="field-error" role="alert">{errors.motivo}</small>
        : <small className="field-hint">{deliveryCount === 0 ? "Opcional: aún no hay entregas." : "Se registra en la auditoría."} Máximo {REASON_MAX} caracteres.</small>}
    </label>}
    <TaskAttachments existing={existing} pending={pending} disabled={busy} canUpload={canUpload} error={errors.archivoIds}
      onRemoveExisting={(id) => setExisting(existing.filter((file) => file.id !== id))}
      onRemoveNew={(index) => setPending(pending.filter((_, position) => position !== index))}
      onSelect={(files) => {
        const invalid = files.map(fileError).find(Boolean);
        if (invalid || existing.length + pending.length + files.length > 20) {
          setErrors({ ...errors, archivoIds: invalid || "La tarea admite hasta 20 adjuntos." });
          return;
        }
        setErrors({ ...errors, archivoIds: "" });
        setPending([...pending, ...files.map((file) => ({ file }))]);
      }} />
  </FormDialog>;
}
