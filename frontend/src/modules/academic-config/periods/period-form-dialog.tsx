"use client";
import { useRef, useState, type FormEvent } from "react";
import { api, message } from "@/core/api/client";
import { Dialog, Feedback } from "@/shared/ui";
import { stateLabel } from "@/shared/lib/formatters";
import {
  PERIOD_NAME_MAX,
  hasErrors,
  nextPeriodOrder,
  validatePeriod,
  type PeriodErrors,
} from "../lib/validation";
import styles from "../academic-config.module.css";
import type { AcademicYear } from "../types";
type Props = {
  year: AcademicYear;
  onClose: () => void;
  /** Recibe el año actualizado que devuelve el backend. */
  onCreated: (year: AcademicYear) => void;
};
/** US-05: modal "Nuevo período". El backend siempre lo crea PLANIFICADO. */
export function PeriodFormDialog({ year, onClose, onCreated }: Props) {
  const [values, setValues] = useState({
    nombre: "",
    fechaInicio: "",
    fechaFin: "",
  });
  const [errors, setErrors] = useState<PeriodErrors>({});
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
    const found = validatePeriod(values, year, year.periodos);
    setErrors(found);
    if (hasErrors(found)) return;
    sending.current = true;
    setBusy(true);
    setServerError("");
    try {
      const updated = await api<AcademicYear>(
        `/api/configuracion-academica/anios/${year.anio}/periodos`,
        {
          method: "POST",
          body: JSON.stringify({
            nombre: values.nombre.trim(),
            orden: nextPeriodOrder(year.periodos),
            fechaInicio: values.fechaInicio,
            fechaFin: values.fechaFin,
          }),
        },
      );
      onCreated(updated);
    } catch (error) {
      setServerError(message(error));
      sending.current = false;
      setBusy(false);
    }
  }
  return (
    <Dialog title="Nuevo período" onClose={onClose} busy={busy}>
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
        <label className={styles.label}>
          Estado inicial <span className={styles.required}>*</span>
          <select className={styles.input} value="PLANIFICADO" disabled>
            <option value="PLANIFICADO">{stateLabel("PLANIFICADO")}</option>
          </select>
        </label>
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
            {busy ? "Creando…" : "Crear período"}
          </button>
        </div>
      </form>
    </Dialog>
  );
}
