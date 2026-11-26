import type { UserRole } from "@/core/auth/types";
import { roleLabels } from "@/core/navigation/navigation";
import { Brand } from "./brand";
import { SpaceIcon } from "./role-context";
import { roleSpaces } from "./role-space";
import "./role-theme.css";
import styles from "./auth-identity-panel.module.css";

const roles: UserRole[] = ["ADMINISTRADOR", "DOCENTE", "ESTUDIANTE", "APODERADO"];

/** Columna institucional de la recuperación de contraseña. */
export function AuthIdentityPanel() {
  return (
    <section className={styles.panel} aria-label="AsisControl">
      <span className={styles.circleTop} aria-hidden="true" />
      <span className={styles.circleMiddle} aria-hidden="true" />
      <span className={styles.circleBottom} aria-hidden="true" />
      <div className={styles.panelContent}>
        <Brand />
        <div className={styles.identityContent}>
          <h2>
            Plataforma
            <br />
            Académica
            <br />
            Integrada
          </h2>
          <p>
            Matrícula, asistencia, evaluaciones, tareas y pagos del Colegio PUCP
            en un solo lugar.
          </p>
          <h3 className={styles.rolesTitle}>Un espacio para cada rol</h3>
          {/* Cada tarjeta usa el color con el que el rol verá su espacio al ingresar. */}
          <ul className={styles.roleGrid}>
            {roles.map((role) => (
              <li key={role} className={styles.roleCard} data-role-palette={role}>
                <span className={styles.roleIcon} aria-hidden="true">
                  <SpaceIcon icon={roleSpaces[role].icon} size={18} />
                </span>
                <strong>{roleLabels[role]}</strong>
                <small>{roleSpaces[role].title}</small>
              </li>
            ))}
          </ul>
        </div>
      </div>
    </section>
  );
}
