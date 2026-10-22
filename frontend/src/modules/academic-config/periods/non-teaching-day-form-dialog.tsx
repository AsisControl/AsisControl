"use client";
import { useRef, useState, type FormEvent } from "react";
import { api, message } from "@/core/api/client";
import { Dialog, Feedback } from "@/shared/ui";
import { formatDate } from "../lib/format-date";
import {
  NON_TEACHING_DESCRIPTION_MAX,
  hasErrors,
  validateNonTeachingDay,
  validateNonTeachingDescription,
  type NonTeachingDayErrors,
} from "../lib/validation";
import styles from "../academic-config.module.css";
import type { AcademicYear, NonTeachingDay } from "../types";
type Props = {
  year: AcademicYear;
  /** Días ya cargados: sirven para detectar fechas repetidas. */
  days: NonTeachingDay[];
  /** Si se indica, el formulario edita solo la descripción de ese día. */
  day?: NonTeachingDay;
  onClose: () => void;
  /** Se llama tras guardar; el panel recarga la lista. */
  onSaved: () => void;
  /** Se llama si el PATCH falla: la lista pudo cambiar (p. ej. versión vieja). */
  onFailed?: (reason: string) => void;
};
/** US-05: agregar un día no lectivo o editar su descripción (la fecha no cambia). */
export function NonTeachingDayFormDialog({
  year,
  days,
  day,
  onClose,
  onSaved,
  onFailed,
}: Props) {
  const [values, setValues] = useState({
    fecha: day?.fecha ?? "",
    descripcion: day?.descripcion ?? "",
  });
  const [errors, setErrors] = useState<NonTeachingDayErrors>({});
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
    const found: NonTeachingDayErrors = day
      ? (() => {
          const description = validateNonTeachingDescription(values.descripcion);
          return description ? { descripcion: description } : {};
        })()
      : validateNonTeachingDay(values, year, days);
    setErrors(found);
    if (hasErrors(found)) return;
    sending.current = true;
    setBusy(true);
    setServerError("");
    const descripcion = values.descripcion.trim();
    try {
      if (day) {
        await api<NonTeachingDay>(
          `/api/configuracion-academica/dias-no-lectivos/${day.id}`,
          {
            method: "PATCH",
            body: JSON.stringify({ descripcion, version: day.version }),
          },
        );
      } else {
        await api<NonTeachingDay>(
          `/api/configuracion-academica/anios/${year.anio}/dias-no-lectivos`,
          {
            method: "POST",
            body: JSON.stringify({
              fecha: values.fecha,
              ...(descripcion && { descripcion }),
            }),
          },
        );
      }
      onSaved();
    } catch (error) {
      if (day && onFailed) {
        onFailed(message(error));
        return;
      }
      setServerError(message(error));
      sending.current = false;
      setBusy(false);
    }
  }
  return (
    <Dialog
      title={day ? "Editar descripción" : "Agregar día no lectivo"}
      onClose={onClose}
      busy={busy}
    >
      <form className={styles.dialogForm} onSubmit={submit} noValidate>
        <Feedback error={serverError} />
        {day ? (
          <p>
            Fecha: <strong>{formatDate(day.fecha)}</strong> (no se puede
            cambiar).
          </p>
        ) : (
          <label className={styles.label}>
            Fecha <span className={styles.required}>*</span>
            <input
              className={styles.input}
              type="date"
              min={year.fechaInicio}
              max={year.fechaFin}
              value={values.fecha}
              disabled={busy}
              aria-invalid={!!errors.fecha}
              onChange={(e) => set("fecha")(e.target.value)}
            />
            {errors.fecha && (
              <span className={styles.fieldError} role="alert">
                {errors.fecha}
              </span>
            )}
          </label>
        )}
        <label className={styles.label}>
          Descripción
          <textarea
            className={styles.input}
            rows={3}
            value={values.descripcion}
            maxLength={NON_TEACHING_DESCRIPTION_MAX + 20}
            placeholder="Ej. Feriado nacional"
            disabled={busy}
            aria-invalid={!!errors.descripcion}
            onChange={(e) => set("descripcion")(e.target.value)}
          />
          {errors.descripcion && (
            <span className={styles.fieldError} role="alert">
              {errors.descripcion}
            </span>
          )}
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
            {busy ? "Guardando…" : "Guardar"}
          </button>
        </div>
      </form>
    </Dialog>
  );
}
