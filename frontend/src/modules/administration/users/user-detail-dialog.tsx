"use client";
import { Dialog } from "@/shared/ui";
import { roleLabel } from "@/shared/lib/formatters";
import type { Account } from "../types";
import {
  dateTimeLabel,
  initialsOf,
  rolePillClass,
  stateLabels,
  statePillClass,
} from "./user-format";
import styles from "./users.module.css";
/** Ventana de solo lectura que se abre con el botón del ojo. */
export function UserDetailDialog({
  account,
  onClose,
  onEdit,
}: {
  account: Account;
  onClose: () => void;
  onEdit: () => void;
}) {
  return (
    <Dialog title="Detalle del usuario" onClose={onClose}>
      <div className={styles.profileHeading}>
        <span className={styles.initials} aria-hidden="true">
          {initialsOf(account.nombreCompleto)}
        </span>
        <div>
          <h3>{account.nombreCompleto}</h3>
          <div className={styles.pillRow}>
            <span className={rolePillClass(account.rol)}>
              {roleLabel(account.rol)}
            </span>
            <span className={statePillClass(account.estado)}>
              {stateLabels[account.estado] ?? account.estado}
            </span>
          </div>
        </div>
      </div>
      <p className={styles.sectionTitle}>Cuenta</p>
      <dl className="detail-grid">
        <div>
          <dt>Usuario</dt>
          <dd>{account.username}</dd>
        </div>
        <div>
          <dt>Correo</dt>
          <dd>{account.correo}</dd>
        </div>
        <div>
          <dt>Correo para recuperación</dt>
          <dd>
            {account.correoVerificado ? "Confirmado" : "Pendiente de confirmar"}
          </dd>
        </div>
        <div>
          <dt>Creado</dt>
          <dd>{dateTimeLabel(account.fechaCreacion)}</dd>
        </div>
      </dl>
      <p className={styles.sectionTitle}>Actividad y seguridad</p>
      <dl className="detail-grid">
        <div>
          <dt>Último acceso</dt>
          <dd>{dateTimeLabel(account.ultimoAcceso)}</dd>
        </div>
        <div>
          <dt>Intentos fallidos</dt>
          <dd>{account.intentosFallidos}</dd>
        </div>
        {account.bloqueadoHasta && (
          <div>
            <dt>Bloqueado hasta</dt>
            <dd>{dateTimeLabel(account.bloqueadoHasta)}</dd>
          </div>
        )}
        <div>
          <dt>Cambio de contraseña</dt>
          <dd>
            {account.requiereCambioContrasena
              ? `Pendiente${account.contrasenaTemporalExpira ? ` · temporal vence ${dateTimeLabel(account.contrasenaTemporalExpira)}` : ""}`
              : "Al día"}
          </dd>
        </div>
      </dl>
      <div className="form-actions">
        <button type="button" onClick={onClose}>
          Cerrar
        </button>
        <button type="button" className="primary-action" onClick={onEdit}>
          Editar acceso
        </button>
      </div>
    </Dialog>
  );
}
