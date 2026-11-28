"use client";
import { useRef, useState, type FormEvent } from "react";
import { ApiError, api, message } from "@/core/api/client";
import { Dialog, Feedback } from "@/shared/ui";
import { stateLabel } from "@/shared/lib/formatters";
import {
  PERIOD_NAME_MAX,
  SECTION_REASON_MAX,
  hasErrors,
  nextPeriodOrder,
  validatePeriod,
  validateSectionReason,
  type PeriodErrors,
} from "../lib/validation";
import styles from "../academic-config.module.css";
import type { AcademicYear, Period } from "../types";
type Props = {
  year: AcademicYear;
  /** Si se indica, se editan su nombre y sus fechas (el orden no cambia). */
  period?: Period;
  onClose: () => void;
  /** Recibe el año actualizado que devuelve el backend. */
  onSaved: (year: AcademicYear) => void;
  /** Se llama si el período editado cambió en el servidor (versión vieja). */
  onStale?: (reason: string) => void;
};
/**
 * US-05: modal "Nuevo período" (el backend siempre lo crea PLANIFICADO) y
 * edición. Al editar, el formulario solo revisa los campos y el rango de fechas;
 * solapes, nombres repetidos y demás reglas las responde el servidor.
 */
export function PeriodFormDialog({
  year,
  period,
  onClose,
  onSaved,
  onStale,
}: Props) {
  const [values, setValues] = useState({
    nombre: period?.nombre ?? "",
    fechaInicio: period?.fechaInicio ?? "",
    fechaFin: period?.fechaFin ?? "",
    motivo: "",
  });
  const [errors, setErrors] = useState<PeriodErrors & { motivo?: string }>({});
  const [serverError, setServerError] = useState("");
  const [busy, setBusy] = useState(false);
  const sending = useRef(false);
  const set = (key: keyof typeof values) => (value: string) => {
    setValues((current) => ({ ...current, [key]: value }));
    setErrors((current) => ({ ...current, [key]: undefined }));
  };
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (sending.current) return;
    const found: PeriodErrors & { motivo?: string } = validatePeriod(
      values,
      year,
      period ? [] : year.periodos,
    );
    if (period) {
      const reason = validateSectionReason(values.motivo, true);
      if (reason) found.motivo = reason;
    }
    setErrors(found);
    if (hasErrors(found)) return;
    sending.current = true;
    setBusy(true);
    setServerError("");
    const data = {
      nombre: values.nombre.trim(),
      fechaInicio: values.fechaInicio,
      fechaFin: values.fechaFin,
    };
    try {
      const updated = await api<AcademicYear>(
        period
          ? `/api/configuracion-academica/periodos/${period.id}`
          : `/api/configuracion-academica/anios/${year.anio}/periodos`,
        {
          method: period ? "PUT" : "POST",
          body: JSON.stringify(
            period
              ? {
                  ...data,
                  version: period.version,
                  motivo: values.motivo.trim(),
                }
              : { ...data, orden: nextPeriodOrder(year.periodos) },
          ),
        },
      );
      onSaved(updated);
    } catch (error) {
      if (
        onStale &&
        error instanceof ApiError &&
        error.code === "VERSION_DESACTUALIZADA"
      ) {
        onStale(message(error));
        return;
      }
      setServerError(message(error));
      sending.current = false;
      setBusy(false);
    }
  }
  return (
    <Dialog
      title={period ? "Editar período" : "Nuevo período"}
      onClose={onClose}
      busy={busy}
    >
      <form className={styles.dialogForm} onSubmit={submit} noValidate>
        <Feedback error={serverError} />
        <label className={styles.label}>
          Nombre del período <span className={styles.required}>*</span>
          <input
            className={styles.input}
            value={values.nombre}
            maxLength={PERIOD_NAME_MAX + 20}
            placeholder="Ej. Período IV"
            disabled={busy}
            aria-invalid={!!errors.nombre}
            onChange={(e) => set("nombre")(e.target.value)}
          />
          {errors.nombre && (
            <span className={styles.fieldError} role="alert">
              {errors.nombre}
            </span>
          )}
        </label>
        <div className={styles.twoColumns}>
          <label className={styles.label}>
            Fecha de inicio <span className={styles.required}>*</span>
            <input
              className={styles.input}
              type="date"
              min={year.fechaInicio}
              max={year.fechaFin}
              value={values.fechaInicio}
              disabled={busy}
              aria-invalid={!!errors.fechaInicio}
              onChange={(e) => set("fechaInicio")(e.target.value)}
            />
            {errors.fechaInicio && (
              <span className={styles.fieldError} role="alert">
                {errors.fechaInicio}
              </span>
            )}
          </label>
          <label className={styles.label}>
            Fecha de fin <span className={styles.required}>*</span>
            <input
              className={styles.input}
              type="date"
              min={year.fechaInicio}
              max={year.fechaFin}
              value={values.fechaFin}
              disabled={busy}
              aria-invalid={!!errors.fechaFin}
              onChange={(e) => set("fechaFin")(e.target.value)}
            />
            {errors.fechaFin && (
              <span className={styles.fieldError} role="alert">
                {errors.fechaFin}
              </span>
            )}
          </label>
        </div>
        {period ? (
          <label className={styles.label}>
            Motivo del cambio <span className={styles.required}>*</span>
            <textarea
              className={styles.input}
              rows={3}
              value={values.motivo}
              maxLength={SECTION_REASON_MAX + 20}
              disabled={busy}
              aria-invalid={!!errors.motivo}
              onChange={(e) => set("motivo")(e.target.value)}
            />
            {errors.motivo && (
              <span className={styles.fieldError} role="alert">
                {errors.motivo}
              </span>
            )}
          </label>
        ) : (
          <label className={styles.label}>
            Estado inicial <span className={styles.required}>*</span>
            <select className={styles.input} value="PLANIFICADO" disabled>
              <option value="PLANIFICADO">{stateLabel("PLANIFICADO")}</option>
            </select>
          </label>
        )}
        <div className={styles.dialogActions}>
          <button
            type="button"
            className={styles.cancelButton}
            disabled={busy}
            onClick={onClose}
          >
            Cancelar
          </button>
          <button className={styles.submitButton} disabled={busy}>
            {period
              ? busy
                ? "Guardando…"
                : "Guardar cambios"
              : busy
                ? "Creando…"
                : "Crear período"}
          </button>
        </div>
      </form>
    </Dialog>
  );
}
