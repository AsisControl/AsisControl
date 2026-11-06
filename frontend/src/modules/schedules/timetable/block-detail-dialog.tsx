import { Dialog } from "@/shared/ui";
import { hour, weekDayLabels } from "../lib/labels";
import type { ScheduleBlock } from "../types";
/** Detalle de un bloque de la matriz: curso, sección, docente, aula, día y horas. */
export function BlockDetailDialog({
  block,
  section,
  onClose,
}: {
  block: ScheduleBlock;
  /** Sección con grado y año; el bloque solo trae el nombre ("A"). */
  section: string;
  onClose: () => void;
}) {
  return (
    <Dialog title="Detalle del bloque" description={block.curso} onClose={onClose}>
      <dl className="detail-grid">
        <div>
          <dt>Curso</dt>
          <dd>{block.curso}</dd>
        </div>
        <div>
          <dt>Sección</dt>
          <dd>{section}</dd>
        </div>
        <div>
          <dt>Docente responsable</dt>
          <dd>{block.docente}</dd>
        </div>
        <div>
          <dt>Aula</dt>
          <dd>{block.aula}</dd>
        </div>
        <div>
          <dt>Día</dt>
          <dd>{weekDayLabels[block.diaSemana] ?? block.diaSemana}</dd>
        </div>
        <div>
          <dt>Hora de inicio</dt>
          <dd>{hour(block.horaInicio)}</dd>
        </div>
        <div>
          <dt>Hora de fin</dt>
          <dd>{hour(block.horaFin)}</dd>
        </div>
      </dl>
      <div className="form-actions">
        <button type="button" onClick={onClose}>
          Cerrar
        </button>
      </div>
    </Dialog>
  );
}
