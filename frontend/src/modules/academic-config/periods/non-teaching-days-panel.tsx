"use client";
import { useCallback, useId, useState } from "react";
import { api, message } from "@/core/api/client";
import { Feedback } from "@/shared/ui";
import { formatDate } from "../lib/format-date";
import { PendingBadge } from "../pending/pending-badge";
import styles from "../academic-config.module.css";
import type { NonTeachingDay, YearPanelProps } from "../types";
import { NonTeachingDayFormDialog } from "./non-teaching-day-form-dialog";
const byDate = (a: NonTeachingDay, b: NonTeachingDay) =>
  a.fecha.localeCompare(b.fecha);
/** US-05: días no lectivos del año seleccionado (GET/POST/PATCH reales). */
export function NonTeachingDaysPanel({ year, editable }: YearPanelProps) {
  const [expanded, setExpanded] = useState(false);
  const [days, setDays] = useState<NonTeachingDay[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");
  const [adding, setAdding] = useState(false);
  const [editing, setEditing] = useState<NonTeachingDay | null>(null);
  const bodyId = useId();
  const anio = year.anio;
  const load = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const list = await api<NonTeachingDay[]>(
        `/api/configuracion-academica/anios/${anio}/dias-no-lectivos`,
      );
      setDays([...list].sort(byDate));
    } catch (e) {
      setError(message(e));
    } finally {
      setLoading(false);
    }
  }, [anio]);
  // Al cambiar de año el padre remonta el panel (key), así que la lista se
  // consulta al expandir y no queda nada del año anterior.
  function toggle() {
    if (!expanded) void load();
    setExpanded(!expanded);
  }
  async function saved(text: string) {
    setAdding(false);
    setEditing(null);
    setSuccess(text);
    await load();
  }
  async function failedEdit(reason: string) {
    // Con versión vieja (u otro cambio) el diálogo ya no sirve: se cierra,
    // se refresca la lista y se avisa.
    setEditing(null);
    setSuccess("");
    await load();
    setError(`${reason} Se recargó la lista.`);
  }
  return (
    <section className={styles.collapsible}>
      <button
        type="button"
        className={styles.collapsibleToggle}
        aria-expanded={expanded}
        aria-controls={bodyId}
        onClick={toggle}
      >
        <span className={styles.collapsibleTitle}>Días no lectivos</span>
        <span className={styles.chevron} aria-hidden="true">
          {expanded ? "▲" : "▼"}
        </span>
      </button>
      {expanded && (
        <div id={bodyId} className={styles.collapsibleBody}>
          {editable ? (
            <div className={styles.toolbar}>
              <button
                type="button"
                disabled={loading}
                onClick={() => {
                  setSuccess("");
                  setAdding(true);
                }}
              >
                Agregar día no lectivo
              </button>
            </div>
          ) : (
            <p className="hint">
              Año cerrado: los días no lectivos son de solo lectura.
            </p>
          )}
          <Feedback error={error} success={success} />
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
                          <button
                            type="button"
                            onClick={() => {
                              setSuccess("");
                              setEditing(day);
                            }}
                          >
                            Editar descripción
                          </button>
                          <button type="button" disabled>
                            Eliminar
                          </button>
                          <PendingBadge feature="deleteNonTeachingDay" />
                        </div>
                      )}
                    </td>
                  </tr>
                ))}
                {loading && !days.length && (
                  <tr>
                    <td colSpan={3} className={styles.emptyCell}>
                      Cargando días no lectivos…
                    </td>
                  </tr>
                )}
                {!loading && !error && !days.length && (
                  <tr>
                    <td colSpan={3} className={styles.emptyCell}>
                      Sin días no lectivos
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        </div>
      )}
      {editable && adding && (
        <NonTeachingDayFormDialog
          year={year}
          days={days}
          onClose={() => setAdding(false)}
          onSaved={() => void saved("Día no lectivo agregado.")}
        />
      )}
      {editable && editing && (
        <NonTeachingDayFormDialog
          year={year}
          days={days}
          day={editing}
          onClose={() => setEditing(null)}
          onSaved={() => void saved("Descripción actualizada.")}
          onFailed={(reason) => void failedEdit(reason)}
        />
      )}
    </section>
  );
}
