"use client";
import { roleLabel } from "@/shared/lib/formatters";
import type { Account } from "../types";
import { CheckIcon } from "./step-parts";
import styles from "./new-user.module.css";
/** Confirmación tras crear el acceso. La contraseña temporal no se vuelve a mostrar. */
export function NewUserSuccess({
  account,
  onBack,
  onCreateAnother,
}: {
  account: Account;
  onBack: () => void;
  onCreateAnother: () => void;
}) {
  return (
    <article className={`${styles.card} ${styles.success}`} role="status">
      <span className={styles.successIcon}>
        <CheckIcon size={28} />
      </span>
      <h2>Usuario creado</h2>
      <p>
        La persona deberá cambiar la contraseña temporal al ingresar. La
        contraseña no se conserva ni se vuelve a mostrar en esta pantalla.
      </p>
      <dl>
        <div>
          <dt>Nombre</dt>
          <dd>{account.nombreCompleto}</dd>
        </div>
        <div>
          <dt>Rol</dt>
          <dd>{roleLabel(account.rol)}</dd>
        </div>
        <div>
          <dt>Usuario</dt>
          <dd>{account.username}</dd>
        </div>
        <div>
          <dt>Correo</dt>
          <dd>{account.correo}</dd>
        </div>
      </dl>
      <div className={styles.successActions}>
        <button
          type="button"
          className={styles.secondaryButton}
          onClick={onCreateAnother}
        >
          Crear otro usuario
        </button>
        <button type="button" className="primary-action" onClick={onBack}>
          Volver a usuarios
        </button>
      </div>
    </article>
  );
}
