"use client";
import type { FormEvent, ReactNode } from "react";
import { Dialog, type DialogSize } from "./dialog";
import { Feedback } from "./feedback";
/** Ventana modal con formulario, error y botones Cancelar/Confirmar. */
export function FormDialog({
  title,
  description,
  size,
  busy,
  error,
  onClose,
  onSubmit,
  children,
  notes,
  submitLabel = "Confirmar",
  danger = false,
}: {
  title: string;
  description?: ReactNode;
  size?: DialogSize;
  busy: boolean;
  error: string;
  onClose: () => void;
  onSubmit: (event: FormEvent<HTMLFormElement>) => void;
  /** Campos que se muestran dentro de la retícula del formulario. */
  children: ReactNode;
  /** Texto aclaratorio que se muestra debajo de los campos. */
  notes?: ReactNode;
  /** Texto del botón principal: describe la acción ("Guardar curso"). */
  submitLabel?: string;
  /** Acciones que dejan un registro inactivo usan el botón en rojo. */
  danger?: boolean;
}) {
  return (
    <Dialog
      title={title}
      description={description}
      size={size}
      onClose={onClose}
      busy={busy}
    >
      <form onSubmit={onSubmit}>
        <Feedback error={error} />
        <div className="form-grid">{children}</div>
        {notes}
        <div className="form-actions">
          <button type="button" disabled={busy} onClick={onClose}>
            Cancelar
          </button>
          <button
            className={danger ? "primary-action danger" : "primary-action"}
            disabled={busy}
          >
            {busy ? "Guardando…" : submitLabel}
          </button>
        </div>
      </form>
    </Dialog>
  );
}
