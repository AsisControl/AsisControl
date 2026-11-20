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

/** Campos de formulario (etiqueta + control) mientras llegan las opciones de un selector. */
export function FieldSkeleton({ fields = 1 }: { fields?: number }) {
  return (
    <div className={styles.fields} aria-hidden="true">
      {Array.from({ length: fields }, (_, campo) => (
        <div className={styles.field} key={campo}>
          <Skeleton width="30%" height={10} />
          <Skeleton height={38} radius={8} />
        </div>
      ))}
    </div>
  );
}

/** Indicadores (cifra + etiqueta) seguidos de una tabla: reportes y resúmenes con detalle. */
export function MetricsSkeleton({
  metrics = 4,
  rows = 5,
  columns = 5,
}: {
  metrics?: number;
  rows?: number;
  columns?: number;
}) {
  return (
    <div className={styles.metricsBlock} aria-hidden="true">
      <div className={styles.metrics}>
        {Array.from({ length: metrics }, (_, indicador) => (
          <div className={styles.metric} key={indicador}>
            <Skeleton width="55%" height={11} />
            <Skeleton width={64} height={26} />
            <Skeleton width="40%" height={10} />
          </div>
        ))}
      </div>
      <TableSkeleton rows={rows} columns={columns} />
    </div>
  );
}

/** Pantalla de varios paneles apilados (encabezado del año y sus listados), como configuración. */
export function PanelsSkeleton({ panels = 4 }: { panels?: number }) {
  return (
    <div className={styles.panels} aria-hidden="true">
      <div className={styles.panelsHeading}>
        <div className={styles.panelsTitle}>
          <Skeleton width={150} height={22} />
          <Skeleton width={260} height={12} />
        </div>
        <div className={styles.panelsActions}>
          <Skeleton width={96} height={34} radius={8} />
          <Skeleton width={120} height={34} radius={8} />
        </div>
      </div>
      {Array.from({ length: panels }, (_, panel) => (
        <div className={styles.panel} key={panel}>
          <div className={styles.panelHeader}>
            <div className={styles.panelTitle}>
              <Skeleton width={170} height={16} />
              <Skeleton width="60%" height={11} />
            </div>
            <Skeleton width={104} height={34} radius={8} />
          </div>
          <TableSkeleton rows={3} columns={4} />
        </div>
      ))}
    </div>
  );
}
