import type { ClassroomSchedule, ClassroomType, WeekDay } from "../types";
export const classroomTypeLabels: Record<ClassroomType, string> = {
  AULA_REGULAR: "Aula regular",
  LABORATORIO_COMPUTO: "Laboratorio de cómputo",
  LABORATORIO_CIENCIAS: "Laboratorio de ciencias",
  TALLER: "Taller",
  BIBLIOTECA: "Biblioteca",
  SALA_USOS_MULTIPLES: "Sala de usos múltiples",
};
const weekDayLabels: Record<WeekDay, string> = {
  LUNES: "Lunes",
  MARTES: "Martes",
  MIERCOLES: "Miércoles",
  JUEVES: "Jueves",
  VIERNES: "Viernes",
  SABADO: "Sábado",
};
const weekDayOrder = Object.keys(weekDayLabels);
/** "08:00:00" → "08:00". */
const hour = (time: string) => time.slice(0, 5);
/** "Lunes 08:00 – 09:30". */
export const scheduleSlotLabel = (item: ClassroomSchedule) =>
  `${weekDayLabels[item.diaSemana] ?? item.diaSemana} ${hour(item.horaInicio)} – ${hour(item.horaFin)}`;
/** Activos primero; luego por día de la semana y hora de inicio. */
export function sortSchedules(items: ClassroomSchedule[]) {
  return [...items].sort(
    (a, b) =>
      Number(b.activo) - Number(a.activo) ||
      weekDayOrder.indexOf(a.diaSemana) - weekDayOrder.indexOf(b.diaSemana) ||
      a.horaInicio.localeCompare(b.horaInicio),
  );
}
