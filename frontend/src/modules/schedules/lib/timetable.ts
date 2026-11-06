/** Matriz semanal y conflictos de bloques: funciones puras, sin React. El servidor es la autoridad. */
import { ApiError } from "@/core/api/client";
import type { Classroom, CourseAssignment, ScheduleBlock, WeekDay } from "../types";
import { hour, weekDayLabels } from "./labels";
import { isSchoolEndSlot, isSchoolStartSlot } from "./time-slots";
import { SCHOOL_END, SCHOOL_START } from "./time-slots";

export const WEEK_DAYS = Object.keys(weekDayLabels) as WeekDay[];
/** Perspectivas de la matriz (US-24); solo la de sección permite editar bloques. */
export type Perspective = "seccion" | "docente" | "aula" | "curso";
export const PERSPECTIVES: Record<Perspective, string> = {
  seccion: "Sección",
  docente: "Docente",
  aula: "Aula",
  curso: "Curso",
};
export type PerspectiveOption = { id: number; label: string };
/** Quién se puede consultar en cada perspectiva: lo que tiene asignaciones activas en el año. */
export function perspectiveOptions(
  perspective: Exclude<Perspective, "seccion">,
  year: number | null,
  assignments: CourseAssignment[],
  classrooms: Classroom[],
): PerspectiveOption[] {
  if (perspective === "aula")
    return classrooms.map((item) => ({ id: item.id, label: `${item.codigo} · ${item.nombre}` }));
  const options = new Map<number, string>();
  for (const item of assignments.filter((a) => a.anioAcademico === year)) {
    if (perspective === "docente") options.set(item.docenteId, item.docente);
    // Un mismo nombre de curso se repite entre grados.
    else options.set(item.cursoId, `${item.curso} · ${item.grado}° grado`);
  }
  return [...options]
    .map(([id, label]) => ({ id, label }))
    .sort((a, b) => a.label.localeCompare(b.label));
}
/** Lunes a viernes siempre; el sábado solo si tiene bloques. */
export function visibleDays(blocks: ScheduleBlock[]): WeekDay[] {
  const saturday = blocks.some((block) => block.diaSemana === "SABADO");
  return WEEK_DAYS.filter((day) => day !== "SABADO" || saturday);
}

/** Fila fija de media hora de la matriz semanal. */
export type TimeRow = { inicio: string; fin: string };
const minutesOf = (time: string) => {
  const [hours, minutes] = hour(time).split(":").map(Number);
  return hours * 60 + minutes;
};
const timeOf = (minutes: number) =>
  `${String(Math.floor(minutes / 60)).padStart(2, "0")}:${String(minutes % 60).padStart(2, "0")}`;
/** La jornada siempre se ve completa; se extiende si existen bloques antiguos fuera de ella. */
export function timeRows(blocks: ScheduleBlock[]): TimeRow[] {
  const start = Math.min(minutesOf(SCHOOL_START), ...blocks.map((block) =>
    Math.floor(minutesOf(block.horaInicio) / 30) * 30));
  const end = Math.max(minutesOf(SCHOOL_END), ...blocks.map((block) =>
    Math.ceil(minutesOf(block.horaFin) / 30) * 30));
  return Array.from({ length: (end - start) / 30 }, (_, index) => {
    const inicio = start + index * 30;
    return { inicio: timeOf(inicio), fin: timeOf(inicio + 30) };
  });
}
/** Bloques que comienzan dentro de esta fila; su tarjeta se extiende hasta la hora de fin. */
export function blocksAt(blocks: ScheduleBlock[], day: WeekDay, row: TimeRow) {
  return blocks.filter(
    (block) =>
      block.diaSemana === day &&
      hour(block.horaInicio) >= row.inicio &&
      hour(block.horaInicio) < row.fin,
  );
}

/** Da una columna propia a los bloques simultáneos de una misma perspectiva. */
export function blockLanes(blocks: ScheduleBlock[], day: WeekDay) {
  const ordered = blocks.filter((block) => block.diaSemana === day).sort(
    (a, b) => a.horaInicio.localeCompare(b.horaInicio) || a.id - b.id,
  );
  const result = new Map<number, { lane: number; lanes: number }>();
  let active: { fin: string; lane: number }[] = [];
  let cluster: number[] = [];
  let clusterLanes = 0;
  const finishCluster = () => {
    for (const id of cluster) result.set(id, { lane: result.get(id)!.lane, lanes: clusterLanes });
    cluster = [];
    clusterLanes = 0;
  };
  for (const block of ordered) {
    active = active.filter((item) => item.fin > hour(block.horaInicio));
    if (!active.length && cluster.length) finishCluster();
    const occupied = new Set(active.map((item) => item.lane));
    let lane = 0;
    while (occupied.has(lane)) lane++;
    result.set(block.id, { lane, lanes: 1 });
    active.push({ fin: hour(block.horaFin), lane });
    cluster.push(block.id);
    clusterLanes = Math.max(clusterLanes, lane + 1);
  }
  if (cluster.length) finishCluster();
  return result;
}

/** Dos intervalos se cruzan si uno empieza antes de que termine el otro. */
export const overlaps = (
  a: { inicio: string; fin: string },
  b: { inicio: string; fin: string },
) => a.inicio < b.fin && b.inicio < a.fin;

export type ConflictKind =
  | "rango"
  | "aforo"
  | "docente"
  | "aula"
  | "seccion"
  | "version"
  | "inhabilitado"
  | "otro";
