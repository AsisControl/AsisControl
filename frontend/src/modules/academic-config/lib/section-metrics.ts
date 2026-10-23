import type { Section } from "../types";
type Counts = Pick<Section, "capacidadMaxima" | "ocupacion">;
/** Porcentaje de ocupación (0 si la capacidad es 0), sin tope en 100. */
export const occupancyPercent = ({ ocupacion, capacidadMaxima }: Counts) =>
  capacidadMaxima > 0 ? Math.round((ocupacion / capacidadMaxima) * 100) : 0;
export const isOverCapacity = ({ ocupacion, capacidadMaxima }: Counts) =>
  ocupacion > capacidadMaxima;
