"use client";

import { useState, type FormEvent } from "react";
import { Dialog, Feedback, FormDialog } from "@/shared/ui";
import { message } from "@/core/api/client";
import type { Student } from "@/modules/students";
import type { Guardian } from "./types";
import { kinshipLabel, readRelationshipFields, RecordPicker, RelationshipFields } from "./relationship-fields";
import { createRelationship, GUARDIAN_RECORDS_PATH, STUDENT_RECORDS_PATH } from "./services/relationship-service";
import styles from "./academic-records.module.css";

export function RelationshipCreateDialog({ onClose, onSaved }: {
  onClose: () => void;
  onSaved: () => void;
}) {
  const [student, setStudent] = useState<Student | null>(null);
  const [guardian, setGuardian] = useState<Guardian | null>(null);
  const [fields, setFields] = useState<ReturnType<typeof readRelationshipFields> | null>(null);
  const [review, setReview] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const duplicate = !!student?.apoderados.some(item => item.apoderadoId === guardian?.id);

  function prepare(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!student || !guardian || duplicate) return;
    setFields(readRelationshipFields(new FormData(event.currentTarget)));
    setReview(true);
  }

  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (busy || !student || !guardian || !fields) return;
    setBusy(true);
    setError("");
    try {
      await createRelationship(student.id, { ...fields, apoderadoId: guardian.id });
      onSaved();
    } catch (failure) {
      setError(message(failure));
    } finally {
      setBusy(false);
    }
  }

  if (review && student && guardian && fields) {
    return <FormDialog
      title="Confirmar vínculo"
      description="Revisa los datos antes de registrar el parentesco."
      busy={busy}
      error={error}
      onClose={() => { setReview(false); setError(""); }}
      onSubmit={save}
      submitLabel="Confirmar vínculo"
    >
      <dl className={styles.relationshipSummary}>
        <div><dt>Estudiante</dt><dd>{student.persona.nombreCompleto}</dd></div>
        <div><dt>Apoderado</dt><dd>{guardian.persona.nombreCompleto}</dd></div>
        <div><dt>Parentesco</dt><dd>{kinshipLabel(fields.parentesco)}</dd></div>
        <div><dt>Apoderado principal</dt><dd>{fields.principal ? "Sí" : "No"}</dd></div>
      </dl>
      {fields.principal && student.apoderados.some(item => item.principal) &&
        <p className="hint">El apoderado seleccionado pasará a ser el principal.</p>}
    </FormDialog>;
  }
  return <Dialog title="Vincular apoderado" description="Busca las dos fichas y elige el parentesco." onClose={onClose}>
    <div className={styles.relationshipStack}>
      <RecordPicker title="Estudiante" searchLabel="Nombre, código o documento del estudiante"
        path={STUDENT_RECORDS_PATH} code={item => item.codigoAlumno} selection={student} onSelect={setStudent} />
      <RecordPicker title="Apoderado" searchLabel="Nombre, código o documento del apoderado"
        path={GUARDIAN_RECORDS_PATH} code={item => item.codigoApoderado} selection={guardian} onSelect={setGuardian} />
      {duplicate && <Feedback error="Este apoderado ya está vinculado al estudiante." />}
      <form onSubmit={prepare}>
        <fieldset className="form-grid">
          <RelationshipFields kinship={fields?.parentesco} principal={fields?.principal} />
        </fieldset>
        <div className="form-actions">
          <button type="button" onClick={onClose}>Cancelar</button>
          <button className="primary-action" disabled={!student || !guardian || duplicate}>Revisar vínculo</button>
        </div>
      </form>
    </div>
  </Dialog>;
}
