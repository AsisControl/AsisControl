"use client";
import { roleLabel } from "@/shared/lib/formatters";
import { rolePillClass } from "../users/user-format";
import styles from "./new-user.module.css";
/** Resumen lateral con lo elegido hasta ahora y las acciones del formulario. */
export function NewUserSummary({
  personName,
  role,
  profileAction,
  username,
  email,
  studentCount,
  missing,
  busy,
  onCancel,
}: {
  personName: string;
  role: string;
  /** Qué ocurrirá con el perfil institucional; vacío si aún no hay rol. */
  profileAction: string;
  username: string;
  email: string;
  /** null si el rol no admite estudiantes asociados. */
  studentCount: number | null;
  /** Lo que falta para poder crear el usuario. */
  missing: string[];
  busy: boolean;
  onCancel: () => void;
}) {
  const pending = "Pendiente";
  return (
    <aside className={`${styles.card} ${styles.summary}`}>
      <h3>Resumen</h3>
      <dl>
        <div>
          <dt>Persona</dt>
          <dd>{personName || pending}</dd>
        </div>
        <div>
          <dt>Rol</dt>
          <dd>
            {role ? (
              <span className={rolePillClass(role)}>{roleLabel(role)}</span>
            ) : (
              pending
            )}
          </dd>
        </div>
        {profileAction && (
          <div>
            <dt>Perfil institucional</dt>
            <dd>{profileAction}</dd>
          </div>
        )}
        <div>
          <dt>Usuario</dt>
          <dd>{username || pending}</dd>
        </div>
        <div>
          <dt>Correo</dt>
          <dd>{email || pending}</dd>
        </div>
        {studentCount !== null && (
          <div>
            <dt>Estudiantes asociados</dt>
            <dd>{studentCount}</dd>
          </div>
        )}
      </dl>
      {missing.length > 0 && (
        <p className={styles.missing}>Falta: {missing.join(", ")}.</p>
      )}
      <div className={styles.summaryActions}>
        <button
          className="primary-action"
          disabled={busy || missing.length > 0}
        >
          {busy ? "Creando…" : "Crear usuario"}
        </button>
        <button
          type="button"
          className={styles.secondaryButton}
          onClick={onCancel}
        >
          Cancelar
        </button>
      </div>
    </aside>
  );
}
