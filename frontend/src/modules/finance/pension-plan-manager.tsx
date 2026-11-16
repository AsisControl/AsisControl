"use client";
import { useEffect, useState, type FormEvent } from "react";
import { ApiError, message } from "@/core/api/client";
import { Dialog, Feedback, PanelHeading, StatusPill } from "@/shared/ui";
import { getConcepts, getEnrollmentPlan, generateEnrollmentPlan, linkLegacyCommitment, monthName, type ChargeConcept, type PensionPlan } from "@/modules/payment-settings";
import { EnrollmentPicker } from "./enrollment-picker";
import { dueDateLabel, financialLabels, financialToneForStatus, moneyLabel } from "./commitment-format";
import type { EligibleEnrollment, FinancialStatus, PaymentCommitment } from "./types";
import styles from "./finance.module.css";

export function PensionPlanManager({ canWrite }: { canWrite: boolean }) {
  const [enrollment, setEnrollment] = useState<EligibleEnrollment | null>(null);
  const [plan, setPlan] = useState<PensionPlan | null>(null);
  const [linking, setLinking] = useState<PaymentCommitment | null>(null);
  const [error, setError] = useState(""), [success, setSuccess] = useState(""), [busy, setBusy] = useState(false);
  const hasMissing = !!plan?.items.some((item) => item.idCompromiso == null);
  const planComplete = plan?.items.length === 10 && !hasMissing;
  function choose(value: EligibleEnrollment | null) { setEnrollment(value); setPlan(null); setError(""); setSuccess(""); }
  async function review() {
    if (!enrollment || busy) return; setBusy(true); setError(""); setSuccess("");
    try { setPlan(await getEnrollmentPlan(enrollment.id)); } catch (caught) { setError(message(caught)); } finally { setBusy(false); }
  }
  async function generate() {
    if (!enrollment || busy) return; setBusy(true); setError("");
    try { const result = await generateEnrollmentPlan(enrollment.id, plan?.firmaPlan); setPlan(result); setSuccess(result.mensaje); } catch (caught) { setError(message(caught)); if (caught instanceof ApiError && caught.code === "PLAN_ACTUALIZADO") { try { setPlan(await getEnrollmentPlan(enrollment.id)); setSuccess("El plan cambió. Revisa los importes actuales y confirma nuevamente."); } catch (refreshError) { setPlan(null); setError(message(refreshError)); } } } finally { setBusy(false); }
  }
  return <section className="panel"><PanelHeading title="Plan anual de pensiones" summary="Revisa las diez mensualidades o completa el plan de una matrícula anterior." />
    <p className={styles.notice}>Las matrículas nuevas generan sus pensiones al finalizarse. Aquí puedes revisar una matrícula y completar únicamente las mensualidades faltantes.</p>
    <EnrollmentPicker selected={enrollment} disabled={busy} onSelect={choose} />
    <div className={styles.detailActions}><button type="button" className="secondary-action" disabled={!enrollment || busy} onClick={review}>{busy ? "Consultando…" : "Revisar plan de pensiones"}</button></div>
    <Feedback error={error} success={success} />
    {plan && <>
      {plan.mensaje !== success && <p className={styles.notice}>{plan.mensaje}</p>}
      <dl className="detail-grid"><div><dt>Estudiante</dt><dd>{plan.alumno}</dd></div><div><dt>Año y grado</dt><dd>{plan.anioAcademico} · {plan.grado}° de secundaria</dd></div>{plan.montoMensual != null && <div><dt>Tarifa configurada actualmente</dt><dd>{moneyLabel(plan.montoMensual)}</dd></div>}</dl>
      {!!plan.items.length && <div className="user-table-wrap"><table className={`user-table ${styles.table}`} aria-label="Mensualidades del plan anual"><thead><tr><th>Mes</th><th>Vencimiento</th><th>Importe regular</th><th>Beneficio</th><th>Monto</th><th>Estado</th></tr></thead><tbody>{plan.items.map((item) => <tr key={item.mes}><td>{monthName(item.mes)}</td><td>{dueDateLabel(item.fechaVencimiento)}</td><td>{moneyLabel(item.montoBase)}</td><td>{item.montoDescuento > 0 ? <>{item.beneficioNombre}<small>−{moneyLabel(item.montoDescuento)}</small></> : "Sin beneficio"}</td><td>{moneyLabel(item.montoTotal)}</td><td>{item.idCompromiso ? <StatusPill state={item.estado} label={financialLabels[item.estado as FinancialStatus]} tone={financialToneForStatus(item.estado as FinancialStatus)} /> : "Por generar"}</td></tr>)}</tbody></table></div>}
      {!!plan.legados.length && <><h4>Compromisos anteriores por revisar</h4><p>Relaciona cada compromiso con su mes para conservar los pagos y evitar duplicados.</p><div className="user-table-wrap"><table className={`user-table ${styles.table}`} aria-label="Compromisos anteriores del plan"><thead><tr><th>Compromiso</th><th>Concepto</th><th>Monto</th><th>Vencimiento</th>{canWrite && <th>Acciones</th>}</tr></thead><tbody>{plan.legados.map((item) => <tr key={item.id}><td>{item.codigoCompromiso}</td><td>{item.concepto}</td><td>{moneyLabel(item.montoTotal)}</td><td>{dueDateLabel(item.fechaVencimiento)}</td>{canWrite && <td><button type="button" className="secondary-action" aria-label={`Relacionar mes de ${item.codigoCompromiso}`} onClick={() => setLinking(item)}>Relacionar con un mes</button></td>}</tr>)}</tbody></table></div></>}
      {canWrite && <div className={styles.detailActions}><button type="button" className="primary-action" disabled={busy || !plan.puedeGenerar || !hasMissing} onClick={generate}>{busy ? "Generando…" : planComplete ? "Plan completo" : "Generar mensualidades faltantes"}</button></div>}
    </>}
    {linking && enrollment && <LegacyLinkDialog commitment={linking} enrollmentId={enrollment.id} onClose={() => setLinking(null)} onSaved={(value) => { setPlan(value); setLinking(null); setSuccess("Compromiso relacionado. Se conservaron su importe y sus pagos."); }} />}
  </section>;
}

