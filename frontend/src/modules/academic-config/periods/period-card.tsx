import { stateLabel } from "@/shared/lib/formatters";
import { formatDate } from "../lib/format-date";
import { PendingBadge } from "../pending/pending-badge";
import styles from "../academic-config.module.css";
import type { Period } from "../types";
type Props = {
  period: Period;
  /** Solo se activa/cierra con el año ACTIVO. */
  canChangeState: boolean;
  onChangeState: (period: Period, next: string) => void;
};
/** Tarjeta de un período con sus fechas y las acciones disponibles. */
export function PeriodCard({ period, canChangeState, onChangeState }: Props) {
  const planned = period.estado === "PLANIFICADO";
  const statusClass = [
    styles.status,
    period.estado === "ACTIVO" && styles.statusActive,
    period.estado === "CERRADO" && styles.statusClosed,
  ]
    .filter(Boolean)
    .join(" ");
  return (
    <article
      className={
        period.estado === "ACTIVO"
          ? `${styles.card} ${styles.cardActive}`
          : styles.card
      }
    >
      <div className={styles.cardHeader}>
        <strong>
          {period.orden}. {period.nombre}
        </strong>
        <span className={statusClass}>{stateLabel(period.estado)}</span>
      </div>
      <div className={styles.row}>
        <span>Inicio</span>
        <span>{formatDate(period.fechaInicio)}</span>
      </div>
      <div className={styles.row}>
        <span>Fin</span>
        <span>{formatDate(period.fechaFin)}</span>
      </div>
      <div className={styles.cardActions}>
        {canChangeState && period.estado !== "CERRADO" && (
          <button
            onClick={() =>
              onChangeState(period, planned ? "ACTIVO" : "CERRADO")
            }
          >
            {planned ? "Activar" : "Cerrar"}
          </button>
        )}
        <button disabled>Editar</button>
        <PendingBadge feature="editPeriod" />
      </div>
    </article>
  );
}
