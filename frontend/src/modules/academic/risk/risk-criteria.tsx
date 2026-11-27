"use client";

import { useState, type FormEvent } from "react";
import { message } from "@/core/api/client";
import { usePaged } from "@/shared/hooks/use-paged";
import { Field, Feedback, FormDialog, Loading, Pager, PanelHeading } from "@/shared/ui";
import {
  CRITERIA_PATH, comparison, disableCriterion, indicators, levels, saveCriterion, signs,
  type Criterion,
} from "./risk-service";

const operators = {
  MENOR_QUE: "Menor que", MENOR_IGUAL: "Menor o igual que", MAYOR_QUE: "Mayor que",
  MAYOR_IGUAL: "Mayor o igual que", IGUAL: "Igual a",
};
type Action = { kind: "create" } | { kind: "edit" | "disable"; item: Criterion };

export function RiskCriteria({ onBack }: { onBack: () => void }) {
  const list = usePaged<Criterion>(CRITERIA_PATH);
  const [action, setAction] = useState<Action | null>(null);
  const [success, setSuccess] = useState("");
  return <section className="panel">
    <PanelHeading title="Criterios de riesgo"
      summary="Define las reglas que se usarán en las próximas evaluaciones de riesgo."
      actions={<>
        <button type="button" onClick={onBack}>Volver a alertas</button>
        <button type="button" className="primary-action" onClick={() => setAction({ kind: "create" })}>Nuevo criterio</button>
      </>} />
    <Feedback error={list.error} success={success} />
    {list.loading ? <Loading>Cargando criterios…</Loading> : !list.error && (
      list.data?.content.length ? <>
        <div className="user-table-wrap">
          <table className="user-table" aria-label="Criterios de riesgo">
            <thead><tr>{["Criterio", "Indicador", "Regla", "Nivel", "Datos mínimos", "Acciones"].map((column) =>
              <th key={column} scope="col">{column}</th>)}</tr></thead>
            <tbody>{list.data.content.map((item) => <tr key={item.id}>
              <td data-label="Criterio"><strong>{item.nombre}</strong>{item.descripcion && <div>{item.descripcion}</div>}</td>
              <td data-label="Indicador">{indicators[item.indicador]}</td>
              <td data-label="Regla">{comparison(item.operador, item.umbral)}</td>
              <td data-label="Nivel">{levels[item.nivelRiesgo]}</td>
              <td data-label="Datos mínimos">{item.minimoDatos}</td>
              <td data-label="Acciones"><div className="form-actions">
                <button type="button" onClick={() => setAction({ kind: "edit", item })}>Editar</button>
                <button type="button" onClick={() => setAction({ kind: "disable", item })}>Desactivar</button>
              </div></td>
            </tr>)}</tbody>
          </table>
        </div>
        <Pager data={list.data} loading={list.loading} setPage={list.setPage} />
      </> : <p className="empty">Todavía no hay criterios de riesgo activos.</p>
    )}
    {action && <CriterionDialog action={action} onClose={() => setAction(null)} onSaved={(text) => {
      setAction(null);
      setSuccess(text);
      list.refresh();
    }} />}
  </section>;
}

function CriterionDialog({ action, onClose, onSaved }: {
  action: Action; onClose: () => void; onSaved: (text: string) => void;
}) {
  const item = action.kind === "create" ? undefined : action.item;
  const disabling = action.kind === "disable";
  const [indicator, setIndicator] = useState(item?.indicador ?? "PROMEDIO_ACADEMICO");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const average = indicator === "PROMEDIO_ACADEMICO";

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (busy) return;
    const data = new FormData(event.currentTarget);
    const value = (key: string) => String(data.get(key) ?? "").trim();
    setBusy(true);
    setError("");
    try {
      if (action.kind === "disable") {
        await disableCriterion(action.item.id);
        onSaved("Criterio desactivado.");
        return;
      }
      await saveCriterion({
        nombre: value("nombre"), descripcion: value("descripcion") || null, indicador: indicator,
        operador: value("operador"), umbral: Number(value("umbral")),
        nivelRiesgo: value("nivel"), minimoDatos: Number(value("minimo")),
      }, item?.id);
      onSaved("Criterio guardado.");
    } catch (cause) {
      setError(message(cause));
    } finally { setBusy(false); }
  }

  return <FormDialog title={disabling ? "Desactivar criterio" : item ? "Editar criterio de riesgo" : "Nuevo criterio de riesgo"}
    description={disabling ? item?.nombre : undefined} busy={busy} error={error} danger={disabling}
    submitLabel={disabling ? "Desactivar criterio" : "Guardar criterio"} onClose={onClose} onSubmit={submit}>
    {disabling ? <p className="full-field">Este criterio dejará de aplicarse en evaluaciones futuras.</p> : <>
      <Field label="Nombre" name="nombre" required maxLength={160} defaultValue={item?.nombre} fullWidth />
      <label className="field full-field">Descripción
        <textarea name="descripcion" maxLength={600} defaultValue={item?.descripcion ?? ""} />
      </label>
      <label className="field">Indicador *
        <select value={indicator} required onChange={(event) => setIndicator(event.target.value as Criterion["indicador"])}>
          {Object.entries(indicators).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
        </select>
      </label>
      <label className="field">Comparación *
        <select name="operador" required defaultValue={item?.operador ?? ""}>
          <option value="">Selecciona una comparación</option>
          {Object.entries(operators).map(([value, label]) =>
            <option key={value} value={value}>{label} ({signs[value as keyof typeof signs]})</option>)}
        </select>
      </label>
      <Field label="Umbral" name="umbral" required type="number" min={average ? 1 : 0}
        max={average ? 4 : 99999999} step={average ? 0.0001 : 1} defaultValue={item?.umbral}
        hint={average ? "Valor de 1 a 4 en la escala de logro." : "Cantidad expresada como número entero."} />
      <Field label="Datos mínimos" name="minimo" type="number" min={1} step={1} required
        defaultValue={item?.minimoDatos ?? 1} hint="Cantidad mínima de registros para evaluar el indicador." />
      <label className="field">Nivel de riesgo *
        <select name="nivel" required defaultValue={item?.nivelRiesgo ?? ""}>
          <option value="">Selecciona un nivel</option>
          {Object.entries(levels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
        </select>
      </label>
    </>}
  </FormDialog>;
}
