/** Matriz semanal y conflictos de bloques: funciones puras, sin React. El servidor es la autoridad. */
import { ApiError } from "@/core/api/client";
import type { ScheduleBlock, WeekDay } from "../types";
import { hour, weekDayLabels } from "./labels";

export const WEEK_DAYS = Object.keys(weekDayLabels) as WeekDay[];
/** Lunes a viernes siempre; el sábado solo si tiene bloques. */
export function visibleDays(blocks: ScheduleBlock[]): WeekDay[] {
  const saturday = blocks.some((block) => block.diaSemana === "SABADO");
  return WEEK_DAYS.filter((day) => day !== "SABADO" || saturday);
}

/** Fila de la matriz: un intervalo "08:00"–"09:30". */
export type TimeRow = { inicio: string; fin: string };
/** Intervalos distintos de los bloques, por hora de inicio y luego de fin. */
export function timeRows(blocks: ScheduleBlock[]): TimeRow[] {
  const rows = new Map<string, TimeRow>();
  for (const block of blocks) {
    const row = { inicio: hour(block.horaInicio), fin: hour(block.horaFin) };
    rows.set(`${row.inicio}-${row.fin}`, row);
  }
  return [...rows.values()].sort(
    (a, b) => a.inicio.localeCompare(b.inicio) || a.fin.localeCompare(b.fin),
  );
}
export function blocksAt(blocks: ScheduleBlock[], day: WeekDay, row: TimeRow) {
  return blocks.filter(
    (block) =>
      block.diaSemana === day &&
      hour(block.horaInicio) === row.inicio &&
      hour(block.horaFin) === row.fin,
  );
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
  if (!values.asignacionCursoId) errors.asignacionCursoId = "Seleccione el curso.";
  if (!values.aulaId) errors.aulaId = "Seleccione el aula.";
  if (!(WEEK_DAYS as string[]).includes(values.diaSemana))
    errors.diaSemana = "Seleccione el día.";
  if (!values.horaInicio) errors.horaInicio = "Indique la hora de inicio.";
  if (!values.horaFin) errors.horaFin = "Indique la hora de fin.";
  else if (values.horaInicio && values.horaInicio >= values.horaFin)
    errors.horaFin = "Debe ser posterior a la hora de inicio.";
  return errors;
}
