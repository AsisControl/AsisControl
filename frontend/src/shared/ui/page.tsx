import type { ReactNode } from "react";
import styles from "./page.module.css";
/** Título de la pantalla con su descripción y, opcionalmente, acciones generales. */
export function PageHeader({
  title,
  description,
  actions,
}: {
  title: string;
  description?: ReactNode;
  actions?: ReactNode;
}) {
  return (
    <div className={styles.pageHeader}>
      <div>
        <h2>{title}</h2>
        {description && <p>{description}</p>}
      </div>
      {actions && <div className={styles.actions}>{actions}</div>}
    </div>
  );
}
/** Encabezado de una tarjeta (.panel): título, resumen y botones. */
export function PanelHeading({
  title,
  summary,
  actions,
}: {
  title: string;
  summary?: ReactNode;
  actions?: ReactNode;
}) {
  return (
    <div className={styles.panelHeading}>
      <div>
        <h3>{title}</h3>
        {summary && <p>{summary}</p>}
      </div>
      {actions && <div className={styles.actions}>{actions}</div>}
    </div>
  );
}
/** "Lucía Flores Vega" → "LF". */
export const initialsOf = (fullName: string) =>
  fullName
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((word) => word.charAt(0))
    .join("")
    .toUpperCase();
/** Nombre de una persona con sus iniciales, como en las tablas del prototipo. */
export function PersonCell({
  name,
  detail,
}: {
  name: string;
  /** Dato secundario bajo el nombre (código, correo). */
  detail?: ReactNode;
}) {
  return (
    <div className={styles.person}>
      <span className={styles.avatar} aria-hidden="true">
        {initialsOf(name)}
      </span>
      <span>
        <strong>{name}</strong>
        {detail && <small>{detail}</small>}
      </span>
    </div>
  );
}
