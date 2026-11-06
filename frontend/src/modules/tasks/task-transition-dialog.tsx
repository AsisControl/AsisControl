"use client";
import { useRef, useState } from "react";
import { ApiError } from "@/core/api/client";
import { message } from "./task-error-copy";
import { Dialog, Feedback } from "@/shared/ui";
import { publicarTarea, cerrarTarea, archivarTarea } from "./services/tarea-service";
import type { TareaResponse } from "./types";
import styles from "./tasks.module.css";

export type TaskTransition = "publicar" | "cerrar" | "archivar";
const operations = {
  publicar: { label: "Publicar", description: "La tarea dejará de ser un borrador y estará disponible para los estudiantes. Podrás seguir editándola; si ya tiene entregas, cambiar la fecha límite pedirá un motivo.", run: publicarTarea },
  cerrar: { label: "Cerrar", description: "Al cerrar la tarea, dejará de recibir entregas.", run: cerrarTarea },
  archivar: { label: "Archivar", description: "La tarea quedará archivada. No podrás editarla, calificar sus entregas ni volver a activarla.", run: archivarTarea },
};
const businessMessages: Record<string, string> = {
  TAREA_TRANSICION_INVALIDA: "El estado de la tarea cambió o no permite esta acción. Actualiza el listado antes de continuar.",
  TAREA_FECHA_LIMITE_VENCIDA: "No se puede publicar: la fecha límite ya se alcanzó. Actualiza el listado y revisa el borrador.",
  TAREA_PUBLICACION_FUTURA: "No se puede publicar: la fecha de publicación está en el futuro. Revisa el borrador.",
  TAREA_AUN_VIGENTE: "Aún no puedes cerrar la tarea porque la fecha límite no ha vencido.",
};

export function TaskTransitionDialog({ task, action, onClose, onRefresh, onSaved }: {
  task: TareaResponse;
  action: TaskTransition;
  onClose: () => void;
  onRefresh: () => void;
  onSaved: (task: TareaResponse) => void;
}) {
  const operation = operations[action];
  const lock = useRef(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [requiresRefresh, setRequiresRefresh] = useState(false);
  async function confirm() {
    if (lock.current || requiresRefresh) return;
    lock.current = true;
    setBusy(true);
    setError("");
    try {
      onSaved(await operation.run(task.id));
    } catch (e) {
      let text = message(e);
      if (e instanceof ApiError) {
        text = businessMessages[e.code] ?? ({
          401: "La sesión no es válida. Inicia sesión nuevamente para continuar.",
          403: "No tienes permiso para realizar esta acción sobre la tarea.",
          404: "La tarea ya no está disponible. Actualiza el listado.",
          409: "La tarea fue modificada por otro usuario. Actualiza el listado antes de continuar.",
        }[e.status] ?? text);
        setRequiresRefresh([401, 403, 404, 409].includes(e.status) || e.code === "TAREA_TRANSICION_INVALIDA");
      }
      setError(text);
    } finally {
      lock.current = false;
      setBusy(false);
    }
  }
  return <Dialog title={`${operation.label} tarea`} description={task.nombre} busy={busy} onClose={onClose}>
    <div className={action === "cerrar" ? styles.closeMessage : undefined}>
      <p>{operation.description}</p>
      <Feedback error={error} />
    </div>
    {error && action !== "cerrar" && <button type="button" disabled={busy} onClick={onRefresh}>Actualizar listado</button>}
    <div className="form-actions">
      <button type="button" disabled={busy} onClick={onClose}>Cancelar</button>
      <button type="button" className="primary-action" disabled={busy || requiresRefresh} onClick={confirm}>
        {busy ? "Procesando…" : `${operation.label} tarea`}
      </button>
    </div>
  </Dialog>;
}
