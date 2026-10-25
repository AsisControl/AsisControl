"use client";
import { useEffect, useState } from "react";
import { message } from "@/core/api/client";
import { Dialog, StatusPill } from "@/shared/ui";
import { countLabel } from "@/shared/lib/formatters";
import {
  classroomTypeLabels,
  scheduleSlotLabel,
  sortSchedules,
} from "../lib/labels";
import { PendingBadge } from "../pending/pending-badge";
import type { Classroom, ClassroomSchedule } from "../types";
import styles from "../schedules.module.css";
import { ClassroomStatus } from "./classroom-status";
import { horariosDeAula } from "./services/aulaService";
type Schedules =
  | { state: "loading" }
  | { state: "error"; error: string }
  | { state: "ready"; items: ClassroomSchedule[] };
/** Ventana de solo lectura con los datos del aula y los horarios que la usan. */
export function ClassroomDetailDialog({
  classroom,
  onClose,
  onEdit,
}: {
  classroom: Classroom;
  onClose: () => void;
  /** Sin permiso de escritura no se ofrece la edición. */
  onEdit?: () => void;
}) {
  const [schedules, setSchedules] = useState<Schedules>({ state: "loading" });
  useEffect(() => {
    let active = true;
    horariosDeAula(classroom.id)
      .then((items) => {
        if (active) setSchedules({ state: "ready", items: sortSchedules(items) });
      })
      .catch((e) => {
        if (active) setSchedules({ state: "error", error: message(e) });
      });
    return () => {
      active = false;
    };
  }, [classroom.id]);
  return (
    <Dialog
      title={classroom.nombre}
      description={`Aula · ${classroom.codigo}`}
      size="lg"
      onClose={onClose}
    >
      <dl className="detail-grid">
        <div>
          <dt>Código</dt>
          <dd>{classroom.codigo}</dd>
        </div>
        <div>
          <dt>Tipo de aula</dt>
          <dd>{classroomTypeLabels[classroom.tipoAula] ?? classroom.tipoAula}</dd>
        </div>
        <div>
          <dt>Capacidad</dt>
          <dd>{countLabel(classroom.capacidad, "estudiante", "estudiantes")}</dd>
        </div>
        <div>
          <dt>Estado</dt>
          <dd>
            <ClassroomStatus active={classroom.activo} />
          </dd>
        </div>
        <div>
          <dt>Ubicación / Pabellón</dt>
          <dd>{classroom.ubicacion || "Sin ubicación"}</dd>
        </div>
        <div>
          <dt>
            Equipamiento <PendingBadge feature="classroomEquipment" />
          </dt>
          <dd>—</dd>
        </div>
      </dl>
      <h4>Horarios en esta aula</h4>
      {schedules.state === "loading" ? (
        <p role="status">Cargando horarios…</p>
      ) : schedules.state === "error" ? (
        <p className="form-error" role="alert">
          {schedules.error}
        </p>
      ) : schedules.items.length ? (
        <>
          <p className="hint">
            {countLabel(
              schedules.items.filter((item) => item.activo).length,
              "bloque activo",
              "bloques activos",
            )}
          </p>
          <ul className={styles.scheduleList}>
            {schedules.items.map((item) => (
              <li
                key={item.id}
                className={item.activo ? undefined : styles.scheduleInactive}
              >
                <span>
                  <strong>{scheduleSlotLabel(item)}</strong>
                  <small>
                    {item.curso} · Sección {item.seccion} · {item.docente}
                  </small>
                </span>
                {!item.activo && (
                  <StatusPill state="INACTIVO" label="Inactivo" tone="neutral" />
                )}
              </li>
            ))}
          </ul>
        </>
      ) : (
        <p className="empty">Esta aula aún no se usa en ningún horario.</p>
      )}
      <div className="form-actions">
        <button type="button" onClick={onClose}>
          Cerrar
        </button>
        {onEdit && (
          <button type="button" className="primary-action" onClick={onEdit}>
            Editar aula
          </button>
        )}
      </div>
    </Dialog>
  );
}
