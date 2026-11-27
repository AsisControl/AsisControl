"use client";
import { useEffect, useState, type FormEvent } from "react";
import { ApiError, message } from "@/core/api/client";
import type { AcademicYear } from "@/modules/academic-config";
import { Dialog, Feedback, Loading } from "@/shared/ui";
import { enrollStudent, getAcademicYears, getStudentPlan } from "./direct-enrollment-service";
import { EnrollmentPaymentPlanAmounts } from "./payment-plan-amounts";
import type { EnrollmentPaymentPlan } from "./types";

/** Matrícula que registra administración desde la ficha: año, grado, sección y pensiones. */
export function DirectEnrollmentDialog({ student, destination, onClose, onEnrolled }: {
  student: { id: number; nombre: string; codigo: string };
  /** Sección que aparece elegida al abrir. */
  destination?: { id: number; grado: number; anioAcademico: number } | null;
  onClose: () => void;
  onEnrolled: () => void;
}) {
  const [years, setYears] = useState<AcademicYear[] | null>(null);
  const [yearNumber, setYearNumber] = useState(destination?.anioAcademico ?? 0);
  const [grade, setGrade] = useState(destination?.grado ?? 0);
  const [sectionId, setSectionId] = useState(destination?.id ?? 0);
  const [plan, setPlan] = useState<EnrollmentPaymentPlan | null>(null);
  const [revision, setRevision] = useState(0);
  const [error, setError] = useState("");
  const [planError, setPlanError] = useState("");
  const [enrolled, setEnrolled] = useState("");
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    let active = true;
    getAcademicYears().then(
      (result) => { if (active) setYears(result.content.filter((item) => item.estado === "ACTIVO")); },
      (cause) => { if (active) setError(message(cause)); },
    );
    return () => { active = false; };
  }, []);

  const year = years?.find((item) => item.anio === yearNumber) ?? years?.[0];
  const sections = year?.secciones.filter((item) => item.activo && item.grado === grade) ?? [];
  const section = sections.find((item) => item.id === sectionId && item.vacantes > 0);

  useEffect(() => {
    if (!year || !grade) return;
    let active = true;
    Promise.resolve().then(async () => {
      if (!active) return;
      setPlan(null);
      setPlanError("");
      const value = await getStudentPlan(student.id, year.anio, grade);
      if (active) setPlan(value);
    }).catch((cause) => { if (active) setPlanError(message(cause)); });
    return () => { active = false; };
  }, [student.id, year, grade, revision]);

  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!section || !plan?.puedeGenerar || !plan.firmaPlan) return;
    setBusy(true);
    setError("");
    try {
      const result = await enrollStudent(student.id, section.id, plan.firmaPlan);
      setEnrolled(result.codigoMatricula);
    } catch (cause) {
      setError(message(cause));
      if (cause instanceof ApiError && cause.code === "PLAN_ACTUALIZADO") setRevision((value) => value + 1);
    } finally {
      setBusy(false);
    }
  }

  return (
    <Dialog
      title="Matricular estudiante"
      description={`${student.nombre} · ${student.codigo}`}
      size="lg"
      onClose={enrolled ? onEnrolled : onClose}
      busy={busy}
    >
      <Feedback
        error={error}
        success={enrolled && `Matrícula ${enrolled} creada con su plan de pensiones.`}
      />
      {enrolled ? (
        <div className="form-actions">
          <button type="button" className="primary-action" onClick={onEnrolled}>Volver a la ficha</button>
        </div>
      ) : !years ? (
        !error && <Loading skeleton="list" rows={3}>Cargando años académicos…</Loading>
      ) : (
        <form onSubmit={save}>
          {!years.length && <p role="alert">No hay un año académico activo. Actívalo en Administración.</p>}
          <div className="form-grid">
            <label className="field">
              Año académico activo *
              <select required value={year?.anio ?? ""} onChange={(event) => {
                setYearNumber(Number(event.target.value));
                setGrade(0);
                setSectionId(0);
              }}>
                {years.map((item) => <option key={item.id} value={item.anio}>{item.anio}</option>)}
              </select>
            </label>
            <label className="field">
              Grado *
              <select required value={grade || ""} onChange={(event) => {
                setGrade(Number(event.target.value));
                setSectionId(0);
              }}>
                <option value="" disabled>Seleccionar grado</option>
                {year?.grados.filter((item) => item.activo)
                  .map((item) => <option key={item.id} value={item.numero}>{item.numero}° de secundaria</option>)}
              </select>
            </label>
            <label className="field">
              Sección *
              <select required value={section?.id ?? ""} disabled={!grade}
                onChange={(event) => setSectionId(Number(event.target.value))}>
                <option value="" disabled>Seleccionar sección</option>
                {sections.map((item) => (
                  <option key={item.id} value={item.id} disabled={item.vacantes <= 0}>
                    {item.nombre} · {item.vacantes} vacantes
                  </option>
                ))}
              </select>
            </label>
          </div>
          {!!grade && !sections.some((item) => item.vacantes > 0) && (
            <p role="alert">No hay secciones con vacantes en {grade}°.</p>
          )}
          {!!grade && (
            <section aria-label="Pensiones de la matrícula">
              <h4>Plan de pensiones</h4>
              <Feedback error={planError} />
              {!plan && !planError && <p role="status">Consultando las pensiones…</p>}
              {plan && !plan.puedeGenerar && <p role="alert">{plan.mensaje}</p>}
              {(planError || (plan && !plan.puedeGenerar)) && (
                <button type="button" className="secondary-action"
                  onClick={() => setRevision((value) => value + 1)}>
                  Actualizar plan de pensiones
                </button>
              )}
              {plan && <EnrollmentPaymentPlanAmounts plan={plan} />}
            </section>
          )}
          <div className="form-actions">
            <button type="button" disabled={busy} onClick={onClose}>Cancelar</button>
            <button className="primary-action" disabled={busy || !section || !plan?.puedeGenerar}>
              {busy ? "Matriculando…" : "Confirmar matrícula"}
            </button>
          </div>
        </form>
      )}
    </Dialog>
  );
}
