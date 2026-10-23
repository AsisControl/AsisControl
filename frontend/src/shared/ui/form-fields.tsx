"use client";
import { useState, type InputHTMLAttributes, type ReactNode } from "react";
import { Eye } from "./eye-icon";
/** Asterisco de los campos obligatorios; el texto " *" se conserva para lectores de pantalla. */
const RequiredMark = () => <span className="field-required"> *</span>;
const FieldHint = ({ children }: { children?: ReactNode }) =>
  children ? <small className="field-hint">{children}</small> : null;
export function Field({
  label,
  hint,
  fullWidth = false,
  ...props
}: InputHTMLAttributes<HTMLInputElement> & {
  label: string;
  /** Ayuda breve bajo el campo (formato, reglas del backend). */
  hint?: ReactNode;
  /** Ocupa las dos columnas de .form-grid. */
  fullWidth?: boolean;
}) {
  return (
    <label className={fullWidth ? "field full-field" : "field"}>
      {label}
      {props.required && <RequiredMark />}
      <input {...props} />
      <FieldHint>{hint}</FieldHint>
    </label>
  );
}
export function PasswordField({
  label,
  ...props
}: InputHTMLAttributes<HTMLInputElement> & { label: string }) {
  const [visible, setVisible] = useState(false);
  return (
    <label className="field">
      {label}
      {props.required && <RequiredMark />}
      <span className="password-field">
        <input {...props} type={visible ? "text" : "password"} />
        <button
          type="button"
          aria-label={visible ? "Ocultar contraseña" : "Mostrar contraseña"}
          aria-pressed={visible}
          onClick={() => setVisible((v) => !v)}
        >
          <Eye crossed={visible} />
        </button>
      </span>
    </label>
  );
}
export function ReasonField({ label = "Motivo" }: { label?: string }) {
  return (
    <label className="field full-field">
      {label}
      <RequiredMark />
      <textarea name="motivo" required maxLength={700} rows={3} />
      <FieldHint>Se registra junto con el cambio. Máximo 700 caracteres.</FieldHint>
    </label>
  );
}
