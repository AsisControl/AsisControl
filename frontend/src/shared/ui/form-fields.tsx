"use client";
import { useState, type InputHTMLAttributes } from "react";
import { Eye } from "./eye-icon";
export function Field({
  label,
  ...props
}: InputHTMLAttributes<HTMLInputElement> & { label: string }) {
  return (
    <label className="field">
      {label}
      {props.required && " *"}
      <input {...props} />
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
      {props.required && " *"}
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
      {label} *
      <textarea name="motivo" required maxLength={700} rows={3} />
    </label>
  );
}
