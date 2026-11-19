import type { ReactNode } from "react";
import {
  CardGridSkeleton,
  DetailSkeleton,
  ListSkeleton,
  TableSkeleton,
  TimetableSkeleton,
} from "./skeleton";
import styles from "./loading.module.css";

type Forma = "table" | "list" | "detail" | "cards" | "timetable";

const FORMAS: Record<Forma, (rows?: number, columns?: number) => ReactNode> = {
  table: (rows, columns) => <TableSkeleton rows={rows} columns={columns} />,
  list: (rows) => <ListSkeleton rows={rows} />,
  detail: (rows) => <DetailSkeleton fields={rows} />,
  cards: (rows) => <CardGridSkeleton cards={rows} />,
  timetable: (rows) => <TimetableSkeleton rows={rows} />,
};

/**
 * Estado de carga. El texto se anuncia a los lectores de pantalla (role="status").
 * - block (por defecto): esqueleto con la forma del contenido que viene (tabla, lista, ficha,
 *   tarjetas u horario, o uno propio); el texto queda solo para lectores de pantalla.
 * - inline: anillo pequeño + texto, para cargas dentro de un formulario, lista o celda.
 * - screen: anillo grande + texto a pantalla completa (restaurar o cerrar la sesión).
 */
export function Loading({
  children,
  variant = "block",
  skeleton = "table",
  rows,
  columns,
  className,
}: {
  children: ReactNode;
  variant?: "block" | "inline" | "screen";
  /** Forma del esqueleto en la variante block, o un esqueleto propio de la pantalla. */
  skeleton?: Forma | ReactNode;
  rows?: number;
  columns?: number;
  className?: string;
}) {
  if (variant === "block") {
    const forma = typeof skeleton === "string" && skeleton in FORMAS
      ? FORMAS[skeleton as Forma](rows, columns)
      : skeleton;
    return (
      <div role="status" aria-busy="true" className={[styles.skeleton, className].filter(Boolean).join(" ")}>
        <span className={styles.srOnly}>{children}</span>
        {forma}
      </div>
    );
  }
  return (
    <p role="status" className={[styles.loading, styles[variant], className].filter(Boolean).join(" ")}>
      <span className={styles.spinner} aria-hidden="true" />
      <span>{children}</span>
    </p>
  );
}
