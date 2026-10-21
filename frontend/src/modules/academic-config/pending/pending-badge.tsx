import styles from "../academic-config.module.css";
import { pendingFeatures, type PendingFeatureKey } from "./pending-features";
/** Insignia para controles que aún no tienen soporte en el backend. */
export function PendingBadge({ feature }: { feature: PendingFeatureKey }) {
  return (
    <span className={styles.pendingBadge} title={pendingFeatures[feature].hint}>
      Pendiente de backend
    </span>
  );
}
