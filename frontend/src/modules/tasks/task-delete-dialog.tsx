"use client";
import { useEffect, useRef, useState, type FormEvent } from "react";
import { ApiError } from "@/core/api/client";
import { fieldErrorsOf, message } from "./task-error-copy";
import { FormDialog } from "@/shared/ui";
import { contarEntregas, eliminarTarea } from "./services/tarea-service";
import { REASON_MAX } from "./task-form-dialog";
import type { TareaResponse } from "./types";

/**
 * US-22: elimina la tarea en cualquier estado (baja lógica). Si ya tiene entregas, el motivo es
 * obligatorio y queda en la auditoría; las entregas se conservan en el servidor.
 */
export function TaskDeleteDialog({ task, onClose, onSaved }: {
  task: TareaResponse;
  onClose: () => void;
  onSaved: () => void;
}) {
  // Un borrador nunca tiene entregas: no hace falta consultarlas.
  const [count, setCount] = useState<number | null | undefined>(task.estado === "BORRADOR" ? 0 : undefined);
  const [reason, setReason] = useState("");
  const [reasonError, setReasonError] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const sending = useRef(false);

  useEffect(() => {
    if (task.estado === "BORRADOR") return;
    let active = true;
    contarEntregas(task.id).then((value) => { if (active) setCount(value); })
      .catch(() => { if (active) setCount(null); });
    return () => { active = false; };
  }, [task]);

  // Si no se pudo contar se pide igual: el backend lo exige solo si hay entregas.
  const required = count !== 0;
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (sending.current || count === undefined) return;
    const motivo = reason.trim();
    if (required && !motivo) {
      setReasonError(count
        ? "La tarea ya tiene entregas: indica el motivo de la eliminación."
        : "No se pudo confirmar si hay entregas: indica el motivo de la eliminación.");
      return;
    }
    sending.current = true;
    setBusy(true);
    setError("");
    try {
      await eliminarTarea(task.id, motivo || null);
      onSaved();
    } catch (e) {
      setError(message(e));
      const fields = fieldErrorsOf(e);
      setReasonError(fields.motivo ?? (e instanceof ApiError && e.code === "TAREA_MOTIVO_REQUERIDO" ? message(e, "Indica el motivo de la eliminación.") : ""));
      sending.current = false;
      setBusy(false);
    }
  }

  return <FormDialog title="Eliminar tarea" description={task.nombre} submitLabel="Eliminar tarea" danger
    busy={busy || count === undefined} error={error} onClose={onClose} onSubmit={submit} noValidate>
    <p className="full-field hint">
      {count === undefined ? "Consultando las entregas de la tarea…"
        : count ? `La tarea tiene ${count} ${count === 1 ? "entrega" : "entregas"}. Dejará de mostrarse a los estudiantes y apoderados; las entregas se conservarán para su consulta.`
          : "La tarea dejará de mostrarse a los estudiantes y apoderados. Esta acción no se puede deshacer desde la pantalla."}
    </p>
    {count !== 0 && <label className="field full-field">Motivo{required && <span className="field-required"> *</span>}
      <textarea name="motivo" rows={3} maxLength={REASON_MAX} value={reason} disabled={busy}
        placeholder="Ej. Tarea duplicada por error" aria-invalid={reasonError ? true : undefined}
        onChange={(event) => { setReason(event.target.value); setReasonError(""); }} />
      {reasonError ? <small className="field-error" role="alert">{reasonError}</small>
        : <small className="field-hint">El motivo quedará registrado para su consulta. Máximo {REASON_MAX} caracteres.</small>}
    </label>}
  </FormDialog>;
}
