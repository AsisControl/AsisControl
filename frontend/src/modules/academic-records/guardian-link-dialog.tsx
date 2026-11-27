"use client";
import { useRef, useState } from "react";
import { message } from "@/core/api/client";
import type { Student } from "@/modules/students";
import { Dialog, Feedback } from "@/shared/ui";
import { GuardianLinkForm, useGuardianPicker } from "./guardian-link-form";
import {
  linkGuardian,
  type GuardianLinkValues,
} from "./services/guardian-link-service";
import { StudentPicker } from "./student-picker";
import styles from "./academic-records.module.css";

/** Se monta una vez por estudiante para no arrastrar la elección hecha con otro. */
function GuardianChoice({
  student,
  busy,
  onSave,
}: {
  student: Student;
  busy: boolean;
  onSave: (values: GuardianLinkValues) => void;
}) {
  const picker = useGuardianPicker();
  return (
    <div className="action-card">
      <h4>Apoderado</h4>
      <GuardianLinkForm
        picker={picker}
        linked={student.apoderados}
        busy={busy}
        onSave={onSave}
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
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const sending = useRef(false);
  async function save(target: Student, values: GuardianLinkValues) {
    if (sending.current) return;
    sending.current = true;
    setBusy(true);
    setError("");
    try {
      await linkGuardian(target.id, values);
      onLinked(target);
    } catch (e) {
      setError(message(e));
      sending.current = false;
      setBusy(false);
    }
  }
  return (
    <Dialog title="Vincular apoderado" size="lg" busy={busy} onClose={onClose}>
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
            onSave={(values) => void save(student, values)}
          />
        )}
      </fieldset>
      <Feedback error={error} />
      <div className="form-actions">
        <button type="button" disabled={busy} onClick={onClose}>
          Cancelar
        </button>
      </div>
    </Dialog>
  );
}
