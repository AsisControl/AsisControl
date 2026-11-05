import { dateLabel, roleLabel, stateLabel } from "@/shared/lib/formatters";
import type { History, HistoryField } from "./types";
/** Acciones que registra la auditoría de solicitudes y matrículas. */
const ACTIONS: Record<string, string> = {
  CREAR_MATRICULA: "Matrícula creada",
  CAMBIAR_ESTADO_MATRICULA: "Cambio de estado de la matrícula",
  GUARDAR_SOLICITUD: "Solicitud guardada",
  FINALIZAR_SOLICITUD: "Solicitud finalizada",
};
const FIELD_LABELS: Record<string, string> = {
  estado: "Estado",
  seccion: "Sección",
};
/** Estados cuyo código no lleva la tilde que necesita el texto. */
const STATE_LABELS: Record<string, string> = {
  EN_REVISION: "En revisión",
  MATRICULA_FINALIZADA: "Matrícula finalizada",
};
const fieldValue = (field: string, value: string) =>
  field === "estado" ? (STATE_LABELS[value] ?? stateLabel(value)) : value;
/** "Estado: Activa → Retirada", o solo el valor si es el primer registro. */
function changeLabel({ campo, anterior, nuevo }: HistoryField) {
  const values = [anterior, nuevo]
    .filter((value) => value !== null)
    .map((value) => fieldValue(campo, value));
  return `${FIELD_LABELS[campo] ?? campo}: ${values.join(" → ")}`;
}
/** Cronología de una solicitud o matrícula, sin códigos ni identificadores internos. */
export function EnrollmentHistory({ events }: { events: History[] }) {
  return (
    <ol className="history-list">
      {events.map((item) => (
        <li key={item.id}>
          <strong>{ACTIONS[item.accion] ?? stateLabel(item.accion)}</strong>
          <small>
            {[
              dateLabel(item.fecha),
              item.actorNombre ?? item.actor,
              item.rol && stateLabel(roleLabel(item.rol)),
            ]
              .filter(Boolean)
              .join(" · ")}
          </small>
          {item.campos
            .filter(({ anterior, nuevo }) => anterior !== nuevo)
            .map((change) => (
              <p key={change.campo}>{changeLabel(change)}</p>
            ))}
          {item.motivo && <p>Motivo: {item.motivo}</p>}
        </li>
      ))}
    </ol>
  );
}
