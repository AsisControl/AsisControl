"use client";

import { useRef, useState, type FormEvent } from "react";
import { ApiError, message } from "@/core/api/client";
import { Dialog } from "@/shared/ui/dialog";
import { downloadTaskFile, findOwnDelivery, getDelivery, submitTask, updateDelivery, uploadTaskFile } from "./api";
import { canEditDelivery, canSubmit, taskDate, taskStatus } from "./presentation";
import type { StudentTask, TaskDelivery, TaskFile } from "./types";
import styles from "./student-tasks.module.css";

const ACCEPT = ".pdf,.jpg,.jpeg,.png,.doc,.docx,.xls,.xlsx,.ppt,.pptx";
const MAX_BYTES = 10 * 1024 * 1024;

export function TaskDialog({ task, onClose, onSaved }: {
  task: StudentTask; onClose: () => void; onSaved: () => void;
}) {
  const [latestDelivery, setLatestDelivery] = useState<TaskDelivery | null>(null);
  const [editing, setEditing] = useState(false);
  const [keptFiles, setKeptFiles] = useState<TaskFile[]>([]);
  const [files, setFiles] = useState<File[]>([]);
  const [busy, setBusy] = useState(false);
  const [progress, setProgress] = useState("");
  const [error, setError] = useState("");
  const [conflict, setConflict] = useState(false);
  const inFlight = useRef(false);
  // Conserva las cargas exitosas durante los reintentos de este formulario.
  const uploaded = useRef(new Map<File, TaskFile>());
  const { tarea } = task;
  const entrega = latestDelivery ?? task.entrega;
  const currentTask = { ...task, entrega };
  const creating = canSubmit(currentTask);
  const editable = creating || (editing && canEditDelivery(currentTask));
  const totalFiles = keptFiles.length + files.length;
  const changed = creating || files.length > 0 || keptFiles.length !== entrega?.archivos.length;
  const canSave = editable && totalFiles > 0 && totalFiles <= tarea.maxArchivosEntrega && changed && !conflict;

  async function startEditing() {
    if (!entrega || inFlight.current) return;
    inFlight.current = true; setBusy(true); setError("");
    try {
      const fresh = await getDelivery(entrega.id);
      setLatestDelivery(fresh);
      setKeptFiles(fresh.archivos);
      setFiles([]);
      setConflict(false);
      setEditing(canEditDelivery({ ...task, entrega: fresh }));
    } catch (e) { setError(message(e)); }
    finally { inFlight.current = false; setBusy(false); }
  }

  function cancelEditing() {
    setEditing(false); setKeptFiles([]); setFiles([]); setError(""); setConflict(false);
  }

  function selectFiles(selected: FileList | null) {
    const next = [...files, ...Array.from(selected ?? [])];
    if (keptFiles.length + next.length > tarea.maxArchivosEntrega) { setError(`Puedes adjuntar hasta ${tarea.maxArchivosEntrega} archivos.`); return; }
    if (next.some((f) => f.size === 0 || f.size > MAX_BYTES)) { setError("Cada archivo debe tener contenido y pesar como máximo 10 MB."); return; }
    if (next.some((f) => !ACCEPT.split(",").some((ext) => f.name.toLowerCase().endsWith(ext)))) {
      setError("Selecciona archivos PDF, imágenes JPG o PNG, Word, Excel o PowerPoint."); return;
    }
    setError(""); setFiles(next);
  }

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (inFlight.current || !canSave) return;
    inFlight.current = true; setBusy(true); setError("");
    let registering = false;
    const ids = keptFiles.map((file) => file.id);
    try {
      for (const [index, file] of files.entries()) {
        setProgress(`Cargando archivo ${index + 1} de ${files.length}...`);
        let saved = uploaded.current.get(file);
        if (!saved) { saved = await uploadTaskFile(file); uploaded.current.set(file, saved); }
        ids.push(saved.id);
      }
      setProgress("Guardando entrega..."); registering = true;
      if (editing && entrega) await updateDelivery(entrega, ids);
      else await submitTask(task, ids);
      onSaved();
    } catch (e) {
      const stale = e instanceof ApiError && e.status === 409 && editing;
      if (stale) setConflict(true);
      // Tras una respuesta perdida, solo confirmar una edición si coincide con lo enviado.
      if (registering && !stale) {
        try {
          const saved = editing && entrega ? await getDelivery(entrega.id) : await findOwnDelivery(tarea.id);
          if (saved && (!editing || (saved.version === entrega!.version + 1 &&
              saved.archivos.length === ids.length && saved.archivos.every((file) => ids.includes(file.id))))) {
            onSaved(); return;
          }
        } catch { /* Se conserva el error original y los archivos seleccionados. */ }
      }
      setError(message(e));
    } finally { inFlight.current = false; setBusy(false); setProgress(""); }
  }

  async function download(file: TaskFile) {
    setError(""); setBusy(true);
    try { await downloadTaskFile(file); } catch (e) { setError(message(e)); } finally { setBusy(false); }
  }

  function attachments(items: TaskFile[], removable = false) {
    return items.map((file) => <div className={styles.selectedFile} key={file.id}>
      <span>{file.nombreOriginal} <small>({Math.ceil(file.tamanioBytes / 1024)} KB)</small></span>
      <div className={styles.fileActions}>
        <button type="button" disabled={busy} onClick={() => download(file)}>Descargar</button>
        {removable && <button type="button" aria-label={`Eliminar ${file.nombreOriginal}`} disabled={busy} onClick={() => setKeptFiles((current) => current.filter((item) => item.id !== file.id))}>Eliminar</button>}
      </div>
    </div>);
  }

  return (
    <Dialog title={editable ? (editing ? "Editar entrega" : "Entregar tarea") : "Detalle de entrega"} onClose={onClose} busy={busy}>
      <div className={styles.taskHeader}>
        <h3>{tarea.nombre}</h3>
        <p>{task.curso} · Prof. {task.docente} · Vence: {taskDate(tarea.fechaLimite)}</p>
        <small>{task.seccion}</small>
      </div>
      <p className={styles.description}>{tarea.descripcion}</p>
      {tarea.archivos.length > 0 && <div><h3>Material de la tarea</h3>{attachments(tarea.archivos)}</div>}
      {entrega && <>
        <div className={styles.grade} aria-label="Calificación de la tarea">
          <span>Calificación</span>
          <strong>{entrega.nota === null ? "Pendiente de calificación" : `${entrega.nota} / ${tarea.puntajeMaximo}`}</strong>
        </div>
        {entrega.estado === "CALIFICADO" && entrega.comentario?.trim() && <section className={styles.teacherComment} aria-label="Comentario del profesor">
          <h4>Comentario del profesor</h4>
          <p className={styles.description}>{entrega.comentario}</p>
        </section>}
        <div className={styles.attachment}>
          <h3>Tu entrega</h3>
          <p className={styles.deliveryMeta}>{taskStatus(currentTask)} · {taskDate(entrega.fechaHoraEntrega)}</p>
          {attachments(editing ? keptFiles : entrega.archivos, editable && editing)}
          {!editable && !entrega.archivos.length && <p className={styles.hint}>Esta entrega no tiene archivos adjuntos.</p>}
        </div>
        {!editable && !canEditDelivery(currentTask) && <p className={styles.hint}>
          {entrega.estado === "CALIFICADO" ? "La entrega ya fue calificada y no se puede editar."
            : entrega.estado === "ANULADO" ? "La entrega está anulada y no se puede editar."
            : "La tarea ya no está recibiendo cambios en las entregas."}
        </p>}
      </>}
      {!creating && !entrega && <p>Esta tarea no está recibiendo entregas.</p>}
      {error && <p role="alert" className={styles.error}>{error}</p>}
      {conflict && <button type="button" disabled={busy} onClick={startEditing}>Recargar entrega</button>}
      {editable ? <form className={editing ? styles.editForm : undefined} onSubmit={submit}>
        {tarea.maxArchivosEntrega > 0 ? <>
          <div className={styles.filePicker}>
            <label htmlFor="task-file">{editing ? "Agregar archivos a la entrega" : "Selecciona los archivos de tu trabajo"}</label>
            <input id="task-file" type="file" multiple accept={ACCEPT} disabled={busy || conflict}
              aria-describedby="task-file-limits" onChange={(e) => { selectFiles(e.target.files); e.target.value = ""; }} />
            <small id="task-file-limits">Hasta {tarea.maxArchivosEntrega} archivos en total, 10 MB por archivo. PDF, JPG, PNG, Word, Excel o PowerPoint.</small>
          </div>
          {files.map((file, index) => <div className={styles.selectedFile} key={index}>
            <span>{file.name} <small>({Math.ceil(file.size / 1024)} KB)</small></span>
            <button type="button" aria-label={`Eliminar ${file.name}`} disabled={busy} onClick={() => setFiles((current) => current.filter((_, i) => i !== index))}>Eliminar</button>
          </div>)}
          <p className={styles.hint}>{editing ? "Los archivos que quites se retirarán de esta entrega al guardar los cambios. Debe quedar al menos un archivo." : "Adjunta al menos un archivo para entregar tu trabajo."}</p>
        </> : <p className={styles.hint}>Esta tarea no admite archivos. Consulta con tu docente para habilitar la entrega.</p>}
        {progress && <p role="status">{progress}</p>}
        <div className="form-actions">
          <button type="button" disabled={busy} onClick={editing ? cancelEditing : onClose}>Cancelar</button>
          <button type="submit" className="primary-action" disabled={busy || !canSave}>
            {busy ? "Guardando..." : editing ? "Guardar cambios" : "Entregar trabajo"}
          </button>
        </div>
      </form> : <div className="form-actions">
        <button type="button" disabled={busy} onClick={onClose}>Cerrar</button>
        {canEditDelivery(currentTask) && <button type="button" className="primary-action" disabled={busy} onClick={startEditing}>Editar entrega</button>}
      </div>}
    </Dialog>
  );
}
