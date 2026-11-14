"use client";
import { useEffect, useState, type FormEvent } from "react";
import { ApiError, message } from "@/core/api/client";
import { Dialog, Feedback, Field, PanelHeading, StatusPill } from "@/shared/ui";
import { applyBenefit, deactivateSettings, getBenefitPreview, saveSettings, SETTINGS_PATH } from "./services/settings-service";
import { PaymentStudentPicker } from "./student-picker";
import { SettingsReasonDialog } from "./reason-dialog";
import { useSettingsList } from "./use-settings-list";
import { MONTHS, monthName, soles } from "./settings-format";
import type { BenefitPreview, ChargeConcept, StudentBenefit, StudentOption } from "./types";
import styles from "./payment-settings.module.css";

export function BenefitsPanel({ canWrite }: { canWrite: boolean }) {
  const list = useSettingsList<StudentBenefit>(`${SETTINGS_PATH}/beneficios`);
  const concepts = useSettingsList<ChargeConcept>(`${SETTINGS_PATH}/conceptos`);
  const years = useSettingsList<number>(`${SETTINGS_PATH}/anios`);
  const [editing, setEditing] = useState<StudentBenefit | "new" | null>(null);
  const [deactivating, setDeactivating] = useState<StudentBenefit | null>(null);
  const [preview, setPreview] = useState<StudentBenefit | null>(null);
  const [success, setSuccess] = useState("");
  return <section className="panel"><PanelHeading title="Becas y descuentos" summary="Asigna un beneficio a un estudiante, concepto y meses determinados."
    actions={canWrite && <button type="button" className="primary-action" disabled={!concepts.items.some((item) => item.activo) || !years.items.length} onClick={() => setEditing("new")}>Asignar beneficio</button>} />
    <Feedback error={list.error || concepts.error || years.error} success={success} />
    {(concepts.error || years.error) && <button type="button" className="secondary-action" onClick={() => { concepts.refresh(); years.refresh(); }}>Reintentar configuración</button>}
    <p className={styles.notice}>Se aplica un beneficio por concepto y mes. Los pagos registrados se conservan; para actualizar cuotas ya emitidas, revisa primero la vista previa.</p>
    <button type="button" className="secondary-action" disabled={list.loading} onClick={list.refresh}>Actualizar beneficios</button>
    {list.loading ? <p role="status">Cargando beneficios…</p> : list.error ? <button type="button" className="secondary-action" onClick={list.refresh}>Reintentar beneficios</button> : !list.items.length ? <p className={styles.empty}>Todavía no hay becas o descuentos asignados.</p> : <div className={styles.tableWrap}><table className={`user-table ${styles.table}`} aria-label="Becas y descuentos por estudiante"><thead><tr><th>Estudiante</th><th>Beneficio y concepto</th><th>Valor</th><th>Vigencia</th><th>Estado</th>{canWrite && <th>Acciones</th>}</tr></thead><tbody>{list.items.map((item) => <tr key={item.id}>
      <td>{item.alumno}</td><td><strong>{item.nombre}</strong><small>{item.concepto}</small></td><td>{item.tipo === "PORCENTAJE" ? `${item.valor} %` : `${soles(item.valor)} por cobro`}</td><td>{monthName(item.mesInicio)}–{monthName(item.mesFin)}<small>{item.anioAcademico}</small></td>
      <td><StatusPill state={item.activo ? "ACTIVO" : "INACTIVO"} tone={item.activo ? "success" : "neutral"} /></td>
      {canWrite && <td><div className={styles.actions}><button type="button" className="secondary-action" aria-label={`${item.activo ? "Editar" : "Reactivar"} beneficio ${item.nombre} de ${item.alumno}`} onClick={() => setEditing(item)}>{item.activo ? "Editar" : "Reactivar"}</button>{item.activo && <><button type="button" className="secondary-action" aria-label={`Revisar aplicación de ${item.nombre} a ${item.alumno}`} onClick={() => setPreview(item)}>Revisar aplicación</button><button type="button" className="secondary-action" aria-label={`Desactivar beneficio ${item.nombre} de ${item.alumno}`} onClick={() => setDeactivating(item)}>Desactivar</button></>}</div></td>}
    </tr>)}</tbody></table></div>}
    {editing && <BenefitDialog record={editing === "new" ? undefined : editing} concepts={concepts.items} years={years.items} onClose={() => setEditing(null)} onSaved={() => { setEditing(null); list.refresh(); setSuccess("Beneficio guardado. Se utilizará al generar nuevas obligaciones dentro de su vigencia."); }} />}
    {preview && <BenefitApplicationDialog record={preview} onClose={() => setPreview(null)} onApplied={(notice) => { setPreview(null); list.refresh(); setSuccess(notice); }} />}
    {deactivating && <SettingsReasonDialog title="Desactivar beneficio" description={`${deactivating.alumno} · ${deactivating.nombre}`} actionLabel="Desactivar beneficio" onClose={() => setDeactivating(null)} onConfirm={async (reason) => { await deactivateSettings("beneficios", deactivating.id, deactivating.version, reason); setDeactivating(null); list.refresh(); setSuccess("Beneficio desactivado. Las obligaciones anteriores conservan sus importes."); }} />}
  </section>;
}

