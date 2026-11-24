"use client";

import { useState, type FormEvent } from "react";
import { message } from "@/core/api/client";
import { usePaged } from "@/shared/hooks/use-paged";
import { Dialog, Feedback, Pager } from "@/shared/ui";
import type { AcademicYear } from "@/modules/academic-config";
import type { Student } from "@/modules/students";
import { evaluateRisk, outcomes, type RiskEvaluation } from "./risk-service";

export function RiskEvaluationDialog({ onClose, onEvaluated }: {
  onClose: () => void; onEvaluated: () => void;
}) {
  const [search, setSearch] = useState("");
  const [student, setStudent] = useState<Student | null>(null);
  const [year, setYear] = useState("");
  const [period, setPeriod] = useState("");
  const [result, setResult] = useState<RiskEvaluation | null>(null);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const students = usePaged<Student>("/api/alumnos", search);
  const years = usePaged<AcademicYear>("/api/configuracion-academica/anios");
  const selectedYear = years.data?.content.find((item) => String(item.anio) === year);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!student || !period || saving) return;
    setSaving(true);
    setError("");
    setResult(null);
    try {
      setResult(await evaluateRisk(student.id, Number(period)));
      onEvaluated();
    } catch (reason) { setError(message(reason)); }
    finally { setSaving(false); }
  }

  return <Dialog title="Evaluar riesgo" description="Selecciona un estudiante matriculado y su período."
    busy={saving} onClose={onClose}>
    <Feedback error={error || students.error || years.error} />
    <form className="form-grid" onSubmit={submit}>
      <label className="field">Buscar estudiante
        <input disabled={saving} value={search} onChange={(event) => {
          students.setPage(0); setSearch(event.target.value); setStudent(null); setResult(null);
        }} />
      </label>
      <label className="field">Estudiante
        <select required disabled={students.loading || saving} value={student?.id ?? ""}
          onChange={(event) => {
            setStudent(students.data?.content.find((item) => String(item.id) === event.target.value) ?? null);
            setResult(null);
          }}>
          <option value="">{students.loading ? "Cargando estudiantes…" : "Selecciona un estudiante"}</option>
          {students.data?.content.filter((item) => item.activo).map(({ id, persona, codigoAlumno }) =>
            <option key={id} value={id}>{persona.nombres} {persona.apellidoPaterno} {persona.apellidoMaterno} · {codigoAlumno}</option>)}
        </select>
      </label>
      <Pager data={students.data} loading={students.loading || saving}
        setPage={(page) => { students.setPage(page); setStudent(null); setResult(null); }} />
      <label className="field">Año académico
        <select required disabled={years.loading || saving} value={year} onChange={(event) => {
          setYear(event.target.value); setPeriod(""); setResult(null);
        }}>
          <option value="">Selecciona un año</option>
          {years.data?.content.map((item) => <option key={item.id} value={item.anio}>{item.anio}</option>)}
        </select>
      </label>
      <Pager data={years.data} loading={years.loading || saving}
        setPage={(page) => { years.setPage(page); setYear(""); setPeriod(""); setResult(null); }} />
      <label className="field">Período
        <select required disabled={!selectedYear || saving} value={period}
          onChange={(event) => { setPeriod(event.target.value); setResult(null); }}>
          <option value="">Selecciona un período</option>
          {selectedYear?.periodos.map((item) => <option key={item.id} value={item.id}>{item.nombre}</option>)}
        </select>
      </label>
      <div><button className="primary-action" disabled={saving || !student || !period}>
        {saving ? "Evaluando…" : "Ejecutar evaluación"}
      </button></div>
    </form>
    {result && <div role="status">
      <strong>{outcomes[result.resultado]}</strong>
      {!result.reglas.length && <p>No hay criterios de riesgo activos.</p>}
      {result.reglas.map((rule) => <p key={rule.idCriterio}>{rule.nombreCriterio} · {outcomes[rule.resultado]}. {rule.motivo}</p>)}
    </div>}
  </Dialog>;
}
