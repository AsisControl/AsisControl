"use client";
import type { FormEvent, ReactNode } from "react";
import { Dialog } from "./dialog";
import { Feedback } from "./feedback";
/** Ventana modal con formulario, error y botones Cancelar/Confirmar. */
export function FormDialog({
  title,
  busy,
  error,
  onClose,
  onSubmit,
  children,
  notes,
}: {
  title: string;
  busy: boolean;
  error: string;
  onClose: () => void;
  onSubmit: (event: FormEvent<HTMLFormElement>) => void;
  /** Campos que se muestran dentro de la retícula del formulario. */
  children: ReactNode;
  /** Texto aclaratorio que se muestra debajo de los campos. */
  notes?: ReactNode;
}) {
  return (
    <Dialog title={title} onClose={onClose} busy={busy}>
      <form onSubmit={onSubmit}>
        <Feedback error={error} />
        <div className="form-grid">{children}</div>
        {notes}
        <div className="form-actions">
          <button type="button" disabled={busy} onClick={onClose}>
            Cancelar
          </button>
          <button className="primary-action" disabled={busy}>
            {busy ? "Guardando…" : "Confirmar"}
          </button>
        </div>
      </form>
    </Dialog>
  );
}
