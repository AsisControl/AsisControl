import type { CSSProperties } from "react";
import styles from "./skeleton.module.css";

/**
 * Bloque gris con brillo suave que ocupa el lugar de un contenido que aún no llega.
 * Es decorativo: quien lo usa anuncia la carga con texto (ver Loading).
 */
export function Skeleton({
  width = "100%",
  height = 14,
  radius,
  className,
}: {
  width?: CSSProperties["width"];
  height?: CSSProperties["height"];
  radius?: CSSProperties["borderRadius"];
  className?: string;
}) {
  return (
    <span
      aria-hidden="true"
      className={[styles.bone, className].filter(Boolean).join(" ")}
      style={{ width, height, borderRadius: radius }}
    />
  );
}

/** Anchos variados para que las filas no parezcan una grilla rígida. */
const ANCHOS = ["72%", "54%", "86%", "63%", "47%", "78%"];
const ancho = (fila: number, columna = 0) => ANCHOS[(fila + columna * 2) % ANCHOS.length];

/** Tabla o listado paginado: encabezado y filas con la altura de una fila real. */
export function TableSkeleton({ rows = 5, columns = 4 }: { rows?: number; columns?: number }) {
  const grid = { gridTemplateColumns: `repeat(${columns}, minmax(0, 1fr))` };
  return (
    <div className={styles.table} aria-hidden="true">
      <div className={`${styles.tableRow} ${styles.tableHead}`} style={grid}>
        {Array.from({ length: columns }, (_, columna) => (
          <Skeleton key={columna} width="45%" height={10} />
        ))}
      </div>
      {Array.from({ length: rows }, (_, fila) => (
        <div className={styles.tableRow} style={grid} key={fila}>
          {Array.from({ length: columns }, (_, columna) => (
            <Skeleton key={columna} width={ancho(fila, columna)} />
          ))}
        </div>
      ))}
    </div>
  );
}

/** Lista simple (historial, vínculos, solicitudes): una línea principal y una secundaria. */
export function ListSkeleton({ rows = 4 }: { rows?: number }) {
  return (
    <div className={styles.list} aria-hidden="true">
      {Array.from({ length: rows }, (_, fila) => (
        <div className={styles.listItem} key={fila}>
          <Skeleton width={ancho(fila)} height={13} />
          <Skeleton width="38%" height={10} />
        </div>
      ))}
    </div>
  );
}

/** Ficha o detalle: pares etiqueta/valor en dos columnas, como .detail-grid. */
export function DetailSkeleton({ fields = 6 }: { fields?: number }) {
  return (
    <div className={styles.detail} aria-hidden="true">
      {Array.from({ length: fields }, (_, campo) => (
        <div className={styles.detailField} key={campo}>
          <Skeleton width="34%" height={10} />
          <Skeleton width={ancho(campo)} height={15} />
        </div>
      ))}
    </div>
  );
}

/** Tarjetas genéricas en grilla (estudiantes asociados, configuración por año). */
export function CardGridSkeleton({ cards = 3 }: { cards?: number }) {
  return (
    <div className={styles.cards} aria-hidden="true">
      {Array.from({ length: cards }, (_, tarjeta) => (
        <div className={styles.card} key={tarjeta}>
          <Skeleton width={36} height={36} radius={8} />
          <Skeleton width={ancho(tarjeta)} height={16} />
          <Skeleton width="45%" height={12} />
          <Skeleton width="100%" height={12} />
        </div>
      ))}
    </div>
  );
}

/** Horario semanal: columna de horas y cinco días con algunos bloques. */
export function TimetableSkeleton({ rows = 6 }: { rows?: number }) {
  return (
    <div className={styles.timetable} aria-hidden="true">
      {Array.from({ length: rows + 1 }, (_, fila) =>
        Array.from({ length: 6 }, (_, columna) => {
          const encabezado = fila === 0;
          const hora = columna === 0;
          const bloque = !encabezado && !hora && (fila * 3 + columna * 2) % 4 === 0;
          return (
            <div className={styles.timetableCell} key={`${fila}-${columna}`}>
              {(encabezado || hora) && <Skeleton width={hora ? "70%" : "50%"} height={10} />}
              {bloque && <Skeleton height={44} radius={6} />}
            </div>
          );
        }),
      )}
    </div>
  );
}
