"use client";
import { useState, type FormEvent } from "react";
import { api, message } from "@/core/api/client";
import { FormDialog, ReasonField } from "@/shared/ui";
import { stateLabel } from "@/shared/lib/formatters";
import type { AcademicYear, Period, YearPanelProps } from "../types";
import styles from "../academic-config.module.css";
import { PeriodCard } from "./period-card";
type PeriodAction = { kind: "period-state"; target: Period; next: string };
/** US-05: períodos del año académico seleccionado. */
export function PeriodsPanel({
  year,
  onYearChange,
  onSuccess,
}: YearPanelProps) {
  const [action, setAction] = useState<PeriodAction | null>(null);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const open = (value: PeriodAction) => {
    setError("");
    onSuccess("");
    setAction(value);
  };
  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!action) return;
    const form = new FormData(event.currentTarget);
    const str = (key: string) => String(form.get(key) || "").trim();
    setBusy(true);
    setError("");
    try {
      const result = await api<AcademicYear>(
        `/api/configuracion-academica/periodos/${action.target.id}/estado`,
        {
          method: "PUT",
          body: JSON.stringify({
            estado: action.next,
            version: action.target.version,
            motivo: str("motivo"),
          }),
        },
      );
      onYearChange(result);
      setAction(null);
      onSuccess("Configuración guardada.");
    } catch (error) {
      setError(message(error));
    } finally {
      setBusy(false);
    }
  }
  return (
    <>
      <div className="section-heading">
        <h4>Períodos</h4>
      </div>
      <div className={styles.cards}>
        {year.periodos.map((period) => (
          <PeriodCard
            key={period.id}
            period={period}
            canChangeState={year.estado === "ACTIVO"}
            onChangeState={(target, next) =>
              open({ kind: "period-state", target, next })
            }
          />
        ))}
      </div>
      {!year.periodos.length && (
        <p className="empty">
          Sin períodos configurados. Sus fechas y cantidad se definen aquí.
        </p>
      )}
      {action && (
        <FormDialog
          title="Cambiar estado del período"
          busy={busy}
          error={error}
          onClose={() => setAction(null)}
          onSubmit={save}
          notes={
            <p>
              Nuevo estado: <strong>{stateLabel(action.next)}</strong>. Los
              estados cerrados no se reabren mediante este flujo.
            </p>
          }
        >
          <ReasonField />
        </FormDialog>
      )}
    </>
  );
}
