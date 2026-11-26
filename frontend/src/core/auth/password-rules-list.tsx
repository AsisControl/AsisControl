import { passwordRules } from "./password-policy";
import styles from "./password-screens.module.css";

/** Requisitos de la contraseña nueva, marcados según lo que ya se escribió. */
export function PasswordRulesList({ id, password }: { id: string; password: string }) {
  return (
    <ul id={id} className={styles.rules} aria-label="Requisitos de la nueva contraseña">
      {passwordRules.map(({ label, isMet }) => {
        const met = isMet(password);
        return (
          <li key={label} data-met={met}>
            {label}
            <span className={styles.ruleState}>{met ? ": cumplido" : ": pendiente"}</span>
          </li>
        );
      })}
    </ul>
  );
}
