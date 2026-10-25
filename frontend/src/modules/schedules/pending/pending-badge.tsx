import styles from "../schedules.module.css";
import { pendingFeatures, type PendingFeatureKey } from "./pending-features";
/** Insignia para controles que aún no están disponibles; la razón va en el título. */
export function PendingBadge({ feature }: { feature: PendingFeatureKey }) {
  const { hint } = pendingFeatures[feature];
  return (
    <span className={styles.pendingBadge} title={hint}>
      Próximamente
      <span className={styles.srOnly}>: {hint}</span>
    </span>
  );
}