export type Conflict = { kind: ConflictKind; title: string; detail: string };
const titles: Record<ConflictKind, string> = {
  rango: "Horario inválido",
  aforo: "Aforo insuficiente",
  docente: "Cruce de docente",
  aula: "Aula ocupada",
  seccion: "Cruce de sección",
  version: "Bloque modificado por otro usuario",
  inhabilitado: "Recurso no disponible",
  otro: "No se pudo guardar el bloque",
};
const conflict = (kind: ConflictKind, detail: string): Conflict => ({
  kind,
  title: titles[kind],
  detail,
});
/** "Matemática · Sección A · Lunes 08:00 – 09:30". */
const describe = (block: ScheduleBlock) =>
  `${block.curso} · Sección ${block.seccion} · ${weekDayLabels[block.diaSemana]} ${hour(block.horaInicio)} – ${hour(block.horaFin)}`;

/** Bloque que se está preparando, con los datos que deciden los cruces. */
export type DraftBlock = {
  id?: number;
  diaSemana: WeekDay;
  horaInicio: string;
  horaFin: string;
  aulaId: number;
  docenteId: number;
  seccionId: number;
  capacidadSeccion: number;
  capacidadAula: number;
};
/**
 * Recalcula los conflictos con los bloques conocidos (de la sección, del docente
 * y del aula). Mismo orden de prioridad que el servidor: aula, docente, sección.
 */
export function detectConflicts(
  draft: DraftBlock,
  blocks: ScheduleBlock[],
): Conflict[] {
  const found: Conflict[] = [];
  if (draft.horaInicio >= draft.horaFin) {
    found.push(conflict("rango", "La hora de inicio debe ser anterior a la hora de fin."));
    return found;
  }
  if (draft.capacidadAula < draft.capacidadSeccion)
    found.push(
      conflict(
        "aforo",
        `El aula admite ${draft.capacidadAula} estudiantes y la sección tiene una capacidad máxima de ${draft.capacidadSeccion}.`,
      ),
    );
  const seen = new Set<number>();
  for (const block of blocks) {
    if (seen.has(block.id) || block.id === draft.id || !block.activo) continue;
    seen.add(block.id);
    if (
      block.diaSemana !== draft.diaSemana ||
      !overlaps(
        { inicio: draft.horaInicio, fin: draft.horaFin },
        { inicio: hour(block.horaInicio), fin: hour(block.horaFin) },
      )
    )
      continue;
    if (block.aulaId === draft.aulaId)
      found.push(conflict("aula", `${block.aula} ya está ocupada: ${describe(block)}.`));
    else if (block.docenteId === draft.docenteId)
      found.push(conflict("docente", `${block.docente} ya dicta ${describe(block)}.`));
    else if (block.seccionId === draft.seccionId)
      found.push(conflict("seccion", `La sección ya tiene ${describe(block)}.`));
  }
  return found;
}

/** Códigos de rechazo de /api/horarios. */
const serverKinds: Record<string, ConflictKind> = {
  HORARIO_RANGO_INVALIDO: "rango",
  HORARIO_INICIO_INVALIDO: "rango",
  HORARIO_FIN_INVALIDO: "rango",
  AULA_CAPACIDAD_INSUFICIENTE: "aforo",
  HORARIO_DOCENTE_OCUPADO: "docente",
  HORARIO_AULA_OCUPADA: "aula",
  HORARIO_SECCION_OCUPADA: "seccion",
  VERSION_DESACTUALIZADA: "version",
  AULA_NO_ENCONTRADA: "inhabilitado",
  ASIGNACION_CURSO_NO_ENCONTRADA: "inhabilitado",
  HORARIO_NO_ENCONTRADO: "inhabilitado",
};
/** Conflicto que devolvió el servidor al confirmar; null si el error no es de negocio. */
export function serverConflict(error: unknown): Conflict | null {
  if (!(error instanceof ApiError) || error.status === 403 || error.status === 401)
    return null;
  return conflict(serverKinds[error.code] ?? "otro", error.message);
}
/** Conflictos que dejan desactualizada la matriz: hay que volver a consultarla. */
export const needsReload = (item: Conflict) =>
  item.kind === "version" ||
  item.kind === "inhabilitado" ||
  item.kind === "docente" ||
  item.kind === "aula" ||
  item.kind === "seccion";

/** Valores del formulario tal como se escriben. */
export type BlockFormValues = {
  asignacionCursoId: string;
  aulaId: string;
  diaSemana: string;
  horaInicio: string;
  horaFin: string;
};
export type BlockErrors = Partial<Record<keyof BlockFormValues, string>>;
export function validateBlock(values: BlockFormValues): BlockErrors {
  const errors: BlockErrors = {};
  if (!values.asignacionCursoId) errors.asignacionCursoId = "Seleccione la asignación docente.";
  if (!values.aulaId) errors.aulaId = "Seleccione el aula.";
  if (!(WEEK_DAYS as string[]).includes(values.diaSemana))
    errors.diaSemana = "Seleccione el día.";
  if (!values.horaInicio) errors.horaInicio = "Indique la hora de inicio.";
  else if (!isSchoolStartSlot(values.horaInicio))
    errors.horaInicio = "Elija una hora entre 07:00 y 19:30, en pasos de 30 minutos.";
  if (!values.horaFin) errors.horaFin = "Indique la hora de fin.";
  else if (!isSchoolEndSlot(values.horaFin))
    errors.horaFin = "Elija una hora entre 07:30 y 20:00, en pasos de 30 minutos.";
  else if (values.horaInicio && values.horaInicio >= values.horaFin)
    errors.horaFin = "Debe ser posterior a la hora de inicio.";
  return errors;
}
