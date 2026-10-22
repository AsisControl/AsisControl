"use client";
import {
  useEffect,
  useRef,
  useState,
  type ClipboardEvent,
  type FocusEvent,
  type FormEvent,
} from "react";
import { saveStudent } from "./services/student-service";
import {
  homonyms,
  studentErrorMessage,
  studentFieldErrors,
} from "./student-errors";
import {
  hasSensitiveChanges,
  sanitizeStudentField,
  studentDraft,
  studentInput,
  validateStudent,
} from "./student-rules";
import type { Homonym, Student, StudentDraft, StudentErrors } from "./types";

/** Coordina edición, validación y guardado sin mover el foco durante blur o escritura. */
export function useStudentForm(
  student: Student | undefined,
  onSaved: (student: Student) => void,
) {
  const [initial] = useState(() => studentDraft(student));
  const [draft, setDraft] = useState<StudentDraft>(initial);
  const [errors, setErrors] = useState<StudentErrors>({});
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const [matches, setMatches] = useState<Homonym[] | null>(null);
  const [confirmed, setConfirmed] = useState(false);
  const submitting = useRef(false);
  const mounted = useRef(true);
  const formRef = useRef<HTMLFormElement>(null);
  const focusAfterSubmit = useRef(false);
  useEffect(() => {
    mounted.current = true;
    return () => {
      mounted.current = false;
    };
  }, []);
  const sensitive = hasSensitiveChanges(draft, student);
  const dirty = JSON.stringify(draft) !== JSON.stringify(initial);

  useEffect(() => {
    if (busy || !focusAfterSubmit.current) return;
    focusAfterSubmit.current = false;
    formRef.current
      ?.querySelector<HTMLElement>('[aria-invalid="true"]')
      ?.focus();
  }, [errors, busy]);

  function changeField(fieldName: keyof StudentDraft, value: string) {
    value = sanitizeStudentField(fieldName, value, draft.tipoDocumento);
    setDraft((previous) => {
      const nextDraft = { ...previous, [fieldName]: value };
      if (fieldName === "tipoDocumento")
        nextDraft.numeroDocumento = sanitizeStudentField(
          "numeroDocumento",
          previous.numeroDocumento,
          value,
        );
      if (fieldName !== "motivo" && !hasSensitiveChanges(nextDraft, student))
        nextDraft.motivo = "";
      return nextDraft;
    });
    setErrors((previous) => ({ ...previous, [fieldName]: undefined }));
    setError("");
    setMatches(null);
    setConfirmed(false);
  }
  function readCurrentDraft(form: HTMLFormElement): StudentDraft {
    // FormData captura también autofill que no haya emitido onChange.
    const values = new FormData(form);
    const currentValues = { ...draft };
    for (const fieldName of Object.keys(
      currentValues,
    ) as (keyof StudentDraft)[])
      if (values.has(fieldName))
        currentValues[fieldName] = String(values.get(fieldName) ?? "");
    if (!hasSensitiveChanges(currentValues, student)) currentValues.motivo = "";
    return currentValues;
  }
  function validateFieldOnBlur(
    fieldName: keyof StudentDraft,
    form: HTMLFormElement | null,
  ) {
    if (!form) return;
    const currentValues = readCurrentDraft(form);
    if (JSON.stringify(currentValues) !== JSON.stringify(draft)) setError("");
    setDraft(currentValues);
    const validationErrors = validateStudent(currentValues, student);
    // Blur solo valida el campo abandonado y nunca cambia el foco.
    setErrors((previous) => ({
      ...previous,
      [fieldName]: validationErrors[fieldName],
    }));
  }
  async function submitForm(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (submitting.current) return;
    const submittedDraft = readCurrentDraft(event.currentTarget);
    const snapshotChanged =
      JSON.stringify(submittedDraft) !== JSON.stringify(draft);
    const confirmedForSubmission = confirmed && !snapshotChanged;
    if (snapshotChanged) {
      setMatches(null);
      setConfirmed(false);
    }
    setDraft(submittedDraft);
    const validationErrors = validateStudent(submittedDraft, student);
    setErrors(validationErrors);
    setError("");
    if (Object.keys(validationErrors).length) {
      focusAfterSubmit.current = true;
      setError("Revisa los campos indicados antes de guardar la ficha.");
      return;
    }
    if (
      !snapshotChanged &&
      matches &&
      (!matches.length || !confirmedForSubmission)
    ) {
      setError(
        "Revisa las coincidencias y confirma que es otra persona antes de guardar.",
      );
      return;
    }
    submitting.current = true;
    setBusy(true);
    try {
      const savedStudent = await saveStudent(
        studentInput(submittedDraft, student, confirmedForSubmission),
        student?.id,
      );
      if (mounted.current) onSaved(savedStudent);
    } catch (cause) {
      if (!mounted.current) return;
      const fields = studentFieldErrors(cause);
      focusAfterSubmit.current = true;
      setErrors(fields);
      setError(studentErrorMessage(cause));
      setMatches(homonyms(cause));
      setConfirmed(false);
    } finally {
      submitting.current = false;
      if (mounted.current) setBusy(false);
    }
  }
  const bindField = (fieldName: keyof StudentDraft) => ({
    name: fieldName,
    value: draft[fieldName],
    onChange: (event: { target: { value: string } }) =>
      changeField(fieldName, event.target.value),
    onBlur: (event: FocusEvent<HTMLInputElement>) =>
      validateFieldOnBlur(fieldName, event.currentTarget.form),
    onPaste: (event: ClipboardEvent<HTMLInputElement>) => {
      if (
        ![
          "nombres",
          "apellidoPaterno",
          "apellidoMaterno",
          "telefono",
          "numeroDocumento",
        ].includes(fieldName)
      )
        return;
      // Filtra ANTES del maxlength nativo para no perder dígitos válidos del pegado.
      event.preventDefault();
      const input = event.currentTarget;
      const start = input.selectionStart ?? input.value.length;
      const end = input.selectionEnd ?? start;
      changeField(
        fieldName,
        input.value.slice(0, start) +
          event.clipboardData.getData("text") +
          input.value.slice(end),
      );
    },
    error: errors[fieldName],
  });

  return {
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
  };
}

export type StudentFormState = ReturnType<typeof useStudentForm>;
