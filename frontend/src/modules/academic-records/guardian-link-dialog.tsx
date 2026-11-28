"use client";
import { useRef, useState, type FormEvent } from "react";
import { message } from "@/core/api/client";
import type { Student } from "@/modules/students";
import { Dialog, FormDialog } from "@/shared/ui";
import { GuardianLinkForm, useGuardianPicker } from "./guardian-link-form";
import { kinshipLabel } from "./relationship-fields";
import {
  linkGuardian,
  type GuardianLinkValues,
} from "./services/guardian-link-service";
import { StudentPicker } from "./student-picker";
import type { Guardian } from "./types";
import styles from "./academic-records.module.css";

/** Se monta una vez por estudiante para no arrastrar la elección hecha con otro. */
function GuardianChoice({
  student,
  busy,
  onSave,
}: {
  student: Student;
  busy: boolean;
  onSave: (values: GuardianLinkValues, guardian: Guardian) => void;
}) {
  const picker = useGuardianPicker();
  return (
    <div className="action-card">
      <h4>Apoderado</h4>
      <GuardianLinkForm
        picker={picker}
        linked={student.apoderados}
        busy={busy}
        submitLabel="Revisar vínculo"
        onSave={(values) => {
          if (picker.choice) onSave(values, picker.choice);
        }}
      />
    </div>
  );
}

/** Vincula desde Parentescos: primero el estudiante y después su apoderado. */
export function GuardianLinkDialog({
  onClose,
  onLinked,
}: {
  onClose: () => void;
  onLinked: (student: Student) => void;
}) {
  const [student, setStudent] = useState<Student | null>(null);
  const [review, setReview] = useState<{ values: GuardianLinkValues; guardian: Guardian } | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const sending = useRef(false);
  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!student || !review || sending.current) return;
    sending.current = true;
    setBusy(true);
    setError("");
    try {
      await linkGuardian(student.id, review.values);
      onLinked(student);
    } catch (e) {
      setError(message(e));
      sending.current = false;
      setBusy(false);
    }
  }
  return <>
    <Dialog title="Vincular apoderado" size="lg" busy={busy} onClose={onClose} active={!review}>
      {/* Mientras se envía, no se puede cambiar lo ya enviado. */}
      <fieldset className={styles.plainFieldset} disabled={busy}>
        <StudentPicker
          selected={student}
          onSelect={(value) => {
            setStudent(value);
            setError("");
          }}
        />
        {student && (
          <GuardianChoice
            key={student.id}
            student={student}
            busy={busy}
            onSave={(values, guardian) => {
              setError("");
              setReview({ values, guardian });
            }}
          />
        )}
      </fieldset>
      <div className="form-actions">
        <button type="button" disabled={busy} onClick={onClose}>
          Cancelar
        </button>
      </div>
    </Dialog>
    {student && review && <FormDialog
      title="Confirmar vínculo"
      description="Revisa los datos antes de registrar el parentesco."
      busy={busy}
      error={error}
      onClose={() => setReview(null)}
      onSubmit={save}
      submitLabel="Confirmar vínculo"
    >
      <dl className={styles.relationshipSummary}>
        <div><dt>Estudiante</dt><dd>{student.persona.nombreCompleto}</dd></div>
        <div><dt>Apoderado</dt><dd>{review.guardian.persona.nombreCompleto}</dd></div>
        <div><dt>Parentesco</dt><dd>{kinshipLabel(review.values.parentesco)}</dd></div>
        <div><dt>Apoderado principal</dt><dd>{review.values.principal ? "Sí" : "No"}</dd></div>
      </dl>
      {review.values.principal && student.apoderados.some(item => item.principal) &&
        <p className="hint">El apoderado seleccionado pasará a ser el principal.</p>}
    </FormDialog>}
  </>;
}
