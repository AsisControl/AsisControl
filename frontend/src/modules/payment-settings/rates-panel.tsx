"use client";
import { useState, type FormEvent } from "react";
import { message } from "@/core/api/client";
import { Dialog, Feedback, Field, PanelHeading, StatusPill } from "@/shared/ui";
import { deactivateSettings, saveSettings, SETTINGS_PATH } from "./services/settings-service";
import { SettingsReasonDialog } from "./reason-dialog";
import { useSettingsList } from "./use-settings-list";
import { soles } from "./settings-format";
import type { ChargeConcept, PensionRate } from "./types";
import styles from "./payment-settings.module.css";

export function RatesPanel({ canWrite }: { canWrite: boolean }) {
  const list = useSettingsList<PensionRate>(`${SETTINGS_PATH}/tarifas`);
  const concepts = useSettingsList<ChargeConcept>(`${SETTINGS_PATH}/conceptos`);
  const years = useSettingsList<number>(`${SETTINGS_PATH}/anios`);
  const [editing, setEditing] = useState<PensionRate | "new" | null>(null);
  const [deactivating, setDeactivating] = useState<PensionRate | null>(null);
  const [year, setYear] = useState("");
  const [success, setSuccess] = useState("");
  const pension = concepts.items.find((concept) => concept.esPension && concept.activo);
  return <section className="panel"><PanelHeading title="Tarifas por año y grado" summary="Define el importe mensual de las diez pensiones de marzo a diciembre."
    actions={canWrite && <button type="button" className="primary-action" disabled={!pension || !years.items.length} onClick={() => setEditing("new")}>Nueva tarifa</button>} />
    <Feedback error={list.error || concepts.error || years.error} success={success} />
    {(concepts.error || years.error) && <button type="button" className="secondary-action" onClick={() => { concepts.refresh(); years.refresh(); }}>Reintentar configuración</button>}
    {!years.loading && !years.error && !years.items.length && <p className={styles.notice}>Primero registra el año académico para configurar sus tarifas.</p>}
    <div className={styles.toolbar}><label className="field">Filtrar año académico<select value={year} onChange={(e) => setYear(e.target.value)}><option value="">Todos</option>{Array.from(new Set(list.items.map((row) => row.anioAcademico))).sort().map((value) => <option key={value}>{value}</option>)}</select></label><button type="button" className="secondary-action" disabled={list.loading} onClick={list.refresh}>Actualizar tarifas</button></div>
    {list.loading ? <p role="status">Cargando tarifas…</p> : list.error ? <button type="button" className="secondary-action" onClick={list.refresh}>Reintentar tarifas</button> : !list.items.filter((item) => !year || item.anioAcademico === Number(year)).length ? <p className={styles.empty}>No hay tarifas para esta consulta.</p> : <div className={styles.tableWrap}><table className={`user-table ${styles.table}`} aria-label="Tarifas de pensiones"><thead><tr><th>Año académico</th><th>Grado</th><th>Pensión mensual</th><th>Estado</th>{canWrite && <th>Acciones</th>}</tr></thead><tbody>{list.items.filter((item) => !year || item.anioAcademico === Number(year)).map((item) => <tr key={item.id}>
      <td>{item.anioAcademico}</td><td>{item.grado}° de secundaria</td><td>{soles(item.montoMensual)}</td><td><StatusPill state={item.activo ? "ACTIVO" : "INACTIVO"} tone={item.activo ? "success" : "neutral"} /></td>
      {canWrite && <td><div className={styles.actions}><button type="button" className="secondary-action" aria-label={`${item.activo ? "Editar" : "Reactivar"} tarifa ${item.anioAcademico} grado ${item.grado}`} onClick={() => setEditing(item)}>{item.activo ? "Editar" : "Reactivar"}</button>{item.activo && <button type="button" className="secondary-action" aria-label={`Desactivar tarifa ${item.anioAcademico} grado ${item.grado}`} onClick={() => setDeactivating(item)}>Desactivar</button>}</div></td>}
    </tr>)}</tbody></table></div>}
    <p className={styles.notice}>Las tarifas nuevas se utilizan al generar pensiones. Los compromisos ya emitidos conservan sus importes.</p>
    {editing && pension && <RateDialog record={editing === "new" ? undefined : editing} pensionId={pension.id} years={years.items} onClose={() => setEditing(null)} onSaved={() => { setEditing(null); list.refresh(); setSuccess("Tarifa guardada. Los compromisos anteriores se conservan."); }} />}
    {deactivating && <SettingsReasonDialog title="Desactivar tarifa" description={`${deactivating.anioAcademico} · ${deactivating.grado}° de secundaria`} actionLabel="Desactivar tarifa" onClose={() => setDeactivating(null)} onConfirm={async (reason) => { await deactivateSettings("tarifas", deactivating.id, deactivating.version, reason); setDeactivating(null); list.refresh(); setSuccess("Tarifa desactivada."); }} />}
  </section>;
}

function RateDialog({ record, years, pensionId, onClose, onSaved }: { record?: PensionRate; years: number[]; pensionId: number; onClose: () => void; onSaved: () => void }) {
  const reactivating = !!record && !record.activo;
  const [busy, setBusy] = useState(false), [error, setError] = useState("");
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); if (busy) return;
    const form = new FormData(event.currentTarget); setBusy(true); setError("");
    try { await saveSettings("tarifas", { idConcepto: pensionId, anioAcademico: record?.anioAcademico ?? Number(form.get("anioAcademico")), grado: record?.grado ?? Number(form.get("grado")), montoMensual: Number(form.get("montoMensual")), version: record?.version ?? 0, activo: true }, record?.id); onSaved(); }
    catch (caught) { setError(message(caught)); setBusy(false); }
  }
  return <Dialog title={reactivating ? "Reactivar tarifa de pensión" : record ? "Editar tarifa de pensión" : "Nueva tarifa de pensión"} onClose={onClose} busy={busy}><form onSubmit={submit}><Feedback error={error} />{reactivating && <p className={styles.notice}>Al guardar, esta tarifa volverá a utilizarse para generar nuevas pensiones. Los compromisos emitidos conservan sus importes.</p>}<div className="form-grid">
    <label className="field">Año académico *<select name="anioAcademico" required defaultValue={record?.anioAcademico ?? ""} disabled={busy || !!record}><option value="">Selecciona el año</option>{Array.from(new Set([...years, ...(record ? [record.anioAcademico] : [])])).sort().map((year) => <option key={year}>{year}</option>)}</select></label>
    <label className="field">Grado *<select name="grado" required defaultValue={record?.grado ?? ""} disabled={busy || !!record}><option value="">Selecciona el grado</option>{[1, 2, 3, 4, 5].map((grade) => <option value={grade} key={grade}>{grade}° de secundaria</option>)}</select></label>
    <Field label="Pensión mensual en soles (S/)" name="montoMensual" required type="number" min="0.01" max="9999999999.99" step="0.01" defaultValue={record?.montoMensual} disabled={busy} />
  </div><div className="form-actions"><button type="button" className="secondary-action" disabled={busy} onClick={onClose}>Cancelar</button><button className="primary-action" disabled={busy}>{busy ? "Guardando…" : reactivating ? "Guardar y reactivar" : "Guardar tarifa"}</button></div></form></Dialog>;
}
