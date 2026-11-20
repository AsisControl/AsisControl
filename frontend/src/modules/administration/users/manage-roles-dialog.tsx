"use client";
import { useState, type FormEvent } from "react";
import { message } from "@/core/api/client";
import { roleLabel } from "@/shared/lib/formatters";
import { Dialog, Feedback } from "@/shared/ui";
import type { Account, Role } from "../types";
import { actualizarRolUsuario } from "./services/usuarioService";
import { accountRoles } from "./user-format";

export function ManageRolesDialog({
  account,
  roles,
  onClose,
  onSaved,
}: {
  account: Account;
  roles: Role[];
  onClose: () => void;
  onSaved: () => void;
}) {
  const [roleId, setRoleId] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const assigned = accountRoles(account).find((role) => String(role.id) === roleId);
  const enable = !assigned?.activo;

  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!roleId || busy) return;
    const reason = String(new FormData(event.currentTarget).get("motivo") ?? "").trim();
    if (!reason) {
      setError("Ingresa el motivo del cambio.");
      return;
    }
    setBusy(true);
    setError("");
    try {
      await actualizarRolUsuario(account.id, Number(roleId), enable, reason);
      onSaved();
    } catch (cause) {
      setError(message(cause));
      setBusy(false);
    }
  }

  return (
    <Dialog title="Administrar roles" onClose={onClose} busy={busy}>
      <p>{account.nombreCompleto} · {account.codigo}</p>
      <form onSubmit={save}>
        <Feedback error={error} />
        <label className="field">
          Rol *
          <select name="rolId" value={roleId} required disabled={busy}
            onChange={(event) => { setRoleId(event.target.value); setError(""); }}>
            <option value="">Selecciona un rol</option>
            {roles.map((role) => (
              <option key={role.id} value={role.id}>{roleLabel(role.nombre)}</option>
            ))}
          </select>
        </label>
        {roleId && (
          <p>
            {enable
              ? "Para habilitar este rol, la persona debe tener el perfil institucional correspondiente."
              : "Al retirar este rol, la persona conserva su cuenta y los demás roles habilitados."}
          </p>
        )}
        <label className="field">
          Motivo del cambio *
          <textarea name="motivo" rows={3} maxLength={700} required disabled={busy} />
        </label>
        <div className="form-actions">
          <button type="button" onClick={onClose} disabled={busy}>Cancelar</button>
          <button className="primary-action" disabled={!roleId || busy}>
            {busy ? "Guardando…" : enable ? "Habilitar rol" : "Retirar rol"}
          </button>
        </div>
      </form>
    </Dialog>
  );
}
