import { hoursLabel } from "@/shared/lib/formatters";
import type { CourseAssignment, ScheduleBlock } from "../types";
import { hour } from "./labels";

/** OBS-01: 1 hora semanal del plan equivale a 60 minutos de horario, igual que en el backend. */
export const MINUTES_PER_HOUR = 60;

const toMinutes = (time: string) => {
  const [hours, minutes] = hour(time).split(":").map(Number);
  return hours * MINUTES_PER_HOUR + minutes;
};
const duration = (start: string, end: string) => toMinutes(end) - toMinutes(start);

/** Horas de un curso en una sección frente a su plan; todo en minutos. */
export type PlanHours = {
  plan: number;
  /** Bloques vigentes del curso en la sección, sin el que se edita. */
  programmed: number;
  /** Bloques vigentes del curso en la sección, con el que se edita. */
  current: number;
  /** Duración del bloque del formulario; 0 mientras falten horas válidas. */
  added: number;
    /** Horas del curso en la sección si se guarda el formulario. */
  total: number;
  available: number;
  /** Mismo criterio que el servidor: pasa el plan y además sube las horas actuales. */
  exceeds: boolean;
  /** La sección ya excedía el plan (porque se redujo): solo puede mantener o bajar horas. */
  overPlan: boolean;
    /** El cambio del formulario deja una sección excedida dentro del plan. */
  fixes: boolean;
};

/**
 * OBS-01: suma los bloques vigentes del curso en la sección y compara con el plan. Al editar se excluye
 * el propio bloque, así que cambiar aula o día no cuenta dos veces sus horas.
 */
export function planHours(
  assignment: CourseAssignment,
  sectionBlocks: ScheduleBlock[],
  blockId: number | undefined,
  start: string,
  end: string,
): PlanHours | null {
  if (assignment.horasPlan == null) return null;
  const plan = assignment.horasPlan * MINUTES_PER_HOUR;
  const course = sectionBlocks.filter(
    (item) => item.activo && item.cursoId === assignment.cursoId && item.seccionId === assignment.seccionId,
  );
  const current = course.reduce((total, item) => total + duration(item.horaInicio, item.horaFin), 0);
  const programmed = course
    .filter((item) => item.id !== blockId)
    .reduce((total, item) => total + duration(item.horaInicio, item.horaFin), 0);
  const added = start && end && start < end ? duration(start, end) : 0;
  const total = programmed + added;
  return {
    plan,
    programmed,
    current,
    added,
    total,
    available: Math.max(0, plan - programmed),
    exceeds: total > plan && total > current,
    overPlan: current > plan,
    fixes: current > plan && added > 0 && total <= plan,
  };
}

/** OBS-01: curso de la sección cuyo horario vigente supera las horas del plan (en minutos). */
export type ExceededCourse = { cursoId: number; curso: string; current: number; plan: number };

/** OBS-01: cursos de la sección que quedaron por encima del plan, por ejemplo porque el plan se redujo. */
export function exceededCourses(
  assignments: CourseAssignment[],
  sectionBlocks: ScheduleBlock[],
): ExceededCourse[] {
  return assignments.flatMap((assignment) => {
    const hours = planHours(assignment, sectionBlocks, undefined, "", "");
    return hours?.overPlan
      ? [{ cursoId: assignment.cursoId, curso: assignment.curso, current: hours.current, plan: hours.plan }]
      : [];
  });
}

/** Texto del conflicto: lo mismo que explica el servidor al rechazar. */
export const exceedDetail = (hours: PlanHours) =>
  `Este bloque suma ${hoursLabel(hours.added)} y solo hay ${hoursLabel(hours.available)} disponibles ` +
  `(plan ${hoursLabel(hours.plan)}, programadas ${hoursLabel(hours.programmed)}).`;