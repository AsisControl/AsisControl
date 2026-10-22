import { occupancyBarWidth, occupancyPercent } from "../lib/section-metrics";
import styles from "../academic-config.module.css";
import type { Section } from "../types";
/** Barra de ocupación con el porcentaje al lado; roja desde el 100 %. */
export function OccupancyBar({
  section,
}: {
  section: Pick<Section, "capacidadMaxima" | "ocupacion">;
}) {
  const percent = occupancyPercent(section);
  const full = percent >= 100;
  return (
    <div className={styles.occupancy}>
      <div
        className={styles.occupancyTrack}
        role="progressbar"
        aria-label="Ocupación"
        aria-valuemin={0}
        aria-valuemax={100}
        aria-valuenow={Math.min(100, percent)}
      >
        <div
          className={`${styles.occupancyFill} ${full ? styles.occupancyFull : ""}`}
          style={{ width: `${occupancyBarWidth(section)}%` }}
        />
      </div>
      <span className={styles.occupancyValue}>{percent}%</span>
    </div>
  );
}
