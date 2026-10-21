"use client";
import { useState } from "react";
import { Dialog } from "@/shared/ui";
import { NON_TEACHING_DESCRIPTION_MAX } from "../lib/validation";
import styles from "../academic-config.module.css";
import type { AcademicYear } from "../types";
type Props = {
  year: AcademicYear;
  onClose: () => void;
};
/**
 * Vista previa de "Agregar día no lectivo": muestra cómo quedará el formulario.
 * No envía nada; "Guardar" sigue deshabilitado hasta que exista la API de BE 3.
 */
export function NonTeachingDayPreviewDialog({ year, onClose }: Props) {
  const [values, setValues] = useState({ fecha: "", descripcion: "" });
  return (
    <Dialog title="Agregar día no lectivo" onClose={onClose}>
      <form
        className={styles.dialogForm}
        onSubmit={(event) => event.preventDefault()}
        noValidate
      >
        <p className={styles.notice} role="status">
          Vista previa: falta el backend de días no lectivos (rama
          feature/backend-estructura-academica). Por ahora no se puede guardar.
        </p>
        <label className={styles.label}>
          Fecha <span className={styles.required}>*</span>
          <input
            className={styles.input}
            type="date"
            min={year.fechaInicio}
            max={year.fechaFin}
            value={values.fecha}
            onChange={(e) =>
              setValues((current) => ({ ...current, fecha: e.target.value }))
            }
          />
        </label>
        <label className={styles.label}>
          Descripción
          <textarea
            className={styles.input}
            rows={3}
            value={values.descripcion}
            maxLength={NON_TEACHING_DESCRIPTION_MAX + 20}
            placeholder="Ej. Feriado nacional"
            onChange={(e) =>
              setValues((current) => ({
                ...current,
                descripcion: e.target.value,
              }))
            }
          />
        </label>
        <div className={styles.dialogActions}>
          <button
            type="button"
            className={styles.cancelButton}
            onClick={onClose}
          >
            Cancelar
          </button>
          <button className={styles.submitButton} disabled>
            Guardar
          </button>
        </div>
      </form>
    </Dialog>
  );
}
