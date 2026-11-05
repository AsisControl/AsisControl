"use client";
import { useRef, useState, type FormEvent } from "react";
import { api, message } from "@/core/api/client";
import { Dialog, Feedback } from "@/shared/ui";
import {
  SECTION_NAME_MAX,
  SECTION_REASON_MAX,
  hasErrors,
  validateSection,
  type SectionErrors,
} from "../lib/validation";
import { PendingBadge } from "../pending/pending-badge";
import styles from "../academic-config.module.css";
import type { AcademicYear, Section } from "../types";
type Props = {
  year: AcademicYear;
  /** Si viene, el modal edita esa sección; si no, crea una nueva. */
  section?: Section;
  onClose: () => void;
  /** Se llama tras guardar; el panel recarga el año y cierra el modal. */
  onSaved: (text: string) => Promise<void>;
};
/** US-06: modal "Nueva sección" y edición de sección (mismo aspecto). */
export function SectionFormDialog({ year, section, onClose, onSaved }: Props) {
  const editing = !!section;
  const grades = year.grados
    .filter((g) => g.activo)
    .sort((a, b) => a.numero - b.numero);
  const [values, setValues] = useState({
    nombre: section?.nombre ?? "",
    grado: section ? section.grado : (grades[0]?.numero ?? null),
    capacidad: section
      ? String(section.capacidadMaxima)
      : String(grades[0]?.capacidadDefault ?? ""),
    motivo: "",
  });
  const [capacityTouched, setCapacityTouched] = useState(false);
  const [errors, setErrors] = useState<SectionErrors>({});
  const [serverError, setServerError] = useState("");
  const [busy, setBusy] = useState(false);
  const sending = useRef(false);
  // El servidor exige motivo siempre al editar y solo con el año ACTIVO al crear.
  const reasonRequired = editing || year.estado === "ACTIVO";
  const set = (key: keyof typeof values, value: string | number | null) => {
    setValues((current) => ({ ...current, [key]: value }));
    setErrors((current) => ({ ...current, [key]: undefined }));
  };
  function changeGrade(value: string) {
    const grade = Number(value);
    set("grado", grade);
    if (!capacityTouched) {
      const suggested = grades.find(
        (g) => g.numero === grade,
      )?.capacidadDefault;
      set("capacidad", suggested ? String(suggested) : "");
    }
  }
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (sending.current) return;
    const found = validateSection(values, {
      sections: year.secciones,
      enabledGrades: grades.map((g) => g.numero),
      reasonRequired,
      editing: section && { id: section.id, enrolled: section.ocupacion },
    });
    setErrors(found);
    if (hasErrors(found)) return;
    sending.current = true;
    setBusy(true);
    setServerError("");
    try {
      let tutor: number | null = null;
      let path = "/api/secciones";
      if (section) {
        // El listado del año no trae el tutor: se conserva el que ya tiene.
        const detail = await api<{ docenteTutorId: number | null }>(
          `${path}/${section.id}`,
        );
        tutor = detail.docenteTutorId;
        path += `/${section.id}`;
      }
      await api(path, {
        method: section ? "PUT" : "POST",
        body: JSON.stringify({
          nombre: values.nombre.trim(),
          grado: values.grado,
          anioAcademico: year.anio,
          capacidadMaxima: Number(values.capacidad),
          docenteTutorId: tutor,
          motivo: values.motivo.trim() || null,
          ...(section ? { version: section.version } : {}),
        }),
      });
    } catch (error) {
      setServerError(message(error));
      sending.current = false;
      setBusy(false);
      return;
    }
    await onSaved(
      editing ? "Sección actualizada." : "Sección creada correctamente.",
    );
  }
  return (
    <Dialog
      title={editing ? "Editar sección" : "Nueva sección"}
      onClose={onClose}
      busy={busy}
    >
      <form className={styles.dialogForm} onSubmit={submit} noValidate>
        <Feedback error={serverError} />
        <div className={styles.twoColumns}>
          <label className={styles.label}>
            Grado <span className={styles.required}>*</span>
            <select
              className={styles.input}
              value={values.grado ?? ""}
              disabled={busy || editing}
              aria-invalid={!!errors.grado}
              onChange={(e) => changeGrade(e.target.value)}
            >
              {editing && section && (
                <option value={section.grado}>{section.grado}° Grado</option>
              )}
              {!editing &&
                grades.map((g) => (
                  <option key={g.numero} value={g.numero}>
                    {g.numero}° Grado
                  </option>
                ))}
            </select>
            {errors.grado && (
              <span className={styles.fieldError} role="alert">
                {errors.grado}
              </span>
            )}
          </label>
          <label className={styles.label}>
            Sección <span className={styles.required}>*</span>
            <input
              className={styles.input}
              value={values.nombre}
              maxLength={SECTION_NAME_MAX + 20}
              placeholder="Ej. A, B, C"
              disabled={busy}
              aria-invalid={!!errors.nombre}
              onChange={(e) => set("nombre", e.target.value)}
            />
            {errors.nombre && (
              <span className={styles.fieldError} role="alert">
                {errors.nombre}
              </span>
            )}
          </label>
        </div>
        {editing && (
          <span className={styles.fieldHint}>
            Si la sección ya se usó, el servidor no permite cambiar su nombre.
          </span>
        )}
        <label className={styles.label}>
          Capacidad máxima <span className={styles.required}>*</span>
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
            onChange={(e) => {
              setCapacityTouched(true);
              set("capacidad", e.target.value);
            }}
          />
          {errors.capacidad && (
            <span className={styles.fieldError} role="alert">
              {errors.capacidad}
            </span>
          )}
          {editing && section && !errors.capacidad && (
            <span className={styles.fieldHint}>
              Alumnos matriculados: {section.ocupacion}. La capacidad no puede
              ser menor.
            </span>
          )}
        </label>
        <label className={styles.label}>
          Aula asignada <PendingBadge feature="sectionClassroom" />
          <select className={styles.input} value="" disabled>
            <option value="">Seleccionar aula</option>
          </select>
        </label>
        {reasonRequired && (
          <label className={styles.label}>
            Motivo <span className={styles.required}>*</span>
            <textarea
              className={styles.input}
              rows={2}
              value={values.motivo}
              maxLength={SECTION_REASON_MAX + 50}
              placeholder={
                editing
                  ? "Motivo del cambio"
                  : "Motivo para añadir una sección al año activo"
              }
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
            {busy ? "Guardando…" : "Guardar sección"}
          </button>
        </div>
      </form>
    </Dialog>
  );
}
