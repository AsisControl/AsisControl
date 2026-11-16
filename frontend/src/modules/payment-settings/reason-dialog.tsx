"use client";
import { useState, type FormEvent } from "react";
import { message } from "@/core/api/client";
import { Dialog, Feedback } from "@/shared/ui";

export function SettingsReasonDialog({ title, description, actionLabel, onClose, onConfirm }: {
  title: string; description: string; actionLabel: string; onClose: () => void; onConfirm: (reason: string) => Promise<unknown>;
}) {
  const [reason, setReason] = useState(""), [error, setError] = useState(""), [busy, setBusy] = useState(false);
  async function submit(event: FormEvent) {
    event.preventDefault(); if (busy) return;
    if (!reason.trim()) { setError("Explica el motivo para registrar el cambio."); return; }
    setBusy(true); setError("");
    try { await onConfirm(reason.trim()); } catch (caught) { setError(message(caught)); setBusy(false); }
  }
  return <Dialog title={title} description={description} onClose={onClose} busy={busy}>
    <form onSubmit={submit} noValidate><Feedback error={error} /><label className="field">Motivo *<textarea name="motivo" required value={reason} disabled={busy} maxLength={500} rows={3} onChange={(e) => setReason(e.target.value)} /></label>
      <div className="form-actions"><button type="button" className="secondary-action" disabled={busy} onClick={onClose}>Cancelar</button><button className="primary-action" disabled={busy}>{busy ? "Guardando…" : actionLabel}</button></div>
    </form>
  </Dialog>;
}
