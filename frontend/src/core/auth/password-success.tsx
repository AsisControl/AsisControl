/* eslint-disable @next/next/no-img-element -- SVG assets are served from public/. */
import styles from "./password-screens.module.css";

/** Marca de confirmación sobre el título cuando la contraseña ya se cambió. */
export function PasswordSuccessIcon() {
  return (
    <span className={styles.successIcon} aria-hidden="true">
      <img src="/icons/check.svg" alt="" width={26} height={26} />
    </span>
  );
}
