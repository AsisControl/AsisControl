"use client";
import { useRef, useState, type FormEvent } from "react";
import { fieldErrorsOf, message } from "@/core/api/client";
import { FormDialog } from "@/shared/ui";
import {
  CLASSROOM_REASON_MAX,
  validateClassroomReason,
} from "../lib/validation";
import type { Classroom } from "../types";
import { habilitarAula, inhabilitarAula } from "./services/aulaService";
/** Inhabilita un aula disponible (motivo obligatorio) o habilita una inhabilitada (motivo opcional). */
export function ClassroomStateDialog({
  classroom,
  onClose,
  onSaved,
}: {
  classroom: Classroom;
  onClose: () => void;
  onSaved: (classroom: Classroom) => void;
}) {
  const disabling = classroom.activo;
  const [reason, setReason] = useState("");
  const [reasonError, setReasonError] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const sending = useRef(false);
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (sending.current) return;
    const found = validateClassroomReason(reason, disabling);
    setReasonError(found);
    if (found) return;
    const motivo = reason.trim();
    sending.current = true;
    setBusy(true);
    setError("");
    try {
      onSaved(
        await (disabling
          ? inhabilitarAula(classroom, motivo)
          : habilitarAula(classroom, motivo || null)),
      );
    } catch (e) {
      setError(message(e));
      setReasonError(fieldErrorsOf(e).motivo ?? "");
      sending.current = false;
      setBusy(false);
    }
  }
  return (
    <FormDialog
      title={disabling ? "Inhabilitar aula" : "Habilitar aula"}
      description={`${classroom.nombre} · ${classroom.codigo}`}
      submitLabel={disabling ? "Inhabilitar aula" : "Habilitar aula"}
      danger={disabling}
      busy={busy}
      error={error}
      onClose={onClose}
      onSubmit={submit}
      noValidate
    >
      <p className="full-field hint">
        {disabling
          ? `${classroom.nombre} dejará de ofrecerse para nuevos bloques de horario. No se puede inhabilitar si todavía tiene horarios activos.`
          : `${classroom.nombre} volverá a estar disponible para los bloques de horario.`}
      </p>
      <label className="field full-field">
        Motivo
        {disabling && <span className="field-required"> *</span>}
        <textarea
          name="motivo"
          rows={3}
          maxLength={CLASSROOM_REASON_MAX}
          required={disabling}
          placeholder={
            disabling ? "Ej. Mantenimiento del proyector" : "Opcional"
          }
          value={reason}
          disabled={busy}
          aria-invalid={reasonError ? true : undefined}
          onChange={(e) => {
            setReason(e.target.value);
            setReasonError("");
          }}
        />
        {reasonError ? (
          <small className="field-error" role="alert">
            {reasonError}
          </small>
        ) : (
          <small className="field-hint">
            Se registra en la auditoría. Máximo {CLASSROOM_REASON_MAX}{" "}
            caracteres.
          </small>
        )}
      </label>
    </FormDialog>
  );
}
