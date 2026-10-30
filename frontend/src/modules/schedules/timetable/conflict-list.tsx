import type { Conflict } from "../lib/timetable";
import styles from "../schedules.module.css";
/** Conflictos de docente, aula, sección o aforo que impiden confirmar un bloque. */
export function ConflictList({
  heading,
  conflicts,
}: {
  heading: string;
  conflicts: Conflict[];
}) {
  if (!conflicts.length) return null;
  return (
    <section className={`full-field ${styles.conflicts}`} aria-label={heading} role="alert">
      <strong>{heading}</strong>
      <ul>
        {conflicts.map((item, index) => (
          <li key={`${item.kind}-${index}`} data-kind={item.kind}>
            <b>{item.title}:</b> {item.detail}
          </li>
        ))}
      </ul>
    </section>
  );
}
