"use client";
import type { FamilyStudent } from "./types";
import styles from "./guardian-payments.module.css";

/** Texto que identifica a quién corresponde lo que se muestra: un hijo o el consolidado de la familia. */
export function scopeLabel(students: FamilyStudent[] | null, studentId: string) {
  const current = students?.find((student) => String(student.id) === studentId);
  return current ? current.nombreCompleto : "mis estudiantes";
}

/**
 * Selector de estudiante de la pantalla Pagos. «Todos los estudiantes» muestra el estado de cuenta
 * consolidado; elegir un hijo filtra Obligaciones, Historial y Seguimiento de pagos.
 */
export function StudentScope({ students, value, onChange }: {
  students: FamilyStudent[] | null;
  value: string;
  onChange: (studentId: string) => void;
}) {
  const current = students?.find((student) => String(student.id) === value) ?? null;
  const count = students?.length ?? 0;
  return (
    <section className={styles.scope} aria-label="Estudiante seleccionado">
      <label className={`field ${styles.scopeField}`}>
        Estudiante
        <select value={value} disabled={!count} onChange={(event) => onChange(event.target.value)}>
          <option value="">Todos los estudiantes</option>
          {students?.map((student) => (
            <option key={student.id} value={student.id}>{student.nombreCompleto}</option>
          ))}
        </select>
      </label>
      <div className={styles.scopeSummary} role="status" aria-live="polite">
        <span className={styles.scopeAvatar} aria-hidden="true">
          {current ? initials(current.nombreCompleto) : count || "–"}
        </span>
        {current ? (
          <p>
            <small>Mostrando la cuenta de</small>
            <strong>{current.nombreCompleto}</strong>
            <span>Código {current.codigoAlumno}</span>
          </p>
        ) : (
          <p>
            <small>Estado de cuenta consolidado</small>
            <strong>Todos los estudiantes</strong>
            <span>{count === 1 ? "1 estudiante asociado" : `${count} estudiantes asociados`}</span>
          </p>
        )}
      </div>
    </section>
  );
}

function initials(name: string) {
  return name.split(/\s+/).filter(Boolean).slice(0, 2).map((part) => part[0]?.toUpperCase()).join("");
}
