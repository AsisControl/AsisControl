"use client";
import { useRef, useState, type FormEvent } from "react";
import {
  Dialog,
  Feedback,
  PersonSections,
  RecordHeader,
  RecordItem,
  RecordSection,
} from "@/shared/ui";
import { dateLabel, stateLabel } from "@/shared/lib/formatters";
import { canPerform, changeStudentState } from "./services/student-service";
import { studentErrorMessage } from "./student-errors";
import { reasonError, studentName } from "./student-rules";
import type { Student, StudentAction } from "./types";
import styles from "./students.module.css";

const STUDENT_ACTION_LABELS: Record<StudentAction, string> = {
  inactivar: "Inactivar ficha",
  reactivar: "Reactivar ficha",
  eliminar: "Eliminar ficha",
};

export function StudentDetail({
  student,
  onClose,
  onEdit,
  onChanged,
}: {
  student: Student;
  onClose: () => void;
  onEdit: () => void;
  onChanged: (student: Student | null) => void;
}) {
  const [action, setAction] = useState<StudentAction | null>(null);
  // La confirmación reemplaza a la ficha mientras está abierta: nunca hay dos ventanas apiladas.
  if (action)
    return (
      <StateDialog
        key={action}
        student={student}
        action={action}
        onClose={() => setAction(null)}
        onSaved={(updated) => {
          setAction(null);
          onChanged(updated);
        }}
      />
    );
  return (
    <Dialog title="Ficha del estudiante" size="lg" onClose={onClose}>
      <RecordHeader name={studentName(student)} active={student.activo} />
      <RecordSection title="Datos del estudiante" />
      <dl className="detail-grid">
        <RecordItem label="Código">{student.codigoAlumno}</RecordItem>
        <RecordItem label="Fecha de registro">
          {dateLabel(student.fechaCreacion ?? student.persona.fechaCreacion ?? null)}
        </RecordItem>
        <RecordItem label="Última actualización">
          {dateLabel(
            student.fechaActualizacion ??
              student.persona.fechaActualizacion ??
              null,
          )}
        </RecordItem>
      </dl>
      <PersonSections persona={student.persona} />
      <RecordSection title="Apoderados" />
      {student.apoderados?.length ? (
        <div className="user-table-wrap">
          <table className="user-table" aria-label="Apoderados">
            <thead>
              <tr>
                <th scope="col">Apoderado</th>
                <th scope="col">Parentesco</th>
                <th scope="col">Principal</th>
              </tr>
            </thead>
            <tbody>
              {student.apoderados.map((link) => (
                <tr key={link.id}>
                  <td data-label="Apoderado">{link.nombreCompleto}</td>
                  <td data-label="Parentesco">
                    {link.parentesco ? stateLabel(link.parentesco) : "—"}
                  </td>
                  <td data-label="Principal">{link.principal ? "Sí" : "No"}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : (
        <p className="empty">No tiene apoderados vinculados.</p>
      )}
      <p className={styles.helper}>
        El acceso al sistema se gestiona en Administración → Usuarios → Crear
        acceso. Las matrículas y los vínculos familiares se gestionan por
        separado.
      </p>
      <div className="form-actions">
        <button type="button" onClick={onClose}>
          Cerrar
        </button>
        {(["inactivar", "reactivar", "eliminar"] as const)
          .filter((item) => canPerform(student, item))
          .map((item) => (
            <button
              key={item}
              type="button"
              className={item === "eliminar" ? styles.danger : undefined}
              onClick={() => setAction(item)}
            >
              {STUDENT_ACTION_LABELS[item]}
            </button>
          ))}
        {student.activo && student.accionesPermitidas?.editar !== false && (
          <button type="button" className="primary-action" onClick={onEdit}>
            Editar ficha
          </button>
        )}
      </div>
    </Dialog>
  );
}

function StateDialog({
  student,
  action,
  onClose,
  onSaved,
}: {
  student: Student;
  action: StudentAction;
  onClose: () => void;
  onSaved: (student: Student | null) => void;
}) {
  const [reason, setReason] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const submitting = useRef(false);
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (submitting.current) return;
    const invalid = reasonError(reason);
    if (invalid) {
      setError(invalid);
      return;
    }
    submitting.current = true;
    setBusy(true);
    setError("");
    try {
      onSaved(await changeStudentState(student, action, reason));
    } catch (cause) {
      setError(studentErrorMessage(cause));
    } finally {
      submitting.current = false;
      setBusy(false);
    }
  }
  return (
    <Dialog title={STUDENT_ACTION_LABELS[action]} onClose={onClose} busy={busy}>
      <form noValidate onSubmit={submit} aria-busy={busy}>
        <Feedback error={error} />
        <p>
          {student.codigoAlumno} · {studentName(student)}
        </p>
        <p className={styles.summary}>
          {action === "eliminar"
            ? "La eliminación es definitiva y solo procede si la ficha no tiene " +
              "relaciones ni historial de uso. La acción quedará auditada."
            : action === "inactivar"
              ? "La ficha conservará su historial y dejará de admitir nuevos usos. " +
                "La matrícula vigente impide esta operación. " +
                "Solo se afecta el acceso de estudiante."
              : "La ficha volverá a estar habilitada. El servidor verificará " +
                "las condiciones de reactivación y registrará el motivo."}
        </p>
        <label className="field">
          Motivo *
          <textarea
            name="motivo"
            className={styles.reason}
            value={reason}
            disabled={busy}
            onChange={(event) => setReason(event.target.value)}
            minLength={10}
            maxLength={1000}
            rows={3}
            required
            aria-describedby="student-state-reason-help"
          />
        </label>
        <small className={styles.helper} id="student-state-reason-help">
          De 10 a 1000 caracteres.
        </small>
        <div className="form-actions">
          <button type="button" disabled={busy} onClick={onClose}>
            Cancelar
          </button>
          <button className="primary-action" disabled={busy}>
            {busy ? "Procesando…" : STUDENT_ACTION_LABELS[action]}
          </button>
        </div>
      </form>
    </Dialog>
  );
}
