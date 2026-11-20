"use client";
import { useRef } from "react";
import { Dialog } from "@/shared/ui";

export function DiscardCommitmentDialog({ onContinue, onDiscard }: {
  onContinue: () => void;
  onDiscard: () => void;
}) {
  const continueButton = useRef<HTMLButtonElement>(null);

  return (
    <Dialog
      title="¿Descartar los cambios?"
      description="Los cambios que todavía no guardaste se perderán."
      role="alertdialog"
      initialFocusRef={continueButton}
      onClose={onContinue}
    >
      <div className="form-actions">
        <button ref={continueButton} type="button" className="secondary-action" onClick={onContinue}>
          Seguir editando
        </button>
        <button type="button" className="primary-action danger" onClick={onDiscard}>
          Descartar cambios
        </button>
      </div>
    </Dialog>
  );
}
