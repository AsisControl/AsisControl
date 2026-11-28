"use client";
import { useRef, useState, type FormEvent } from "react";
import { ApiError, api, message } from "@/core/api/client";
import { FormDialog } from "@/shared/ui";
import { formatDate } from "../lib/format-date";
import type { NonTeachingDay } from "../types";
type Props = {
  day: NonTeachingDay;
  onClose: () => void;
  /** Se llama tras eliminar; el panel recarga la lista. */
  onDeleted: () => void;
  /** Se llama si el día cambió o ya no existe: la lista quedó desactualizada. */
  onStale: (reason: string) => void;
};
/** Confirmación para eliminar un día no lectivo; el servidor decide si se puede. */
export function NonTeachingDayDeleteDialog({
  day,
  onClose,
  onDeleted,
  onStale,
}: Props) {
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const sending = useRef(false);
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (sending.current) return;
    sending.current = true;
    setBusy(true);
    setError("");
    try {
      await api<null>(
        `/api/configuracion-academica/dias-no-lectivos/${day.id}?version=${day.version}`,
        { method: "DELETE" },
      );
      onDeleted();
    } catch (failure) {
      if (
        failure instanceof ApiError &&
        (failure.status === 404 || failure.code === "VERSION_DESACTUALIZADA")
      ) {
        onStale(message(failure));
        return;
      }
      setError(message(failure));
      sending.current = false;
      setBusy(false);
    }
  }
  return (
    <FormDialog
      title="Eliminar día no lectivo"
      description={`${formatDate(day.fecha)}${day.descripcion ? ` · ${day.descripcion}` : ""}`}
      submitLabel="Eliminar día"
      danger
      busy={busy}
      error={error}
      onClose={onClose}
      onSubmit={submit}
    >
      <p className="full-field hint">
        La fecha dejará de figurar como no lectiva y podrás volver a registrarla.
      </p>
    </FormDialog>
  );
}
