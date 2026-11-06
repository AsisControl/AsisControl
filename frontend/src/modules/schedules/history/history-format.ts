import type { StatusTone } from "@/shared/ui";
import { hour, weekDayLabels } from "../lib/labels";
import type { ScheduleHistoryEntry, ScheduleHistoryField, WeekDay } from "../types";
/** Texto y color de cada acción que registra la auditoría de Horarios. */
const ACTIONS: Record<string, { label: string; tone: StatusTone }> = {
  CREAR_HORARIO: { label: "Bloque creado", tone: "success" },
  ACTUALIZAR_HORARIO: { label: "Bloque editado", tone: "info" },
  ELIMINAR_HORARIO: { label: "Bloque eliminado", tone: "danger" },
  CREAR_ASIGNACION_CURSO: { label: "Docente asignado", tone: "success" },
  REACTIVAR_ASIGNACION_CURSO: { label: "Asignación reactivada", tone: "success" },
  ACTUALIZAR_ASIGNACION_CURSO: { label: "Asignación editada", tone: "info" },
  RETIRAR_ASIGNACION_CURSO: { label: "Docente retirado", tone: "warning" },
};
export const historyAction = (action: string) => ACTIONS[action];
const FIELD_LABELS: Record<string, string> = {
  curso: "Curso",
  seccion: "Sección",
  docente: "Docente",
  aula: "Aula",
  dia: "Día",
  horaInicio: "Hora de inicio",
  horaFin: "Hora de fin",
  anio: "Año",
  activo: "Estado",
};
export const fieldLabel = (field: string) => FIELD_LABELS[field] ?? field;
/** Valor legible de un campo; "—" si no existía en ese momento. */
export function fieldValue(field: string, value: string | null) {
  if (value === null) return "—";
  if (field === "dia") return weekDayLabels[value as WeekDay] ?? value;
  if (field === "horaInicio" || field === "horaFin") return hour(value);
  if (field === "activo") return value === "true" ? "Activa" : "Retirada";
  return value;
}
const valueOf = (entry: ScheduleHistoryEntry, field: string) => {
  const found = entry.campos.find((item) => item.campo === field);
  return found?.nuevo ?? found?.anterior ?? null;
};
/** "Matemática · 1° A": a qué curso y sección pertenece el cambio. */
export const historySubject = (entry: ScheduleHistoryEntry) =>
  [valueOf(entry, "curso"), valueOf(entry, "seccion")]
    .filter(Boolean)
    .join(" · ");
/** Al editar solo interesa lo que cambió; un alta o baja muestra todos sus valores. */
export const changedFields = (entry: ScheduleHistoryEntry): ScheduleHistoryField[] =>
  entry.campos.filter(({ anterior, nuevo }) => anterior !== nuevo);
