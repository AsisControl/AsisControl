"use client";
import type { InputHTMLAttributes } from "react";
import { Field } from "@/shared/ui";
import styles from "./students.module.css";

export function StudentField({
  label,
  error,
  hint,
  ...props
}: InputHTMLAttributes<HTMLInputElement> & {
  label: string;
  error?: string;
  hint?: string;
}) {
  const errorId = `student-${props.name}-error`;
  const hintId = `student-${props.name}-hint`;
  return (
    <div>
      <Field
        {...props}
        label={label}
        aria-invalid={!!error}
        aria-describedby={
          [error ? errorId : "", hint ? hintId : ""]
            .filter(Boolean)
            .join(" ") || undefined
        }
        className={error ? styles.invalid : undefined}
      />
      {hint && (
        <small className={styles.helper} id={hintId}>
          {hint}
        </small>
      )}
      {error && (
        <small className={styles.fieldError} id={errorId}>
          {error}
        </small>
      )}
    </div>
  );
}
