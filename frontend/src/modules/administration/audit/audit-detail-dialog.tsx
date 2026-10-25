"use client";
import { Dialog } from "@/shared/ui";
import { roleLabel } from "@/shared/lib/formatters";
import type { Audit } from "../types";
import { dateTimeLabel, initialsOf, rolePillClass } from "../users/user-format";
import {
  actionLabel,
  actorDisplayName,
  entityLabel,
  modulePillClass,
  moduleLabel,
  resultLabels,
} from "./audit-format";
import styles from "./audit.module.css";
/** Ventana de solo lectura que se abre con el botón del ojo. */
export function AuditDetailDialog({
  audit,
  onClose,
}: {
  audit: Audit;
  onClose: () => void;
}) {
  const actorName = actorDisplayName(audit);
  return (
    <Dialog title="Detalle del registro" onClose={onClose}>
      <div className={styles.detailHeading}>
        <span className={styles.initials} aria-hidden="true">
          {initialsOf(actorName)}
        </span>
        <div>
          <h3>{actorName}</h3>
          <div className={styles.pillRow}>
            {audit.rolActor && (
              <span className={rolePillClass(audit.rolActor)}>
                {roleLabel(audit.rolActor)}
              </span>
            )}
            <span className={modulePillClass(audit.modulo)}>
              {moduleLabel(audit.modulo)}
            </span>
          </div>
        </div>
      </div>
      <p className={styles.sectionTitle}>Operación</p>
      <dl className="detail-grid">
        <div>
          <dt>Acción</dt>
          <dd>{actionLabel(audit.accion)}</dd>
        </div>
        <div>
          <dt>Resultado</dt>
          <dd>{resultLabels[audit.resultado] ?? audit.resultado}</dd>
        </div>
        <div>
          <dt>Fecha y hora</dt>
          <dd>{dateTimeLabel(audit.fechaHora)}</dd>
        </div>
        <div>
          <dt>Usuario</dt>
          <dd>{audit.actorIdentificador}</dd>
        </div>
        <div>
          <dt>Registro afectado</dt>
          <dd>
            {entityLabel(audit.entidad)}
            {audit.registroId ? ` #${audit.registroId}` : ""}
          </dd>
        </div>
        <div>
          <dt>Motivo</dt>
          <dd>{audit.motivo || "Sin motivo registrado"}</dd>
        </div>
      </dl>
      <p className={styles.sectionTitle}>Trazabilidad</p>
      <dl className="detail-grid">
        <div>
          <dt>Referencia de correlación</dt>
          <dd className={styles.reference}>{audit.correlationId}</dd>
        </div>
        <div>
          <dt>Dirección IP</dt>
          <dd className={styles.reference}>
            {audit.direccionIp || "Sin registrar"}
          </dd>
        </div>
      </dl>
      {(audit.valorAnterior || audit.nuevoValor) && (
        <>
          <p className={styles.sectionTitle}>Cambios</p>
          <dl className="detail-grid">
            <div>
              <dt>Antes</dt>
              <dd className={styles.change}>{audit.valorAnterior || "—"}</dd>
            </div>
            <div>
              <dt>Después</dt>
              <dd className={styles.change}>{audit.nuevoValor || "—"}</dd>
            </div>
          </dl>
        </>
      )}
      <div className="form-actions">
        <button type="button" className="primary-action" onClick={onClose}>
          Cerrar
        </button>
      </div>
    </Dialog>
  );
}
