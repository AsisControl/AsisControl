"use client";
import { useEffect, useId, useRef, useState, type FormEvent } from "react";
import { ApiError, fieldErrorsOf, message } from "@/core/api/client";
import { Dialog, Feedback, Field, FormDialog } from "@/shared/ui";
import { calificarEntrega, obtenerEntrega } from "./services/entrega-service";
import { obtenerTarea } from "./services/tarea-service";
import type { EntregaTareaResponse, TareaResponse } from "./types";

const businessErrors: Record<string, string> = {
  VERSION_DESACTUALIZADA: "La entrega cambió. Recarga su versión actual antes de guardar.",
  NOTA_FUERA_DE_RANGO: "La nota no puede superar el puntaje máximo de la tarea. Actualiza los datos si el puntaje cambió.",
  TAREA_NO_CALIFICABLE: "La tarea ya no permite calificar. Actualiza el seguimiento.",
  ENTREGA_ANULADA: "La entrega está anulada y no se puede calificar.",
  ENTREGA_NO_ENCONTRADA: "La entrega ya no está disponible. Actualiza el seguimiento.",
};

export function DeliveryGradeDialog({ deliveryId, taskId, student, onClose, onRefresh, onSaved }: {
  deliveryId: number; taskId: number; student: string;
  onClose: () => void; onRefresh: () => void; onSaved: () => void;
}) {
  const [snapshot, setSnapshot] = useState<{ delivery: EntregaTareaResponse; task: TareaResponse } | null>(null);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [nota, setNota] = useState("");
  const [comentario, setComentario] = useState("");
  const [conflict, setConflict] = useState(false);
  const [blocked, setBlocked] = useState(false);
  const [reload, setReload] = useState(0);
  const lock = useRef(false);
  const commentId = useId();

  useEffect(() => {
    let active = true;
    Promise.all([obtenerEntrega(deliveryId), obtenerTarea(taskId)]).then(([delivery, task]) => {
      if (!active) return;
      if (delivery.tareaId !== taskId) throw new Error("La entrega no pertenece a esta tarea.");
      setSnapshot({ delivery, task });
      setNota(delivery.nota === null ? "" : String(delivery.nota));
      setComentario(delivery.comentario ?? "");
      setErrors({});
      setConflict(false);
      setBlocked(false);
    }).catch((e) => { if (active) setError(message(e)); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [deliveryId, taskId, reload]);

  function reloadCurrent() {
    setLoading(true);
    setError("");
    setReload((value) => value + 1);
  }
  const allowed = snapshot && ["PUBLICADA", "CERRADA"].includes(snapshot.task.estado) && snapshot.delivery.estado !== "ANULADO";

  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (lock.current || !snapshot || !allowed || conflict || blocked) return;
    const validation: Record<string, string> = {};
    const value = Number(nota);
    if (!nota.trim() || !Number.isInteger(value) || value < 0 || value > snapshot.task.puntajeMaximo)
      validation.nota = `Introduce una nota entera entre 0 y ${snapshot.task.puntajeMaximo}.`;
    if (comentario.length > 2000) validation.comentario = "El comentario admite hasta 2000 caracteres.";
    setErrors(validation);
    if (Object.keys(validation).length) return;
    lock.current = true;
    setBusy(true);
    setError("");
    try {
      await calificarEntrega(deliveryId, { nota: value, comentario: comentario.trim() || null, version: snapshot.delivery.version });
      onSaved();
    } catch (e) {
      setError(e instanceof ApiError ? businessErrors[e.code] ?? message(e) : message(e));
      setErrors(fieldErrorsOf(e));
      if (e instanceof ApiError) {
        setConflict(e.code === "VERSION_DESACTUALIZADA");
        setBlocked(["TAREA_NO_CALIFICABLE", "ENTREGA_ANULADA", "ENTREGA_NO_ENCONTRADA"].includes(e.code) || [401, 403, 404].includes(e.status));
      }
    } finally { lock.current = false; setBusy(false); }
  }

  if (loading || !snapshot || !allowed || conflict || blocked) {
    return <Dialog title="Calificar entrega" description={student} busy={loading} onClose={onClose}>
      <Feedback error={error} />
      {loading ? <p role="status">Cargando entrega…</p> : conflict ? <>
        <p>Tus cambios no se guardaron. Recargar descarta la nota y el comentario que escribiste; no se reenviará la calificación automáticamente.</p>
        <button type="button" onClick={reloadCurrent}>Descartar cambios y recargar versión actual</button>
      </> : !snapshot ? <button type="button" onClick={reloadCurrent}>Reintentar carga</button>
        : !allowed && <p>Solo se pueden calificar entregas no anuladas de tareas publicadas o cerradas.</p>}
      {!loading && <div className="form-actions">
        <button type="button" onClick={onClose}>Cerrar</button>
        <button type="button" onClick={onRefresh}>Actualizar seguimiento</button>
      </div>}
    </Dialog>;
  }
  return <FormDialog title={snapshot.delivery.nota === null ? "Calificar entrega" : "Modificar calificación"} description={student}
    busy={busy} error={error} onClose={onClose} onSubmit={save} noValidate submitLabel="Guardar calificación">
    <Field label="Nota" type="number" min={0} max={snapshot.task.puntajeMaximo} step={1} required value={nota}
      onChange={(event) => setNota(event.target.value)} error={errors.nota} hint={`Puntaje máximo: ${snapshot.task.puntajeMaximo}.`} />
    <div className="field full-field"><label htmlFor={commentId}>Comentario</label>
      <textarea id={commentId} rows={4} maxLength={2000} value={comentario} aria-invalid={!!errors.comentario}
        aria-describedby={`${commentId}-hint${errors.comentario ? ` ${commentId}-error` : ""}`}
        onChange={(event) => setComentario(event.target.value)} />
      <small id={`${commentId}-hint`} className="field-hint">Comentario opcional para el estudiante.</small>
      {errors.comentario && <small id={`${commentId}-error`} className="field-error" role="alert">{errors.comentario}</small>}
    </div>
  </FormDialog>;
}
