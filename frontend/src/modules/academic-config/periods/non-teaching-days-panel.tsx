"use client";
import { useId, useState } from "react";
import { formatDate } from "../lib/format-date";
import { PendingBadge } from "../pending/pending-badge";
import styles from "../academic-config.module.css";
import type { NonTeachingDay, YearPanelProps } from "../types";
import { NonTeachingDayPreviewDialog } from "./non-teaching-day-preview-dialog";
/**
 * Sin fuente de datos hasta que la API de días no lectivos (rama de BE 3)
 * llegue a main. Al conectarla, esta lista sale del GET del año.
 */
const days: NonTeachingDay[] = [];
/** US-05: días no lectivos del año seleccionado (solo interfaz, sin API). */
export function NonTeachingDaysPanel({ year, editable }: YearPanelProps) {
  const [expanded, setExpanded] = useState(false);
  const [previewOpen, setPreviewOpen] = useState(false);
  const bodyId = useId();
  return (
    <section className={styles.collapsible}>
      <button
        type="button"
        className={styles.collapsibleToggle}
        aria-expanded={expanded}
        aria-controls={bodyId}
        onClick={() => setExpanded((value) => !value)}
      >
        <span className={styles.collapsibleTitle}>
          Días no lectivos
          <PendingBadge feature="nonTeachingDays" />
        </span>
        <span className={styles.chevron} aria-hidden="true">
          {expanded ? "▲" : "▼"}
        </span>
      </button>
      {expanded && (
        <div id={bodyId} className={styles.collapsibleBody}>
          {editable ? (
            <div className={styles.toolbar}>
              <button type="button" disabled>
                Agregar día no lectivo
              </button>
              <PendingBadge feature="addNonTeachingDay" />
              <button
                type="button"
                className={styles.linkButton}
                onClick={() => setPreviewOpen(true)}
              >
                Ver vista previa del formulario
              </button>
            </div>
          ) : (
            <p className="hint">
              Año cerrado: los días no lectivos son de solo lectura.
            </p>
          )}
          <div className={styles.tableWrap}>
            <table className={styles.table}>
              <thead>
                <tr>
                  <th scope="col">Fecha</th>
                  <th scope="col">Descripción</th>
                  <th scope="col">Acciones</th>
                </tr>
              </thead>
              <tbody>
                {days.map((day) => (
                  <tr key={day.id}>
                    <td>{formatDate(day.fecha)}</td>
                    <td>{day.descripcion || "—"}</td>
                    <td>
                      {editable && (
                        <div className={styles.rowActions}>
                          <button type="button" disabled>
                            Editar descripción
                          </button>
                          <PendingBadge feature="editNonTeachingDay" />
                          <button type="button" disabled>
                            Eliminar
                          </button>
                          <PendingBadge feature="deleteNonTeachingDay" />
                        </div>
                      )}
                    </td>
                  </tr>
                ))}
                {!days.length && (
                  <tr>
                    <td colSpan={3} className={styles.emptyCell}>
                      Función pendiente de backend: los días no lectivos del año{" "}
                      {year.anio} no se pueden consultar todavía.
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        </div>
      )}
      {previewOpen && editable && (
        <NonTeachingDayPreviewDialog
          year={year}
          onClose={() => setPreviewOpen(false)}
        />
      )}
    </section>
  );
}
