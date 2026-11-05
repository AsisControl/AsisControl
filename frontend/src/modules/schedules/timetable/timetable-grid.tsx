import type { ReactNode } from "react";
import { RowMenu } from "@/shared/ui";
import { hour, weekDayLabels } from "../lib/labels";
import { blocksAt, timeRows, visibleDays } from "../lib/timetable";
import type { ScheduleBlock } from "../types";
import styles from "../schedules.module.css";
/** Matriz semanal: columnas por día y filas por intervalo horario. Sin bloques queda vacía. */
export function TimetableGrid({
  blocks,
  label,
  emptyMessage,
  sectionOf,
  showTeacher = true,
  onOpen,
  onEdit,
  onDelete,
}: {
  blocks: ScheduleBlock[];
  /** Nombre accesible de la tabla. */
  label: string;
  emptyMessage: ReactNode;
  /** Solo en vistas con varias secciones: cada bloque debe indicar la suya. */
  sectionOf?: (block: ScheduleBlock) => string;
  /** En el horario propio del docente su nombre sería redundante en cada bloque. */
  showTeacher?: boolean;
  onOpen: (block: ScheduleBlock) => void;
  /** Sin estas acciones la matriz es de solo lectura. */
  onEdit?: (block: ScheduleBlock) => void;
  onDelete?: (block: ScheduleBlock) => void;
}) {
  const days = visibleDays(blocks);
  const rows = timeRows(blocks);
  return (
    <div className={styles.tableWrap}>
      <table className={styles.timetable} aria-label={label}>
        <thead>
          <tr>
            <th scope="col">Hora</th>
            {days.map((day) => (
              <th key={day} scope="col">
                {weekDayLabels[day]}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {!rows.length && (
            <tr>
              <td colSpan={days.length + 1} className={styles.emptyMatrix}>
                <p className="empty">{emptyMessage}</p>
              </td>
            </tr>
          )}
          {rows.map((row) => (
            <tr key={`${row.inicio}-${row.fin}`}>
              <th scope="row" className={styles.timeCell}>
                {row.inicio} – {row.fin}
              </th>
              {days.map((day) => (
                <td key={day} data-label={weekDayLabels[day]}>
                  {blocksAt(blocks, day, row).map((block) => (
                    <article key={block.id} className={styles.block}>
                      <button
                        type="button"
                        className={styles.blockOpen}
                        aria-label={`Ver detalle de ${block.curso}, ${weekDayLabels[block.diaSemana]} ${hour(block.horaInicio)}`}
                        onClick={() => onOpen(block)}
                      >
                        <strong>{block.curso}</strong>
                        {sectionOf && <small>{sectionOf(block)}</small>}
                        {showTeacher && <small>{block.docente}</small>}
                        <small>{block.aula}</small>
                      </button>
                      {onEdit && onDelete && (
                        <RowMenu
                          itemName={`${block.curso} ${weekDayLabels[block.diaSemana]} ${hour(block.horaInicio)}`}
                          options={[
                            { label: "Editar", onSelect: () => onEdit(block) },
                            {
                              label: "Eliminar",
                              danger: true,
                              onSelect: () => onDelete(block),
                            },
                          ]}
                        />
                      )}
                    </article>
                  ))}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
