"use client";
import { useState, type FormEvent } from "react";
import { message } from "@/core/api/client";
import { passwordError } from "@/core/auth/password-policy";
import { Dialog, Feedback, Field, PasswordField } from "@/shared/ui";
import { roleLabel } from "@/shared/lib/formatters";
import type { Account } from "../types";
import { actualizarUsuario } from "./services/usuarioService";
import { stateLabels } from "./user-format";
const REACTIVABLE_STATES = ["DESACTIVADO", "PENDIENTE"];
/** Edición de correo y estado. Reactivar exige una nueva contraseña temporal. */
export function EditAccessDialog({
  account,
  isCurrentUser,
  onClose,
  onSaved,
}: {
  account: Account;
  isCurrentUser: boolean;
  onClose: () => void;
  onSaved: () => void;
}) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  async function save(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const form = new FormData(e.currentTarget);
    const password = String(form.get("password") || "");
    const activating =
      REACTIVABLE_STATES.includes(account.estado) &&
      form.get("estado") === "ACTIVO";
    if (activating) {
      const policy = passwordError(password, account.username);
      if (policy) {
        setError(policy);
        return;
      }
    }
    setBusy(true);
    setError("");
    try {
      await actualizarUsuario(account, {
        correo: String(form.get("correo")),
        estado: String(form.get("estado")),
        correoVerificado: form.get("correoVerificado") === "on",
        temporaryPassword: activating ? password : null,
        motivo: String(form.get("motivo")),
      });
      onSaved();
    } catch (e) {
      setError(message(e));
      setBusy(false);
    }
  }
  return (
    <Dialog title="Editar acceso" onClose={onClose} busy={busy}>
      <form onSubmit={save}>
        <p>
          {account.nombreCompleto} · {roleLabel(account.rol)}
        </p>
        <Feedback error={error} />
        <Field
          label="Correo del acceso"
          name="correo"
          type="email"
          required
          defaultValue={account.correo}
          onChange={(event) => {
            const confirmation = event.currentTarget.form?.elements.namedItem(
              "correoVerificado",
            ) as HTMLInputElement | null;
            if (confirmation) confirmation.checked = false;
          }}
        />
        <label className="field">
          Estado
          <select name="estado" defaultValue={account.estado}>
            <option value="ACTIVO">Activo</option>
            {!isCurrentUser && <option value="DESACTIVADO">Inactivo</option>}
            {!["ACTIVO", "DESACTIVADO"].includes(account.estado) && (
              <option value={account.estado}>
                {stateLabels[account.estado] ?? account.estado}
              </option>
            )}
          </select>
        </label>
        <label className="check-field">
          <input
            type="checkbox"
            name="correoVerificado"
            defaultChecked={account.correoVerificado}
          />{" "}
          La institución verificó que este correo pertenece al titular.
        </label>
        {REACTIVABLE_STATES.includes(account.estado) && (
          <PasswordField
            label="Nueva contraseña temporal (para reactivar)"
            name="password"
            minLength={8}
            maxLength={64}
            autoComplete="new-password"
          />
        )}
        <label className="field">
          Motivo del cambio *
          <textarea name="motivo" required maxLength={700} rows={3} />
        </label>
        <p className="hint">
          Al cambiar el correo, confirma nuevamente su titularidad. Para otro
          rol, crea un acceso separado.
        </p>
        <div className="form-actions">
          <button type="button" disabled={busy} onClick={onClose}>
            Cancelar
          </button>
          <button className="primary-action" disabled={busy}>
            {busy ? "Guardando…" : "Guardar cambios"}
          </button>
        </div>
      </form>
    </Dialog>
  );
}
