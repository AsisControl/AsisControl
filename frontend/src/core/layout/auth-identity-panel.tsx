import { Brand } from "./brand";
import styles from "./auth-identity-panel.module.css";
/** Columna institucional común a login, recuperación y cambio de contraseña. */
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
            Accede a la información académica, administrativa y de comunicación
            del Colegio PUCP.
          </p>
          <ul>
            <li>Administrador</li>
            <li>Docente</li>
            <li>Estudiante</li>
            <li>Apoderado</li>
          </ul>
        </div>
      </div>
    </section>
  );
}
