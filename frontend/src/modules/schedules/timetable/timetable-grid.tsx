import type { ReactNode } from "react";
import { RowMenu } from "@/shared/ui";
import { hour, weekDayLabels } from "../lib/labels";
import { blockLanes, blocksAt, timeRows, visibleDays } from "../lib/timetable";
import type { ScheduleBlock } from "../types";
import styles from "../schedules.module.css";

const SLOT_HEIGHT = 64;
const minutesOf = (time: string) => {
  const [hours, minutes] = hour(time).split(":").map(Number);
  return hours * 60 + minutes;
};

/** Cuadrícula de media hora: cada tarjeta ocupa la duración real de su clase. */
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
  label: string;
  emptyMessage: ReactNode;
  sectionOf?: (block: ScheduleBlock) => string;
  showTeacher?: boolean;
  onOpen: (block: ScheduleBlock) => void;
  onEdit?: (block: ScheduleBlock) => void;
  onDelete?: (block: ScheduleBlock) => void;
}) {
  const days = visibleDays(blocks);
  const rows = timeRows(blocks);
  const lanes = Object.fromEntries(days.map((day) => [day, blockLanes(blocks, day)]));
  return (
    <div className={styles.timetableWrap}>
      <table className={styles.timetable} aria-label={label}>
        {!blocks.length && <caption className={styles.emptyGridNotice}>{emptyMessage}</caption>}
        <thead>
          <tr>
            <th scope="col">Hora</th>
            {days.map((day) => (
              <th key={day} scope="col">{weekDayLabels[day]}</th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((row) => (
            <tr key={row.inicio}>
              <th scope="row" className={styles.timeCell}>
                {row.inicio} – {row.fin}
              </th>
              {days.map((day) => (
                <td key={day} data-label={weekDayLabels[day]}>
                  {blocksAt(blocks, day, row).map((block) => {
                    const position = lanes[day].get(block.id) ?? { lane: 0, lanes: 1 };
                    const start = minutesOf(block.horaInicio);
                    const duration = minutesOf(block.horaFin) - start;
                    const compact = duration < 60;
                    const name = `${block.curso} ${weekDayLabels[day]} ${hour(block.horaInicio)}`;
                    return (
                      <article
                        key={block.id}
                        className={styles.block}
                        onClick={() => onOpen(block)}
                        data-tone={block.cursoId % 4}
                        data-compact={compact || undefined}
                        title={`${block.curso} · ${hour(block.horaInicio)}–${hour(block.horaFin)} · ${block.docente} · ${block.aula}`}
                        style={{
                          top: (start - minutesOf(row.inicio)) / 30 * SLOT_HEIGHT + 3,
                          height: Math.max(duration / 30 * SLOT_HEIGHT - 6, 48),
                          left: `calc(${position.lane * 100 / position.lanes}% + 3px)`,
                          width: `calc(${100 / position.lanes}% - 6px)`,
                        }}
                      >
                        <button
                          type="button"
                          className={styles.blockOpen}
                          aria-label={`Ver detalle de ${block.curso}, ${weekDayLabels[day]} ${hour(block.horaInicio)}`}
                        >
                          <span className={styles.blockTime}>{hour(block.horaInicio)} – {hour(block.horaFin)}</span>
                          <strong>{block.curso}</strong>
                          {sectionOf && <small>{sectionOf(block)}</small>}
                          {!compact && showTeacher && <small>{block.docente}</small>}
                          {!compact && <small>{block.aula}</small>}
                        </button>
                        {onEdit && onDelete && (
                          <div onClick={(event) => event.stopPropagation()}>
                            <RowMenu
                              itemName={name}
                              options={[
                                { label: "Editar", onSelect: () => onEdit(block) },
                                { label: "Eliminar", danger: true, onSelect: () => onDelete(block) },
                              ]}
                            />
                          </div>
                        )}
                      </article>
                    );
                  })}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
