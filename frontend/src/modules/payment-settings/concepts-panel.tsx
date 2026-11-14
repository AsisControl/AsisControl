"use client";
import { useState, type FormEvent } from "react";
import { message } from "@/core/api/client";
import { Dialog, Feedback, Field, PanelHeading, StatusPill } from "@/shared/ui";
import { deactivateSettings, saveSettings, SETTINGS_PATH } from "./services/settings-service";
import { SettingsReasonDialog } from "./reason-dialog";
import { useSettingsList } from "./use-settings-list";
import { soles } from "./settings-format";
import type { ChargeConcept } from "./types";
import styles from "./payment-settings.module.css";

export function ConceptsPanel({ canWrite }: { canWrite: boolean }) {
  const list = useSettingsList<ChargeConcept>(`${SETTINGS_PATH}/conceptos`);
  const [editing, setEditing] = useState<ChargeConcept | "new" | null>(null);
  const [deactivating, setDeactivating] = useState<ChargeConcept | null>(null);
  const [success, setSuccess] = useState("");
  function saved() { setEditing(null); list.refresh(); setSuccess("Concepto guardado."); }
  return <section className="panel"><PanelHeading title="Conceptos de cobro" summary="Organiza los conceptos que utiliza la institución y sus condiciones de emisión."
    actions={canWrite && <button type="button" className="primary-action" onClick={() => setEditing("new")}>Nuevo concepto</button>} />
    <Feedback error={list.error} success={success} />
    {list.loading ? <p role="status">Cargando conceptos…</p> : list.error ? <button type="button" className="secondary-action" onClick={list.refresh}>Reintentar conceptos</button> : !list.items.length ? <p className={styles.empty}>Todavía no hay conceptos registrados.</p> : <div className={styles.tableWrap}><table className={`user-table ${styles.table}`} aria-label="Conceptos de cobro"><thead><tr><th>Concepto</th><th>Frecuencia</th><th>Importe sugerido</th><th>Aceptación</th><th>Estado</th>{canWrite && <th>Acciones</th>}</tr></thead><tbody>{list.items.map((item) => <tr key={item.id}>
      <td><strong>{item.nombre}</strong><small>{item.codigo}</small>{item.descripcion && <small>{item.descripcion}</small>}</td>
      <td>{item.periodicidad === "MENSUAL" ? "Mensual" : "Pago único"}</td><td>{item.esPension ? "Según año y grado" : item.montoSugerido == null ? "Al emitir" : soles(item.montoSugerido)}</td>
      <td>{item.requiereAceptacion ? "Requiere aceptación familiar" : "Emisión autorizada"}</td><td><StatusPill state={item.activo ? "ACTIVO" : "INACTIVO"} tone={item.activo ? "success" : "neutral"} /></td>
      {canWrite && <td><div className={styles.actions}><button type="button" className="secondary-action" aria-label={`${item.activo ? "Editar" : "Reactivar"} concepto ${item.nombre}`} onClick={() => setEditing(item)}>{item.activo ? "Editar" : "Reactivar"}</button>{item.activo && !item.esPension && <button type="button" className="secondary-action" aria-label={`Desactivar concepto ${item.nombre}`} onClick={() => setDeactivating(item)}>Desactivar</button>}</div></td>}
    </tr>)}</tbody></table></div>}
    {editing && <ConceptDialog record={editing === "new" ? undefined : editing} onClose={() => setEditing(null)} onSaved={saved} />}
    {deactivating && <SettingsReasonDialog title="Desactivar concepto" description={deactivating.nombre} actionLabel="Desactivar concepto" onClose={() => setDeactivating(null)} onConfirm={async (reason) => { await deactivateSettings("conceptos", deactivating.id, deactivating.version, reason); setDeactivating(null); list.refresh(); setSuccess("Concepto desactivado. Los cobros anteriores se conservan."); }} />}
  </section>;
}

function ConceptDialog({ record, onClose, onSaved }: { record?: ChargeConcept; onClose: () => void; onSaved: () => void }) {
  const reactivating = !!record && !record.activo;
  const [frequency, setFrequency] = useState(record?.periodicidad ?? "UNICO");
  const [error, setError] = useState(""), [busy, setBusy] = useState(false);
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); if (busy) return;
    const form = new FormData(event.currentTarget);
    const amount = String(form.get("montoSugerido") || "").trim();
    setBusy(true); setError("");
    try { await saveSettings("conceptos", { codigo: record?.codigo ?? String(form.get("codigo")).trim().toUpperCase(), nombre: String(form.get("nombre")).trim(), descripcion: String(form.get("descripcion") || "").trim() || null,
      periodicidad: frequency, esPension: record?.esPension ?? false, montoSugerido: record?.esPension || !amount ? null : Number(amount), requiereAceptacion: !record?.esPension && form.get("requiereAceptacion") === "on", version: record?.version ?? 0, activo: true }, record?.id); onSaved(); }
    catch (caught) { setError(message(caught)); setBusy(false); }
  }
  return <Dialog title={reactivating ? "Reactivar concepto de cobro" : record ? "Editar concepto de cobro" : "Nuevo concepto de cobro"} onClose={onClose} busy={busy}>
    <form onSubmit={submit}><Feedback error={error} />{reactivating && <p className={styles.notice}>Al guardar, este concepto volverá a estar disponible para emitir nuevos cobros. Los compromisos anteriores se conservan.</p>}<div className="form-grid">
      <Field label="Código del concepto" name="codigo" required maxLength={40} pattern="[A-Za-z0-9_-]{2,40}" placeholder="Ej. MATERIAL_ACTIVIDAD" defaultValue={record?.codigo} disabled={busy || !!record} />
      <Field label="Nombre del concepto" name="nombre" required maxLength={160} placeholder="Ej. Materiales de una actividad" defaultValue={record?.nombre} disabled={busy} />
      <label className="field">Frecuencia<select name="periodicidad" value={frequency} disabled={busy || record?.esPension} onChange={(e) => setFrequency(e.target.value as ChargeConcept["periodicidad"])}><option value="UNICO">Pago único</option><option value="MENSUAL">Mensual</option></select></label>
      {!record?.esPension && <Field label="Importe sugerido en soles (opcional)" name="montoSugerido" type="number" min="0.01" step="0.01" max="9999999999.99" defaultValue={record?.montoSugerido ?? ""} disabled={busy} />}
      <label className="field full-field">Descripción (opcional)<textarea name="descripcion" maxLength={600} rows={3} defaultValue={record?.descripcion ?? ""} disabled={busy} /></label>
    </div>
      {record?.esPension ? <p className={styles.notice}>La pensión utiliza el plan de diez mensualidades y las tarifas del año y grado.</p> : <label className={styles.check}><input type="checkbox" name="requiereAceptacion" defaultChecked={record?.requiereAceptacion ?? false} disabled={busy || frequency === "MENSUAL"} />Solicitar aceptación del apoderado antes de generar el compromiso</label>}
      <div className="form-actions"><button type="button" className="secondary-action" disabled={busy} onClick={onClose}>Cancelar</button><button className="primary-action" disabled={busy}>{busy ? "Guardando…" : reactivating ? "Guardar y reactivar" : "Guardar concepto"}</button></div>
    </form>
  </Dialog>;
}
