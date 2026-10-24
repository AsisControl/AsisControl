import type { Section } from "../types";
type Counts = Pick<Section, "capacidadMaxima" | "ocupacion">;
/** Número finito y no negativo; un dato faltante o inválido cuenta como 0. */
const count = (value: unknown) =>
  typeof value === "number" && Number.isFinite(value) && value > 0 ? value : 0;
/** Porcentaje de ocupación (0 si la capacidad es 0 o falta un dato), sin tope en 100. */
export const occupancyPercent = ({ ocupacion, capacidadMaxima }: Counts) =>
  count(capacidadMaxima) > 0
    ? Math.round((count(ocupacion) / count(capacidadMaxima)) * 100)
    : 0;
/** Ancho visual de la barra: el porcentaje limitado a 100. */
export const occupancyBarWidth = (counts: Counts) =>
  Math.min(100, occupancyPercent(counts));
export const isOverCapacity = ({ ocupacion, capacidadMaxima }: Counts) =>
  count(ocupacion) > count(capacidadMaxima);
/** Vacantes que informa el backend; si falta, se calcula sin bajar de 0. */
export const vacancies = (
  section: Counts & Partial<Pick<Section, "vacantes">>,
) =>
  typeof section.vacantes === "number" && Number.isFinite(section.vacantes)
    ? Math.max(0, section.vacantes)
    : Math.max(0, count(section.capacidadMaxima) - count(section.ocupacion));