function BenefitDialog({ record, concepts, years, onClose, onSaved }: { record?: StudentBenefit; concepts: ChargeConcept[]; years: number[]; onClose: () => void; onSaved: () => void }) {
  const reactivating = !!record && !record.activo;
  const [student, setStudent] = useState<StudentOption | null>(record ? { id: record.idAlumno, nombreCompleto: record.alumno, codigoAlumno: "" } : null);
  const [kind, setKind] = useState(record?.tipo ?? "PORCENTAJE");
  const [busy, setBusy] = useState(false), [error, setError] = useState("");
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); if (busy) return;
    if (!student) { setError("Selecciona el estudiante que recibirá el beneficio."); return; }
    const form = new FormData(event.currentTarget);
    if (Number(form.get("mesInicio")) > Number(form.get("mesFin"))) { setError("El mes de inicio debe ser anterior o igual al mes de fin."); return; }
    setBusy(true); setError("");
    try { await saveSettings("beneficios", { idAlumno: student.id, anioAcademico: record?.anioAcademico ?? Number(form.get("anioAcademico")), idConcepto: record?.idConcepto ?? Number(form.get("idConcepto")), nombre: String(form.get("nombre")).trim(), tipo: kind, valor: Number(form.get("valor")), mesInicio: Number(form.get("mesInicio")), mesFin: Number(form.get("mesFin")), motivo: String(form.get("motivo")).trim(), version: record?.version ?? 0, activo: true }, record?.id); onSaved(); }
    catch (caught) { setError(message(caught)); setBusy(false); }
  }
  return <Dialog title={reactivating ? "Reactivar beneficio del estudiante" : record ? "Editar beneficio del estudiante" : "Asignar beca o descuento"} onClose={onClose} busy={busy} size="lg"><form onSubmit={submit}><Feedback error={error} />
    {reactivating && <p className={styles.notice}>Al guardar, este beneficio volverá a aplicarse a nuevas obligaciones dentro de su vigencia. Para ajustar obligaciones emitidas, utiliza después Revisar aplicación.</p>}
    <PaymentStudentPicker value={student} disabled={busy} allowChange={!record} onSelect={setStudent} />
    <div className="form-grid">
      <Field label="Nombre del beneficio" name="nombre" required maxLength={160} placeholder="Ej. Beca parcial" defaultValue={record?.nombre} disabled={busy} />
      <label className="field">Año académico *<select name="anioAcademico" required defaultValue={record?.anioAcademico ?? ""} disabled={busy || !!record}><option value="">Selecciona el año</option>{Array.from(new Set([...years, ...(record ? [record.anioAcademico] : [])])).sort().map((year) => <option key={year}>{year}</option>)}</select></label>
      <label className="field">Concepto al que se aplica *<select name="idConcepto" required defaultValue={record?.idConcepto ?? concepts.find((concept) => concept.esPension)?.id ?? ""} disabled={busy || !!record}><option value="">Selecciona el concepto</option>{concepts.filter((concept) => concept.activo || concept.id === record?.idConcepto).map((concept) => <option key={concept.id} value={concept.id}>{concept.nombre}</option>)}</select></label>
      <label className="field">Tipo de beneficio<select name="tipo" value={kind} disabled={busy} onChange={(event) => setKind(event.target.value as StudentBenefit["tipo"])}><option value="PORCENTAJE">Porcentaje</option><option value="IMPORTE_FIJO">Importe fijo por cobro</option></select></label>
      <Field label={kind === "PORCENTAJE" ? "Porcentaje de descuento (%)" : "Descuento por cobro en soles (S/)"} name="valor" type="number" required min="0.01" max={kind === "PORCENTAJE" ? 100 : "9999999999.99"} step="0.01" defaultValue={record?.valor} disabled={busy} />
      <label className="field">Mes de inicio *<select name="mesInicio" required defaultValue={record?.mesInicio ?? 3} disabled={busy}>{MONTHS.map((month, index) => <option key={month} value={index + 1}>{month}</option>)}</select></label>
      <label className="field">Mes de fin *<select name="mesFin" required defaultValue={record?.mesFin ?? 12} disabled={busy}>{MONTHS.map((month, index) => <option key={month} value={index + 1}>{month}</option>)}</select></label>
      <label className="field full-field">Motivo de la asignación *<textarea name="motivo" required maxLength={500} rows={3} defaultValue={record?.motivo} disabled={busy} /></label>
    </div>
    <div className="form-actions"><button type="button" className="secondary-action" disabled={busy} onClick={onClose}>Cancelar</button><button className="primary-action" disabled={busy}>{busy ? "Guardando…" : reactivating ? "Guardar y reactivar" : "Guardar beneficio"}</button></div>
  </form></Dialog>;
}

