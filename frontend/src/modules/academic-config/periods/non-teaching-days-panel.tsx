"use client";
import type { YearPanelProps } from "../types";
/**
 * US-05: días no lectivos del año seleccionado.
 * Pendiente (FE 1 + BE 3): el backend aún no expone endpoints para días no
 * lectivos. Acordar el contrato antes de implementar; mientras tanto, si se
 * necesita avanzar la interfaz, usar un mock temporal en este mismo módulo.
 */
export function NonTeachingDaysPanel({ year }: YearPanelProps) {
  return (
    <>
      <div className="section-heading">
        <h4>Días no lectivos</h4>
      </div>
      <p className="empty">
        La gestión de días no lectivos del año {year.anio} está pendiente de
        implementación.
      </p>
    </>
  );
}
