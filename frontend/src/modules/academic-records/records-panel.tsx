"use client";
import { useState, type ReactNode } from "react";
import { Feedback, Field, Pager, PanelHeading, Loading, RecordOrderSelect } from "@/shared/ui";
import { usePaged } from "@/shared/hooks/use-paged";
import { countLabel, timeLabel } from "@/shared/lib/formatters";
import styles from "./academic-records.module.css";

/**
 * Listado con búsqueda y paginación de una vista de Fichas académicas. Las acciones de la
 * cabecera reciben `refresh` para volver a consultar tras una carga masiva.
 */
export function RecordsPanel<T>({
  title,
  path,
  noun,
  searchLabel,
  columns,
  renderRows,
  emptyText,
  actions,
  filterQuery = "",
  filters,
  detail,
}: {
  title: string;
  path: string;
  /** Singular y plural para el resumen: ["apoderado", "apoderados"]. */
  noun: [string, string];
  searchLabel: string;
  columns: string[];
  renderRows: (items: T[]) => ReactNode;
  emptyText: string;
  actions?: (refresh: () => void) => ReactNode;
  /** Parámetros de consulta de los filtros elegidos. */
  filterQuery?: string;
  filters?: ReactNode;
  detail?: (refresh: () => void) => ReactNode;
}) {
  const [query, setQuery] = useState("");
  const [order, setOrder] = useState("");
  const list = usePaged<T>(path, query, [filterQuery, order].filter(Boolean).join("&"));
  const total = list.data?.totalElements ?? 0;
  return (
    <section className="panel" aria-label={title}>
      <PanelHeading
        title={title}
        summary={
          <>
            {list.data ? countLabel(total, ...noun) : "Cargando…"}
            {list.updatedAt &&
              ` · Última actualización: ${timeLabel(list.updatedAt)}`}
          </>
        }
        actions={
          <>
            <button disabled={list.loading} onClick={list.refresh}>
              Actualizar
            </button>
            {actions?.(list.refresh)}
          </>
        }
      />
      <form
        className="toolbar"
        onSubmit={(event) => {
          event.preventDefault();
          setQuery(
            String(
              new FormData(event.currentTarget).get("search") || "",
            ).trim(),
          );
          list.setPage(0);
          list.refresh();
        }}
      >
        <Field label={searchLabel} name="search" />
        <button disabled={list.loading}>Buscar</button>
      </form>
      <div className="toolbar">
        {filters}
        <RecordOrderSelect value={order} onChange={setOrder} />
      </div>
      {list.loading ? (
        <Loading>Cargando {noun[1]}…</Loading>
      ) : list.error ? (
        <>
          <Feedback error="No se pudo cargar el listado. Comprueba la conexión e inténtalo nuevamente." />
          <div className={styles.retry}>
            <button type="button" onClick={list.refresh}>
              Reintentar carga
            </button>
          </div>
        </>
      ) : list.data?.content.length ? (
        <>
          <div className="user-table-wrap">
            <table
              className="user-table"
              aria-label={`Listado de ${title.toLowerCase()}`}
            >
              <thead>
                <tr>
                  {columns.map((column) => (
                    <th key={column} scope="col">
                      {column}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>{renderRows(list.data.content)}</tbody>
            </table>
          </div>
          <Pager
            data={list.data}
            loading={list.loading}
            setPage={list.setPage}
          />
        </>
      ) : (
        <p className="empty">
          {query || filterQuery
            ? `No se encontraron ${noun[1]} para esta búsqueda.`
            : emptyText}
        </p>
      )}
      {detail?.(list.refresh)}
    </section>
  );
}

/** Botón de una acción que el backend o la pantalla todavía no ofrecen. */
export function PendingAction({
  label,
  hint,
  className,
}: {
  label: string;
  hint: string;
  className?: string;
}) {
  return (
    <button type="button" className={className} disabled title={hint}>
      {label}
      <span className={styles.pendingBadge}>Próximamente</span>
      <span className={styles.srOnly}>: {hint}</span>
    </button>
  );
}
