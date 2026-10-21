"use client";
import { useState, type FormEvent } from "react";
import { message } from "@/core/api/client";
import { Dialog, Feedback, ReasonField } from "@/shared/ui";
import type { Account } from "../types";
import { actualizarUsuario } from "./services/usuarioService";
/** Los accesos no se eliminan: se desactivan con un motivo auditable. */
export function DeactivateAccessDialog({
  account,
  onClose,
  onDeactivated,
}: {
  account: Account;
  onClose: () => void;
  onDeactivated: () => void;
}) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  async function deactivate(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setBusy(true);
    setError("");
    try {
      await actualizarUsuario(account, {
        correo: account.correo,
        estado: "DESACTIVADO",
        correoVerificado: account.correoVerificado,
        temporaryPassword: null,
        motivo: String(new FormData(e.currentTarget).get("motivo")),
      });
      onDeactivated();
    } catch (e) {
      setError(message(e));
      setBusy(false);
    }
  }
  return (
    <Dialog title="Desactivar usuario" onClose={onClose} busy={busy}>
      <form onSubmit={deactivate}>
        <Feedback error={error} />
        <p>
          {account.nombreCompleto} no podrá ingresar al sistema y sus sesiones
          activas se cerrarán. El historial se conserva.
        </p>
        <ReasonField label="Motivo de la desactivación" />
        <div className="form-actions">
          <button type="button" disabled={busy} onClick={onClose}>
            Cancelar
          </button>
          <button className="primary-action" disabled={busy}>
            {busy ? "Desactivando…" : "Desactivar"}
          </button>
        </div>
      </form>
    </Dialog>
  );
}
