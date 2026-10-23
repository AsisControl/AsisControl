"use client";
import { useRef, useState, type FormEvent } from "react";
import { api, message } from "@/core/api/client";
import { Dialog, Feedback } from "@/shared/ui";
import {
  GRADE_REASON_MAX,
  availableGradeNumbers,
  hasErrors,
  validateGrade,
  type GradeErrors,
} from "../lib/validation";
import styles from "../academic-config.module.css";
import type { AcademicYear, Grade } from "../types";
export type GradeDialogMode = "create" | "edit" | "state";
type Props = {
  year: AcademicYear;
  mode: GradeDialogMode;
  /** Obligatorio en "edit" y "state". */
  grade?: Grade;
  /** Secciones activas del grado: el servidor rechaza inhabilitarlo con ellas. */
  activeSections?: number;
  onClose: () => void;
  /** Se llama tras guardar; el panel recarga el año y cierra el modal. */
  onSaved: (text: string) => Promise<void>;
  /** Tras un error del servidor: trae la versión vigente del año. */
  onStale: () => void;
};
const titles: Record<GradeDialogMode, string> = {
  create: "Habilitar grado",
  edit: "Editar grado",
  state: "Cambiar estado del grado",
};
/** US-06: modal para habilitar, editar o cambiar el estado de un grado. */
export function GradeFormDialog({
  year,
  mode,
  grade,
  activeSections = 0,
  onClose,
  onSaved,
  onStale,
}: Props) {
  const creating = mode === "create";
  const existing = year.grados.map((g) => g.numero);
  const options = availableGradeNumbers(existing);
  const next = grade ? !grade.activo : true;
  const [values, setValues] = useState({
    numero: null as number | null,
    capacidad: grade ? String(grade.capacidadDefault) : "30",
    motivo: "",
  });
  const [errors, setErrors] = useState<GradeErrors>({});
  const [serverError, setServerError] = useState("");
  const [busy, setBusy] = useState(false);
  const sending = useRef(false);
  const set = (key: keyof typeof values, value: string | number | null) => {
    setValues((current) => ({ ...current, [key]: value }));
    setErrors((current) => ({ ...current, [key]: undefined }));
  };
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (sending.current) return;
    // La versión sale del año cargado: tras un error del servidor se refresca.
    const current = grade && year.grados.find((g) => g.id === grade.id);
    if (!creating && !current) {
      setServerError("El grado ya no existe; cierra y vuelve a abrir.");
      return;
    }
    const found = validateGrade(values, { existing, creating });
    setErrors(found);
    if (hasErrors(found)) return;
    sending.current = true;
    setBusy(true);
    setServerError("");
    try {
      if (creating) {
        await api(`/api/configuracion-academica/anios/${year.anio}/grados`, {
          method: "POST",
          body: JSON.stringify({
            numero: values.numero,
            capacidadDefault: Number(values.capacidad),
          }),
        });
      } else if (current) {
        await api(`/api/configuracion-academica/grados/${current.id}`, {
          method: "PUT",
          body: JSON.stringify({
            activo: mode === "state" ? !current.activo : current.activo,
            capacidadDefault:
              mode === "state"
                ? current.capacidadDefault
                : Number(values.capacidad),
            version: current.version,
            motivo: values.motivo.trim(),
          }),
        });
      }
    } catch (error) {
      setServerError(message(error));
      if (!creating) onStale();
      sending.current = false;
      setBusy(false);
      return;
    }
    await onSaved(
      creating
        ? "Grado habilitado correctamente."
        : mode === "edit"
          ? "Grado actualizado."
          : next
            ? "Grado habilitado."
            : "Grado inhabilitado.",
    );
  }
  return (
    <Dialog title={titles[mode]} onClose={onClose} busy={busy}>
      <form className={styles.dialogForm} onSubmit={submit} noValidate>
        <Feedback error={serverError} />
        {mode === "state" && grade && (
          <p className={styles.notice}>
            {next
              ? `Se habilitará el ${grade.numero}° Grado para crear secciones y recibir solicitudes.`
              : activeSections > 0
                ? `El ${grade.numero}° Grado tiene ${activeSections} sección(es) activa(s). El servidor exige inhabilitarlas antes de inhabilitar el grado.`
                : `Se inhabilitará el ${grade.numero}° Grado. Su registro se conserva.`}
          </p>
        )}
        {creating && (
          <label className={styles.label}>
            Grado <span className={styles.required}>*</span>
            <select
              className={styles.input}
              value={values.numero ?? ""}
              disabled={busy}
              aria-invalid={!!errors.numero}
              onChange={(e) =>
                set("numero", e.target.value ? Number(e.target.value) : null)
              }
            >
              <option value="">Seleccionar grado</option>
              {options.map((n) => (
                <option key={n} value={n}>
                  {n}° Grado
                </option>
              ))}
            </select>
            {errors.numero && (
              <span className={styles.fieldError} role="alert">
                {errors.numero}
              </span>
            )}
          </label>
        )}
        {mode !== "state" && (
          <label className={styles.label}>
            Capacidad por defecto <span className={styles.required}>*</span>
            <input
              className={styles.input}
              type="number"
              inputMode="numeric"
              min={1}
              max={100}
              value={values.capacidad}
              placeholder="Ej. 35"
              disabled={busy}
              aria-invalid={!!errors.capacidad}
              onChange={(e) => set("capacidad", e.target.value)}
            />
            {errors.capacidad ? (
              <span className={styles.fieldError} role="alert">
                {errors.capacidad}
              </span>
            ) : (
              <span className={styles.fieldHint}>
                Capacidad sugerida al crear secciones de este grado.
              </span>
            )}
          </label>
        )}
        {!creating && (
          <label className={styles.label}>
            Motivo <span className={styles.required}>*</span>
            <textarea
              className={styles.input}
              rows={2}
              value={values.motivo}
              maxLength={GRADE_REASON_MAX + 50}
              placeholder="Motivo del cambio"
              disabled={busy}
              aria-invalid={!!errors.motivo}
              onChange={(e) => set("motivo", e.target.value)}
            />
            {errors.motivo && (
              <span className={styles.fieldError} role="alert">
                {errors.motivo}
              </span>
            )}
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
            {busy ? "Guardando…" : "Guardar"}
          </button>
        </div>
      </form>
    </Dialog>
  );
}
