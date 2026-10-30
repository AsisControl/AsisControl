import { RowMenu } from "@/shared/ui";
import { hour, weekDayLabels } from "../lib/labels";
import { blocksAt, timeRows, visibleDays } from "../lib/timetable";
import type { ScheduleBlock } from "../types";
import styles from "../schedules.module.css";
/** Matriz semanal de una sección: columnas por día y filas por intervalo horario. */
export function TimetableGrid({
  blocks,
  onEdit,
  onDelete,
}: {
  blocks: ScheduleBlock[];
  /** Sin estas acciones la matriz es de solo lectura. */
  onEdit?: (block: ScheduleBlock) => void;
  onDelete?: (block: ScheduleBlock) => void;
}) {
  const days = visibleDays(blocks);
  return (
    <div className={styles.tableWrap}>
      <table className={styles.timetable} aria-label="Horario semanal de la sección">
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
          {timeRows(blocks).map((row) => (
            <tr key={`${row.inicio}-${row.fin}`}>
              <th scope="row" className={styles.timeCell}>
                {row.inicio} – {row.fin}
              </th>
              {days.map((day) => (
                <td key={day} data-label={weekDayLabels[day]}>
                  {blocksAt(blocks, day, row).map((block) => (
                    <article key={block.id} className={styles.block}>
                      <div>
                        <strong>{block.curso}</strong>
                        <small>{block.docente}</small>
                        <small>{block.aula}</small>
                      </div>
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