function BenefitApplicationDialog({ record, onClose, onApplied }: { record: StudentBenefit; onClose: () => void; onApplied: (message: string) => void }) {
  const [preview, setPreview] = useState<BenefitPreview | null>(null);
  const [error, setError] = useState(""), [busy, setBusy] = useState(false), [revision, setRevision] = useState(0);
  const [reviewNotice, setReviewNotice] = useState("");
  useEffect(() => { let active = true; getBenefitPreview(record.id).then((data) => { if (active) { setPreview(data); setError(""); } }).catch((caught) => { if (active) setError(message(caught)); }); return () => { active = false; }; }, [record.id, revision]);
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); if (busy || !preview) return;
    const form = new FormData(event.currentTarget); setBusy(true); setError("");
    try { const result = await applyBenefit(record.id, String(form.get("motivo")).trim(), record.version, preview.firmaAjuste); onApplied(result.mensaje); }
    catch (caught) { if (caught instanceof ApiError && caught.code === "AJUSTE_ACTUALIZADO") { setPreview(null); setReviewNotice("Los importes cambiaron. Revisa la información actualizada y confirma el ajuste nuevamente."); setRevision((value) => value + 1); } setError(message(caught)); setBusy(false); }
  }
  return <Dialog title="Aplicar beneficio a obligaciones emitidas" description={`${record.alumno} · ${record.nombre}`} onClose={onClose} busy={busy} size="lg"><Feedback error={error} />
    {reviewNotice && <p role="status" className={styles.notice}>{reviewNotice}</p>}
    {!preview && !error && <p role="status">Revisando obligaciones…</p>}{!preview && error && <button type="button" className="secondary-action" onClick={() => setRevision((value) => value + 1)}>Reintentar revisión</button>}
    {preview && <form onSubmit={submit}><p className={styles.notice}>{preview.mensaje}</p><h4>Obligaciones que se actualizarán</h4>
      {!preview.ajustables.length ? <p>No hay obligaciones disponibles para ajustar.</p> : <ul className={styles.choices}>{preview.ajustables.map((item) => <li key={item.id}><span><strong>{item.concepto}</strong><small>{item.codigoCompromiso} · {item.fechaVencimiento}</small></span><strong>{item.montoTotal === 0 ? "Exonerado" : soles(item.montoTotal)}</strong></li>)}</ul>}
      {!!preview.omitidos.length && <><h4>Obligaciones que se conservan</h4><ul className={styles.choices}>{preview.omitidos.map((item) => <li key={item.id}><span>{item.codigoCompromiso} · {item.concepto}</span><span>{item.montoPagado > 0 ? "Con pagos registrados" : "Sin ajuste"}</span></li>)}</ul></>}
      {!!preview.ajustables.length && <label className="field">Motivo del ajuste *<textarea name="motivo" required disabled={busy} maxLength={500} rows={3} /></label>}
      <div className="form-actions"><button type="button" className="secondary-action" disabled={busy} onClick={onClose}>Cerrar</button>{!!preview.ajustables.length && <button className="primary-action" disabled={busy}>{busy ? "Aplicando…" : "Aplicar a las obligaciones indicadas"}</button>}</div>
    </form>}
  </Dialog>;
}
