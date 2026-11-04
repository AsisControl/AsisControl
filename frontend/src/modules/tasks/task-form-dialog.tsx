"use client";
import { useEffect, useState, type FormEvent } from "react";
import { ApiError, fieldErrorsOf, message } from "@/core/api/client";
import { Dialog, Feedback, Field, FormDialog } from "@/shared/ui";
import { actualizarTarea, crearTarea, obtenerTarea, obtenerContextoAsignacion } from "./services/tarea-service";
import { subirArchivo } from "./services/archivo-service";
import { fileError, validateTask, type TaskFields } from "./task-form-rules";
import { TaskAttachments, type NewAttachment } from "./task-attachments";
import type { ArchivoResponse, TareaResponse, AsignacionCursoResponse } from "./types";

type Props = {
  onClose: () => void;
  onSaved: (task: TareaResponse) => void;
  canUpload: boolean;
} & ({ taskId: number; asignacionCursoId?: never } | { taskId?: never; asignacionCursoId: number });

export function TaskFormDialog({ taskId, asignacionCursoId, canUpload, onClose, onSaved }: Props) {
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

  useEffect(() => {
    if (!task) return;
    let active = true;
    obtenerContextoAsignacion(task.asignacionCursoId).then((value) => {
      if (active) setContext({ source: task, value: value.id === task.asignacionCursoId ? value : null });
    }).catch(() => { if (active) setContext({ source: task, value: null }); });
    return () => { active = false; };
  }, [task]);

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
    }).catch((e) => { if (active) setError(message(e)); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [taskId, reload]);

  function reloadCurrent() {
    setLoading(true);
    setError("");
    setReload((value) => value + 1);
  }

  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (busy || conflict || (taskId !== undefined && (!task || task.estado !== "BORRADOR"))) return;
    const validation = validateTask(fields, existing.length + pending.length);
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
        ? await actualizarTarea(taskId, { ...input, version: task.version })
        : await crearTarea({ ...input, asignacionCursoId: asignacionCursoId! }));
    } catch (e) {
      setError(message(e));
      setErrors(fieldErrorsOf(e));
      if (e instanceof ApiError && e.code === "VERSION_DESACTUALIZADA") setConflict(true);
    } finally { setBusy(false); }
  }

  if (loading || (taskId !== undefined && !task) || conflict || (task && task.estado !== "BORRADOR")) {
    return <Dialog title="Editar tarea" onClose={onClose} busy={loading}>
      <Feedback error={error} />
      {loading ? <p role="status">Cargando tarea…</p> : conflict ? <>
        <p>La tarea cambió en el servidor. Tus cambios no se han guardado. Recargar descarta los cambios locales y los adjuntos nuevos seleccionados; los archivos ya subidos pueden quedar sin vincular.</p>
        <button type="button" onClick={reloadCurrent}>Descartar cambios y recargar versión actual</button>
      </> : task ? <p>La tarea está en estado {task.estado}. Solo se pueden editar borradores.</p>
        : <button type="button" onClick={reloadCurrent}>Reintentar carga</button>}
      <div className="form-actions"><button type="button" disabled={loading} onClick={onClose}>Cerrar</button></div>
    </Dialog>;
  }

  const field = (key: keyof TaskFields) => ({ value: fields[key], onChange: (event: React.ChangeEvent<HTMLInputElement>) => setFields({ ...fields, [key]: event.target.value }), error: errors[key] });
  return <FormDialog title={taskId !== undefined ? "Editar tarea" : "Nueva tarea"} description="Guarda los cambios como borrador. La asignación de curso y sección se conserva."
    busy={busy} error={error} onClose={onClose} onSubmit={save} noValidate
    submitLabel={taskId !== undefined ? "Guardar cambios" : "Guardar borrador"}>
    {task && <div className="full-field" role="group" aria-label="Curso y sección asociados">
      {context?.source !== task ? <p role="status">Cargando curso y sección…</p>
        : context.value ? <p>Curso: <strong>{context.value.curso}</strong> · Sección: <strong>{context.value.seccion}</strong> · {context.value.anioAcademico}</p>
          : <p className="hint">Curso y sección no disponibles. No se pudo consultar el contexto; la asignación original se conserva.</p>}
      <p className="hint">Información de solo lectura. La asignación no se puede cambiar al editar.</p>
    </div>}
    <Field label="Nombre" required maxLength={150} fullWidth {...field("nombre")} />
    <label className="field full-field">Descripción *
      <textarea required maxLength={3000} rows={4} value={fields.descripcion} aria-invalid={!!errors.descripcion}
        onChange={(event) => setFields({ ...fields, descripcion: event.target.value })} />
      {errors.descripcion && <small className="field-error" role="alert">{errors.descripcion}</small>}
    </label>
    <Field label="Fecha de publicación" type="datetime-local" step="any" {...field("fechaPublicacion")} hint="Opcional. Se completa al publicar si la dejas vacía." />
    <Field label="Fecha límite" type="datetime-local" step="any" required {...field("fechaLimite")} />
    <Field label="Puntaje máximo" type="number" min={1} max={1000} step={1} required {...field("puntajeMaximo")} />
    <Field label="Máximo de archivos por entrega" type="number" min={0} max={20} step={1} required {...field("maxArchivosEntrega")} />
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
