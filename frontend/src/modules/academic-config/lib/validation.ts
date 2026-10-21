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

export const hasErrors = (errors: PeriodErrors) =>
  Object.values(errors).some(Boolean);
