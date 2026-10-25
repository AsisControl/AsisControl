"use client";
import { useState } from "react";
import { Dialog, Feedback, Field } from "@/shared/ui";
import {
  StudentContactFields,
  StudentCorrectionReason,
  StudentHomonymReview,
  StudentIdentityFields,
} from "./student-form-fields";
import { useStudentForm } from "./use-student-form";
import type { Student } from "./types";
import styles from "./students.module.css";

export function StudentForm({
  student,
  onSaved,
  onCancel,
}: {
  student?: Student;
  onSaved: (student: Student) => void;
  onCancel: () => void;
}) {
  const {
    draft,
    errors,
    error,
    busy,
    matches,
    confirmed,
    setConfirmed,
    sensitive,
    dirty,
    formRef,
    changeField,
    validateFieldOnBlur,
    bindField,
    submitForm,
  } = useStudentForm(student, onSaved);
  const [discard, setDiscard] = useState(false);

  return (
    <>
      <form
        ref={formRef}
        className="panel"
        noValidate
        onSubmit={submitForm}
        aria-busy={busy}
      >
        <h3>
          {student
            ? "Editar ficha de estudiante"
            : "Registrar ficha de estudiante"}
        </h3>
        <p className={styles.summary}>
          Los campos con * son obligatorios. La ficha conserva la identidad del
          estudiante a lo largo de los años académicos.
        </p>
        <Feedback error={error} />
        <fieldset disabled={busy}>
          {student && (
            <Field
              label="Código institucional"
              value={student.codigoAlumno}
              readOnly
              aria-describedby="student-code-hint"
            />
          )}
          <p className={styles.notice} id="student-code-hint">
            {student
              ? "El código institucional es permanente y se conserva al actualizar la ficha."
              : "El código institucional se asigna automáticamente al guardar la ficha."}
          </p>
          <StudentIdentityFields
            draft={draft}
            errors={errors}
            bindField={bindField}
            changeField={changeField}
            validateFieldOnBlur={validateFieldOnBlur}
          />
          <StudentContactFields bindField={bindField} />
          {sensitive && (
            <StudentCorrectionReason
              draft={draft}
              errors={errors}
              changeField={changeField}
              validateFieldOnBlur={validateFieldOnBlur}
            />
          )}
          {matches !== null && (
            <StudentHomonymReview
              matches={matches}
              confirmed={confirmed}
              onConfirm={setConfirmed}
            />
          )}
        </fieldset>
        <p className={styles.helper}>
          Guardar esta ficha no crea credenciales, una matrícula ni una
          asociación familiar.
        </p>
        <div className="form-actions">
          <button
            type="button"
            disabled={busy}
            onClick={() => (dirty ? setDiscard(true) : onCancel())}
          >
            Cancelar
          </button>
          <button
            className="primary-action"
            disabled={
              busy || (matches !== null && (!confirmed || !matches.length))
            }
          >
            {busy ? "Guardando…" : "Guardar ficha"}
          </button>
        </div>
      </form>
      {discard && (
        <Dialog
          title="¿Descartar los cambios de la ficha?"
          onClose={() => setDiscard(false)}
        >
          <p>Los cambios que todavía no guardaste se perderán.</p>
          <div className="form-actions">
            <button type="button" onClick={() => setDiscard(false)}>
              Seguir editando
            </button>
            <button type="button" onClick={onCancel}>
              Descartar cambios
            </button>
          </div>
        </Dialog>
      )}
    </>
  );
}