function LegacyLinkDialog({ commitment, enrollmentId, onClose, onSaved }: { commitment: PaymentCommitment; enrollmentId: number; onClose: () => void; onSaved: (plan: PensionPlan) => void }) {
  const [busy, setBusy] = useState(false), [error, setError] = useState("");
  const [kind, setKind] = useState("PENSION"), [concepts, setConcepts] = useState<ChargeConcept[]>([]);
  useEffect(() => { let active = true; getConcepts().then((items) => { if (active) setConcepts(items.filter((item) => item.activo && !item.esPension)); }).catch((caught) => { if (active) setError(message(caught)); }); return () => { active = false; }; }, []);
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); if (busy) return; const form = new FormData(event.currentTarget); setBusy(true); setError("");
    try { onSaved(await linkLegacyCommitment(enrollmentId, commitment.id, kind === "PENSION" ? Number(form.get("mes")) : null, String(form.get("motivo")).trim(), commitment.version, kind === "OTRO" ? Number(form.get("idConcepto")) : undefined)); }
    catch (caught) { setError(message(caught)); setBusy(false); }
  }
  return <Dialog title="Clasificar compromiso anterior" description={`${commitment.codigoCompromiso} · ${commitment.concepto}`} onClose={onClose} busy={busy}><form onSubmit={submit}><Feedback error={error} />
    <label className="field">Corresponde a<select value={kind} disabled={busy} onChange={(event) => setKind(event.target.value)}><option value="PENSION">Pensión mensual</option><option value="OTRO">Otro concepto</option></select></label>
    {kind === "PENSION" ? <label className="field">Mes de la pensión *<select name="mes" required defaultValue="" disabled={busy}><option value="">Selecciona el mes</option>{Array.from({ length: 10 }, (_, index) => index + 3).map((month) => <option key={month} value={month}>{monthName(month)}</option>)}</select></label> : <label className="field">Concepto del catálogo *<select name="idConcepto" required defaultValue="" disabled={busy}><option value="">Selecciona el concepto</option>{concepts.map((item) => <option value={item.id} key={item.id}>{item.nombre}</option>)}</select></label>}
    <label className="field">Motivo de la vinculación *<textarea name="motivo" rows={3} required maxLength={500} disabled={busy} /></label><p className={styles.notice}>Esta relación identifica el cobro. No modifica el dinero registrado ni sus comprobantes.</p><div className="form-actions"><button type="button" className="secondary-action" disabled={busy} onClick={onClose}>Cancelar</button><button className="primary-action" disabled={busy}>{busy ? "Guardando…" : "Guardar relación"}</button></div></form></Dialog>;
}
