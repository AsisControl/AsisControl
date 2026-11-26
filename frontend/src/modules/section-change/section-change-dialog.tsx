"use client";
import { useEffect, useState, type FormEvent } from "react";
import { message } from "@/core/api/client";
import { Dialog, Feedback } from "@/shared/ui";
import type { AcademicYear } from "@/modules/academic-config";
import { SectionChangeFields } from "./section-change-fields";
import { changeSection, getEnrollmentYear } from "./section-change-service";
import type { SectionEnrollment } from "./types";

export function SectionChangeDialog({ enrollment, targetSectionId, onClose, onChanged }: {
  enrollment: SectionEnrollment;
  targetSectionId?: number;
  onClose: () => void;
  onChanged: (updated: SectionEnrollment) => void;
}) {
  const [year, setYear] = useState<AcademicYear | null>(null);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  useEffect(() => {
    let active = true;
    getEnrollmentYear(enrollment.anioAcademico).then(
      (value) => { if (active) setYear(value); },
      (cause) => { if (active) setError(message(cause)); },
    );
    return () => { active = false; };
  }, [enrollment.anioAcademico]);

  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    setBusy(true);
    setError("");
    try {
      onChanged(await changeSection(enrollment, Number(form.get("seccionId")),
        String(form.get("motivo") || "").trim()));
    } catch (cause) {
      setError(message(cause));
    } finally {
      setBusy(false);
    }
  }

  return (
    <Dialog
      title="Cambiar sección"
      description={`${enrollment.alumno} · ${enrollment.codigoAlumno} · ${enrollment.grado}° ${enrollment.seccion} ${enrollment.anioAcademico}`}
      onClose={onClose}
      busy={busy}
    >
      <Feedback error={error} />
      <form onSubmit={save}>
        <SectionChangeFields year={year} enrollment={enrollment} targetSectionId={targetSectionId} />
        <div className="form-actions">
          <button type="button" disabled={busy} onClick={onClose}>Cancelar</button>
          <button className="primary-action" disabled={busy || !year}>
            {busy ? "Guardando…" : "Confirmar cambio"}
          </button>
        </div>
      </form>
    </Dialog>
  );
}
