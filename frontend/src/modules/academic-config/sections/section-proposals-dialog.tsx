"use client";

import { useState, type FormEvent } from "react";
import { message } from "@/core/api/client";
import { Field, FormDialog, ReasonField } from "@/shared/ui";
import type { AcademicYear } from "../types";
import { confirmSectionProposals, generateSectionProposals, type SectionProposal } from "./services/section-proposals-service";

const MAX_SECTION_PROPOSALS = 20;

export function SectionProposalsDialog({ year, initialGrade, onClose, onSaved }: {
  year: AcademicYear; initialGrade: number; onClose: () => void; onSaved: (text: string) => Promise<void>;
}) {
  const activeGrades = year.grados.filter((grade) => grade.activo).sort((a, b) => a.numero - b.numero);
  const [gradeNumber, setGradeNumber] = useState(
    () => activeGrades.find((grade) => grade.numero === initialGrade)?.numero ?? activeGrades[0]?.numero,
  );
  const [proposals, setProposals] = useState<SectionProposal[] | null>(null);
  const [excluded, setExcluded] = useState<number[]>([]);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const grade = activeGrades.find((option) => option.numero === gradeNumber);
  if (!grade) return null;
  const context = { anioAcademico: year.anio, grado: grade.numero };

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (busy) return;
    const form = new FormData(event.currentTarget);
    const value = (name: string) => String(form.get(name) ?? "").trim();
    setBusy(true);
    setError("");
    try {
      if (!proposals) {
        setExcluded([]);
        setProposals(await generateSectionProposals({
          ...context, cantidad: Number(value("cantidad")), capacidadMaxima: Number(value("capacidad")),
        }));
        return;
      }
      const selected = proposals.flatMap((_, index) => excluded.includes(index) ? [] : [{
        nombre: value(`nombre-${index}`), capacidadMaxima: Number(value(`capacidad-${index}`)),
      }]);
      if (!selected.length) {
        setError("Selecciona al menos una sección para confirmar.");
        return;
      }
      const created = await confirmSectionProposals({ ...context, secciones: selected, motivo: value("motivo") || null });
      await onSaved(created.length === 1 ? "Se creó una sección." : `Se crearon ${created.length} secciones.`);
    } catch (cause) {
      setError(message(cause));
    } finally { setBusy(false); }
  }

  return <FormDialog title={proposals ? "Revisar propuestas de secciones" : "Generar propuestas de secciones"}
    description={proposals ? `${grade.numero}.° grado · Año académico ${year.anio}` : "Indica cuántas secciones necesitas para el grado."}
    size="lg" busy={busy} error={error} onSubmit={submit}
    onClose={onClose} submitLabel={proposals ? "Confirmar secciones" : "Generar propuestas"}>
    {proposals ? <>
      <p className="full-field">Ajusta el nombre y la capacidad de las secciones que quieras crear.</p>
      {proposals.map((proposal, index) => {
        const selected = !excluded.includes(index);
        return <fieldset className="form-grid full-field" key={proposal.nombre}>
          <legend>Propuesta {index + 1}</legend>
          <label className="check-field full-field">
            <input type="checkbox" checked={selected} onChange={() => setExcluded((previous) =>
              selected ? [...previous, index] : previous.filter((entry) => entry !== index))} />
            Crear sección {proposal.nombre}
          </label>
          <Field label={`Nombre de sección ${index + 1}`} name={`nombre-${index}`}
            defaultValue={proposal.nombre} maxLength={20} required={selected} disabled={!selected} />
          <Field label={`Capacidad de sección ${index + 1}`} name={`capacidad-${index}`} type="number"
            defaultValue={proposal.capacidadMaxima} min={1} max={100} required={selected} disabled={!selected} />
        </fieldset>;
      })}
      {year.estado === "ACTIVO" && <ReasonField />}
      <div className="full-field">
        <button type="button" onClick={() => { setProposals(null); setError(""); }}>Volver a la configuración</button>
      </div>
    </> : <>
      <label className="field full-field">Grado *
        <select required value={grade.numero} onChange={(event) => setGradeNumber(Number(event.target.value))}>
          {activeGrades.map((option) => <option key={option.id} value={option.numero}>{option.numero}.° grado</option>)}
        </select>
      </label>
      <Field label="Cantidad de secciones" name="cantidad" type="number" min={1}
        max={MAX_SECTION_PROPOSALS} required defaultValue={1}
        hint={`Hasta ${MAX_SECTION_PROPOSALS} secciones por propuesta.`} />
      <Field key={grade.id} label="Capacidad por sección" name="capacidad" type="number"
        min={1} max={100} required defaultValue={grade.capacidadDefault} />
    </>}
  </FormDialog>;
}
