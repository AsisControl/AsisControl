"use client";
import { useRef, useState, type FormEvent } from "react";
import { ApiError, message } from "@/core/api/client";
import type { Student } from "@/modules/students";
import { FormDialog } from "@/shared/ui";
import { stateLabel } from "@/shared/lib/formatters";
import { unlinkGuardian } from "./services/guardian-link-service";

export type GuardianLink = Student["apoderados"][number];

/**
 * Confirma el retiro de un vínculo (sin motivo). El backend no permite retirar al
 * apoderado principal mientras haya otros ni cambiarlo directamente.
 */
export function GuardianUnlinkDialog({
  student,
  link,
  onClose,
  onUnlinked,
  onOutdated,
}: {
  student: Student;
  link: GuardianLink;
  onClose: () => void;
  onUnlinked: () => void;
  /** El vínculo ya no existe: el listado quedó desactualizado. */
  onOutdated: () => void;
}) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [principalBlocked, setPrincipalBlocked] = useState(false);
  const sending = useRef(false);
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (sending.current) return;
    sending.current = true;
    setBusy(true);
    setError("");
    try {
      await unlinkGuardian(student.id, link.apoderadoId);
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
      title="Retirar vínculo"
      description={`${link.nombreCompleto} · ${stateLabel(link.parentesco)} de ${student.persona.nombreCompleto}`}
      submitLabel="Retirar vínculo"
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
          Para reemplazar al principal, vincula un apoderado nuevo como
          principal y luego retira a este. Si el reemplazo ya está vinculado,
          retíralo primero y vuelve a vincularlo como principal.
        </p>
      )}
    </FormDialog>
  );
}
