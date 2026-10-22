/** Validaciones del formulario de período: funciones puras, sin React. */
export const PERIOD_NAME_MAX = 100;

type DateRange = { fechaInicio: string; fechaFin: string };
type ExistingPeriod = DateRange & { nombre: string; orden: number };
export type PeriodInput = {
  nombre: string;
  fechaInicio: string;
  fechaFin: string;
};
export type PeriodErrors = Partial<Record<keyof PeriodInput, string>>;

/** Orden del nuevo período: max(orden) + 1 (1 si aún no hay períodos). */
export function nextPeriodOrder(periods: { orden: number }[]): number {
  return periods.reduce((max, p) => Math.max(max, p.orden), 0) + 1;
}

export function validatePeriodName(
  name: string,
  periods: { nombre: string }[],
): string {
  const value = name.trim();
  if (!value) return "El nombre del período es obligatorio.";
  if (value.length > PERIOD_NAME_MAX)
    return `El nombre no puede superar ${PERIOD_NAME_MAX} caracteres.`;
  const key = value.toLowerCase();
  if (periods.some((p) => p.nombre.trim().toLowerCase() === key))
    return "Ya existe un período con ese nombre.";
  return "";
}

/** Fechas ISO (yyyy-mm-dd): se comparan como texto. */
export function validatePeriodDates(
  start: string,
  end: string,
  year: DateRange,
  periods: ExistingPeriod[],
): Pick<PeriodErrors, "fechaInicio" | "fechaFin"> {
  const errors: Pick<PeriodErrors, "fechaInicio" | "fechaFin"> = {};
  if (!start) errors.fechaInicio = "La fecha de inicio es obligatoria.";
  if (!end) errors.fechaFin = "La fecha de fin es obligatoria.";
  if (!start || !end) return errors;
  if (end < start) {
    errors.fechaFin = "La fecha de fin no puede ser anterior al inicio.";
    return errors;
  }
  if (start < year.fechaInicio || start > year.fechaFin)
    errors.fechaInicio = "La fecha de inicio debe estar dentro del año.";
  if (end < year.fechaInicio || end > year.fechaFin)
    errors.fechaFin = "La fecha de fin debe estar dentro del año.";
  if (errors.fechaInicio || errors.fechaFin) return errors;
  // Mismo criterio inclusivo que el servidor: compartir un día ya es solape.
  const overlapped = periods.find(
    (p) => end >= p.fechaInicio && start <= p.fechaFin,
  );
  if (overlapped)
    errors.fechaInicio = `Las fechas se solapan con ${overlapped.nombre}.`;
  return errors;
}

export function validatePeriod(
  input: PeriodInput,
  year: DateRange,
  periods: ExistingPeriod[],
): PeriodErrors {
  const errors: PeriodErrors = validatePeriodDates(
    input.fechaInicio,
    input.fechaFin,
    year,
    periods,
  );
  const name = validatePeriodName(input.nombre, periods);
  if (name) errors.nombre = name;
  return errors;
}

export const hasErrors = (errors: Partial<Record<string, string>>) =>
  Object.values(errors).some(Boolean);

// Días no lectivos: las usa el formulario de agregar/editar.
export const NON_TEACHING_DESCRIPTION_MAX = 255;

export type NonTeachingDayInput = { fecha: string; descripcion: string };
export type NonTeachingDayErrors = Partial<
  Record<keyof NonTeachingDayInput, string>
>;

/** Fecha ISO obligatoria, dentro del año y que no esté ya en `days`. */
export function validateNonTeachingDate(
  date: string,
  year: DateRange,
  days: { fecha: string }[],
): string {
  if (!date) return "La fecha es obligatoria.";
  if (date < year.fechaInicio || date > year.fechaFin)
    return "La fecha debe estar dentro del año.";
  if (days.some((d) => d.fecha === date))
    return "Ya existe un día no lectivo en esa fecha.";
  return "";
}

/** La descripción es opcional; si existe, máximo 255 caracteres. */
export function validateNonTeachingDescription(description: string): string {
  if (description.trim().length > NON_TEACHING_DESCRIPTION_MAX)
    return `La descripción no puede superar ${NON_TEACHING_DESCRIPTION_MAX} caracteres.`;
  return "";
}

export function validateNonTeachingDay(
  input: NonTeachingDayInput,
  year: DateRange,
  days: { fecha: string }[],
): NonTeachingDayErrors {
  const errors: NonTeachingDayErrors = {};
  const date = validateNonTeachingDate(input.fecha, year, days);
  if (date) errors.fecha = date;
  const description = validateNonTeachingDescription(input.descripcion);
  if (description) errors.descripcion = description;
  return errors;
}
