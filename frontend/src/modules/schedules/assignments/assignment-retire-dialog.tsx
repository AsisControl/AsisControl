"use client";
import { useRef, useState, type FormEvent } from "react";
import { ApiError, fieldErrorsOf, message } from "@/core/api/client";
import { FormDialog } from "@/shared/ui";
import { countLabel } from "@/shared/lib/formatters";
import { scheduleSlotLabel } from "../lib/labels";
import type { BlockingSchedule, TeacherAssignment } from "../types";
import { sectionLabel } from "./assignment-form-dialog";
import { retirarAsignacion } from "./services/asignacionService";
export const RETIRE_REASON_MAX = 500;
const BLOCKED_CODE = "ASIGNACION_CON_HORARIOS_FUTUROS";
/**
 * Retira al docente del curso con un motivo obligatorio (US-58). Si todavía tiene bloques
 * de horario, el backend lo rechaza y aquí se listan para reasignarlos o eliminarlos.
 */
export function AssignmentRetireDialog({
  assignment,
  onClose,
  onSaved,
}: {
  assignment: TeacherAssignment;
  onClose: () => void;
  onSaved: (assignment: TeacherAssignment) => void;
}) {
  const [reason, setReason] = useState("");
  const [reasonError, setReasonError] = useState("");
  const [blocking, setBlocking] = useState<BlockingSchedule[]>([]);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const sending = useRef(false);
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (sending.current) return;
    const motivo = reason.trim();
    if (!motivo) {
      setReasonError("Indique el motivo del retiro.");
      return;
    }
    sending.current = true;
    setBusy(true);
    setError("");
    setBlocking([]);
    try {
      onSaved(await retirarAsignacion(assignment, motivo));
    } catch (e) {
      setError(message(e));
      setReasonError(fieldErrorsOf(e).motivo ?? "");
      if (e instanceof ApiError && e.code === BLOCKED_CODE && Array.isArray(e.details))
        setBlocking(e.details as BlockingSchedule[]);
      sending.current = false;
      setBusy(false);
    }
  }
  return (
    <FormDialog
      title="Retirar docente del curso"
      description={`${assignment.docente} · ${assignment.curso} · ${sectionLabel(assignment.grado, assignment.seccion)} · ${assignment.anioAcademico}`}
      submitLabel="Retirar docente"
      danger
      busy={busy}
      error={error}
      onClose={onClose}
      onSubmit={submit}
      noValidate
    >
      <p className="full-field hint">
        {assignment.horariosActivos > 0
          ? `El docente tiene ${countLabel(assignment.horariosActivos, "bloque", "bloques")} de horario en este curso. Para retirarlo, primero reasígnelos o elimínelos en la matriz de horarios.`
          : "El docente dejará de operar en este curso y sección. Podrá consultar su historial, pero no registrar asistencia ni notas."}
      </p>
      {blocking.length > 0 && (
        <div className="full-field" role="region" aria-label="Bloques de horario pendientes">
          <p className="hint">Bloques que debe reasignar o eliminar:</p>
          <ul>
            {blocking.map((item) => (
              <li key={item.horarioId}>
                {scheduleSlotLabel(item)} · {item.aula}
              </li>
            ))}
          </ul>
        </div>
      )}
      <label className="field full-field">
        Motivo
        <span className="field-required"> *</span>
        <textarea
          name="motivo"
          rows={3}
          maxLength={RETIRE_REASON_MAX}
          required
          placeholder="Ej. Reasignación de carga horaria"
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
            Se registra en la auditoría. Máximo {RETIRE_REASON_MAX} caracteres.
          </small>
        )}
      </label>
    </FormDialog>
  );
}
