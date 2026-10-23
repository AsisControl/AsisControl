"use client";
import { useState, type FormEvent } from "react";
import { message } from "@/core/api/client";
import { passwordError } from "@/core/auth/password-policy";
import { Dialog, Feedback, PasswordField } from "@/shared/ui";
import { roleLabel } from "@/shared/lib/formatters";
import type { Account } from "../types";
import { restablecerContrasena } from "./services/usuarioService";
/** Asigna una contraseña temporal tras verificar la identidad del titular. */
export function ResetPasswordDialog({
  account,
  onClose,
  onSaved,
}: {
  account: Account;
  onClose: () => void;
  onSaved: () => void;
}) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  async function save(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const form = new FormData(e.currentTarget);
    const password = String(form.get("password") || "");
    const policy = passwordError(password, account.username);
    if (policy) {
      setError(policy);
      return;
    }
    setBusy(true);
    setError("");
    try {
      await restablecerContrasena(account.id, {
        temporaryPassword: password,
        identidadVerificada: form.get("identidad") === "on",
        motivo: String(form.get("motivo")),
      });
      onSaved();
    } catch (e) {
      setError(message(e));
      setBusy(false);
    }
  }
  return (
    <Dialog title="Restablecer contraseña" onClose={onClose} busy={busy}>
      <form onSubmit={save}>
        <p>
          {account.nombreCompleto} · {roleLabel(account.rol)}
        </p>
        <Feedback error={error} />
        <label className="check-field">
          <input type="checkbox" name="identidad" required /> Verifiqué la
          identidad del titular antes de restablecer su acceso.
        </label>
        <label className="field">
          Motivo *
          <textarea name="motivo" required maxLength={700} rows={3} />
        </label>
        <PasswordField
          label="Contraseña temporal"
          name="password"
          required
          minLength={8}
          maxLength={64}
          autoComplete="new-password"
        />
        <p className="hint">
          Vence en 24 horas y requiere cambio al ingresar. Entrega esta
          contraseña al titular una sola vez. Sus sesiones anteriores serán
          revocadas.
        </p>
        <div className="form-actions">
          <button type="button" disabled={busy} onClick={onClose}>
            Cancelar
          </button>
          <button className="primary-action" disabled={busy}>
            {busy ? "Guardando…" : "Restablecer"}
          </button>
        </div>
      </form>
    </Dialog>
  );
}
