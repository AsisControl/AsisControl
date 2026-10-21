import { Brand } from "./brand";
import styles from "./auth-identity-panel.module.css";

export function AuthIdentityPanel() {
  return (
    <section className={styles.panel} aria-label="AsisControl">
      <span className={styles.circleTop} aria-hidden="true" />
      <span className={styles.circleMiddle} aria-hidden="true" />
      <span className={styles.circleBottom} aria-hidden="true" />
      <div className={styles.panelContent}>
        <Brand />
        <div className={styles.identityContent}>
          <h2>Plataforma<br />Académica<br />Integrada</h2>
          <p>
            Accede a la información académica, administrativa y de comunicación
            de AsisControl.
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
