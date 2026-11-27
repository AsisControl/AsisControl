import Image from "next/image";
import type { ReactNode } from "react";
import { AccessBrand } from "./access-brand";
import access from "./access.module.css";
import styles from "./login-screen.module.css";

/** Pantalla de acceso: marca, ilustración escolar y tarjeta con el formulario. */
export function AccessLayout({
  titleId,
  children,
}: {
  titleId: string;
  children: ReactNode;
}) {
  return (
    <main className={`${access.surface} ${styles.screen}`}>
      <AccessBrand className={styles.masthead} />
      <div className={styles.stage}>
        <aside className={styles.hero} aria-label="Gestión escolar">
          <div className={styles.art} aria-hidden="true">
            <Image src="/brand/registro-escolar.webp" alt="" fill unoptimized loading="eager" />
          </div>
          <div className={styles.story}>
            <p className={styles.headline}>La jornada escolar, <span>en un solo lugar.</span></p>
            <p className={styles.caption}>Matrícula, asistencia, evaluaciones, tareas y pagos.</p>
          </div>
        </aside>
        <section className={styles.formArea} aria-labelledby={titleId}>
          {children}
        </section>
      </div>
    </main>
  );
}
