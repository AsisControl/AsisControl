"use client";
import { useRef, useState, type FormEvent } from "react";
import { ApiError, message } from "@/core/api/client";
import type { Student } from "@/modules/students";
import { FormDialog } from "@/shared/ui";
import { kinshipLabel } from "./relationship-fields";
import { unlinkGuardian } from "./services/guardian-link-service";

export type GuardianLink = Pick<Student["apoderados"][number], "apoderadoId" | "nombreCompleto" | "parentesco">;

/**
 * Finaliza el vínculo con un motivo de auditoría y conserva su historial.
 */
export function GuardianUnlinkDialog({
  student,
  link,
  onClose,
  onUnlinked,
  onOutdated,
  actionLabel = "Retirar vínculo",
}: {
  student: Student;
  link: GuardianLink;
  onClose: () => void;
  onUnlinked: () => void;
  /** El vínculo ya no existe: el listado quedó desactualizado. */
  onOutdated: () => void;
  actionLabel?: string;
}) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [principalBlocked, setPrincipalBlocked] = useState(false);
  const sending = useRef(false);
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (sending.current) return;
    const motivo = String(new FormData(event.currentTarget).get("motivo") ?? "").trim();
    if (!motivo) {
      setError("Ingresa el motivo del cambio.");
      return;
    }
    sending.current = true;
    setBusy(true);
    setError("");
    try {
      await unlinkGuardian(student.id, link.apoderadoId, motivo);
      onUnlinked();
    } catch (e) {
      const code = e instanceof ApiError ? e.code : "";
      setError(message(e));
      setPrincipalBlocked(code === "APODERADO_PRINCIPAL_REQUERIDO");
      if (code === "VINCULO_NO_ENCONTRADO") onOutdated();
      sending.current = false;
      setBusy(false);
    }
  }
  return (
    <FormDialog
      title={actionLabel}
      description={`${link.nombreCompleto} · ${kinshipLabel(link.parentesco)} de ${student.persona.nombreCompleto}`}
      submitLabel={actionLabel}
      danger
      busy={busy}
      error={error}
      onClose={onClose}
      onSubmit={submit}
    >
      <p className="full-field hint">
        El apoderado dejará de tener acceso a la información de este
        estudiante. El retiro queda registrado en la auditoría.
      </p>
      {principalBlocked && (
        <p className="full-field hint">
          Para reemplazar al principal, abre Gestionar vínculos y edita al apoderado
          que asumirá ese rol. Después podrás finalizar este vínculo.
        </p>
      )}
      <label className="field full-field">
        Motivo del cambio
        <textarea name="motivo" required maxLength={500} rows={3} />
      </label>
    </FormDialog>
  );
}
