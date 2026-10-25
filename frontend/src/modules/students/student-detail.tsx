"use client";
import { useRef, useState, type FormEvent } from "react";
import { Dialog, Feedback } from "@/shared/ui";
import { dateLabel } from "@/shared/lib/formatters";
import { canPerform, changeStudentState } from "./services/student-service";
import { studentErrorMessage } from "./student-errors";
import { birthLabel, reasonError, studentName } from "./student-rules";
import type { Student, StudentAction } from "./types";
import styles from "./students.module.css";

const STUDENT_ACTION_LABELS: Record<StudentAction, string> = {
  inactivar: "Inactivar ficha",
  reactivar: "Reactivar ficha",
  eliminar: "Eliminar ficha",
};

export function StudentDetail({
  student,
  onBack,
  onEdit,
  onChanged,
}: {
  student: Student;
  onBack: () => void;
  onEdit: () => void;
  onChanged: (student: Student | null) => void;
}) {
  const [action, setAction] = useState<StudentAction | null>(null);
  const persona = student.persona;
  const documentTypeLabel =
    persona.tipoDocumento === "CARNET_EXTRANJERIA"
      ? "Carné de extranjería"
      : persona.tipoDocumento;
  const documentLabel =
    persona.tipoDocumento && persona.numeroDocumento
      ? `${documentTypeLabel}: ${persona.numeroDocumento}`
      : "Documento pendiente";
  return (
    <article className="panel">
      <div className={styles.actions}>
        <button type="button" onClick={onBack}>
          Volver a fichas
        </button>
        {student.activo && student.accionesPermitidas?.editar !== false && (
          <button type="button" onClick={onEdit}>
            Editar ficha
          </button>
        )}
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
      </div>
      <div className={styles.detailHeading}>
        <h3>{studentName(student)}</h3>
        <span
          className={`${styles.badge} ${student.activo ? "" : styles.inactive}`}
        >
          {student.activo ? "Habilitada" : "Inactiva"}
        </span>
      </div>
      <dl className="detail-grid">
        <div>
          <dt>Código institucional</dt>
          <dd>{student.codigoAlumno}</dd>
        </div>
        <div>
          <dt>Documento</dt>
          <dd>{documentLabel}</dd>
        </div>
        <div>
          <dt>Fecha de nacimiento</dt>
          <dd>{birthLabel(persona.fechaNacimiento)}</dd>
        </div>
        <div>
          <dt>Correo de contacto</dt>
          <dd>{persona.correo || "Sin registrar"}</dd>
        </div>
        <div>
          <dt>Teléfono</dt>
          <dd>{persona.telefono || "Sin registrar"}</dd>
        </div>
        <div>
          <dt>Dirección</dt>
          <dd>{persona.direccion || "Sin registrar"}</dd>
        </div>
        <div>
          <dt>Fecha de creación</dt>
          <dd>
            {dateLabel(student.fechaCreacion ?? persona.fechaCreacion ?? null)}
          </dd>
        </div>
        <div>
          <dt>Última actualización</dt>
          <dd>
            {dateLabel(
              student.fechaActualizacion ?? persona.fechaActualizacion ?? null,
            )}
          </dd>
        </div>
      </dl>
      <h4>Apoderados asociados</h4>
      {student.apoderados?.length ? (
        <ul>
          {student.apoderados.map((guardian) => (
            <li key={guardian.id}>
              {guardian.nombreCompleto}
              {guardian.principal ? " · Principal" : ""}
            </li>
          ))}
        </ul>
      ) : (
        <p className="hint">No tiene apoderados asociados.</p>
      )}
      <p className={styles.helper}>
        El acceso al sistema se gestiona en Administración → Usuarios → Crear
        acceso. Las matrículas y los vínculos familiares se gestionan por
        separado.
      </p>
      {action && (
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
      )}
    </article>
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
