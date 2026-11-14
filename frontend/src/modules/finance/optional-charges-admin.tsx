"use client";
import { useEffect, useState, type FormEvent } from "react";
import { message } from "@/core/api/client";
import { usePaged } from "@/shared/hooks/use-paged";
import { Dialog, Feedback, Field, Pager, PanelHeading, StatusPill } from "@/shared/ui";
import { cancelOptionalOffer, createOptionalOffers, getConcepts, OFFERS_PATH, SettingsReasonDialog, type ChargeConcept, type OptionalChargeOffer } from "@/modules/payment-settings";
import { EnrollmentPicker } from "./enrollment-picker";
import { dueDateLabel, moneyLabel } from "./commitment-format";
import type { EligibleEnrollment } from "./types";
import styles from "./finance.module.css";

const OFFER_LABELS = { PROPUESTA: "Esperando aceptación", ACEPTADA: "Aceptada", RECHAZADA: "Rechazada", CANCELADA: "Cancelada", VENCIDA: "Vencida" };
export function OptionalChargesAdmin({ canWrite }: { canWrite: boolean }) {
  const list = usePaged<OptionalChargeOffer>(OFFERS_PATH, "", "", 10);
  const [creating, setCreating] = useState(false), [cancelling, setCancelling] = useState<OptionalChargeOffer | null>(null);
  const [success, setSuccess] = useState("");
  return <section className="panel"><PanelHeading title="Cobros opcionales" summary="Publica propuestas para que el apoderado revise y acepte antes de generar una obligación."
    actions={canWrite && <button type="button" className="primary-action" onClick={() => setCreating(true)}>Publicar cobro opcional</button>} />
    <Feedback error={list.error} success={success} /><button type="button" className="secondary-action" disabled={list.loading} onClick={list.refresh}>Actualizar propuestas</button>
    {list.loading ? <p role="status">Cargando propuestas…</p> : list.error ? <button type="button" className="secondary-action" onClick={list.refresh}>Reintentar propuestas</button> : !list.data?.content.length ? <p className="empty">Todavía no hay cobros opcionales publicados.</p> : <div className="user-table-wrap"><table className={`user-table ${styles.table}`} aria-label="Cobros opcionales publicados"><thead><tr><th>Estudiante</th><th>Propuesta</th><th>Monto</th><th>Aceptación hasta</th><th>Vencimiento del pago</th><th>Estado</th>{canWrite && <th>Acciones</th>}</tr></thead><tbody>{list.data.content.map((item) => <tr key={item.id}><td>{item.alumno}<small>{item.anioAcademico}</small></td><td><strong>{item.titulo}</strong><small>{item.codigo} · {item.referencia}</small><small>{item.concepto}</small></td><td>{moneyLabel(item.monto)}{item.montoDescuento > 0 && <small>{item.beneficioNombre} · −{moneyLabel(item.montoDescuento)}</small>}</td><td>{dueDateLabel(item.fechaLimiteAceptacion)}</td><td>{dueDateLabel(item.fechaVencimiento)}</td><td><StatusPill state={item.estado} label={OFFER_LABELS[item.estado]} tone={item.estado === "ACEPTADA" ? "success" : item.estado === "PROPUESTA" ? "info" : "neutral"} />{item.codigoCompromiso && <small>{item.codigoCompromiso}</small>}</td>{canWrite && <td>{item.estado === "PROPUESTA" ? <button type="button" className="secondary-action" aria-label={`Cancelar propuesta ${item.codigo}`} onClick={() => setCancelling(item)}>Cancelar propuesta</button> : "—"}</td>}</tr>)}</tbody></table></div>}
    <Pager data={list.data} loading={list.loading} setPage={list.setPage} />
    {creating && <OptionalOfferDialog onClose={() => setCreating(false)} onSaved={(count) => { setCreating(false); list.refresh(); setSuccess(`${count} ${count === 1 ? "propuesta publicada" : "propuestas publicadas"}. Los compromisos se generarán cuando los apoderados acepten.`); }} />}
    {cancelling && <SettingsReasonDialog title="Cancelar propuesta de cobro" description={`${cancelling.codigo} · ${cancelling.alumno}`} actionLabel="Cancelar propuesta" onClose={() => setCancelling(null)} onConfirm={async (reason) => { await cancelOptionalOffer(cancelling.id, cancelling.version, reason); setCancelling(null); list.refresh(); setSuccess("Propuesta cancelada."); }} />}
  </section>;
}

function OptionalOfferDialog({ onClose, onSaved }: { onClose: () => void; onSaved: (count: number) => void }) {
  const [concepts, setConcepts] = useState<ChargeConcept[]>([]), [concept, setConcept] = useState<ChargeConcept | null>(null);
  const [enrollments, setEnrollments] = useState<EligibleEnrollment[]>([]);
  const [amount, setAmount] = useState("");
  const [error, setError] = useState(""), [busy, setBusy] = useState(false), [loading, setLoading] = useState(true), [revision, setRevision] = useState(0);
  useEffect(() => { let active = true; getConcepts().then((items) => { if (active) { setConcepts(items.filter((item) => item.activo && item.periodicidad === "UNICO" && item.requiereAceptacion)); setLoading(false); setError(""); } }).catch((caught) => { if (active) { setError(message(caught)); setLoading(false); } }); return () => { active = false; }; }, [revision]);
  function add(value: EligibleEnrollment | null) { if (value && !enrollments.some((item) => item.id === value.id)) { if (enrollments.length === 100) { setError("Puedes seleccionar hasta cien estudiantes en una publicación."); return; } setEnrollments([...enrollments, value]); } }
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); if (busy) return;
    if (!concept || !enrollments.length) { setError("Selecciona el concepto y al menos una matrícula."); return; }
    const form = new FormData(event.currentTarget); setBusy(true); setError("");
    try { const result = await createOptionalOffers({ idConcepto: concept.id, idMatriculas: enrollments.map((item) => item.id), referencia: String(form.get("referencia")).trim(), titulo: String(form.get("titulo")).trim(), descripcion: String(form.get("descripcion") || "").trim() || null, monto: Number(amount), fechaVencimiento: String(form.get("fechaVencimiento")), fechaLimiteAceptacion: String(form.get("fechaLimiteAceptacion") || "") || null }); onSaved(result.length); }
    catch (caught) { setError(message(caught)); setBusy(false); }
  }
  return <Dialog title="Publicar cobro opcional" description="El apoderado debe aceptar la propuesta antes de que se emita el compromiso." onClose={onClose} busy={busy} size="lg"><form onSubmit={submit}><Feedback error={error} />
    {loading ? <p role="status">Cargando conceptos…</p> : !concepts.length && <p className={styles.notice}>Configura un concepto de pago único que requiera aceptación familiar para publicar una propuesta.</p>}
    {!loading && error && !concepts.length && <button type="button" className="secondary-action" onClick={() => { setLoading(true); setRevision((value) => value + 1); }}>Reintentar conceptos</button>}
    <div className="form-grid"><label className="field">Concepto *<select required value={concept?.id ?? ""} disabled={busy || loading} onChange={(event) => { const next = concepts.find((item) => item.id === Number(event.target.value)) ?? null; setConcept(next); setAmount(next?.montoSugerido == null ? "" : String(next.montoSugerido)); }}><option value="">Selecciona el concepto</option>{concepts.map((item) => <option key={item.id} value={item.id}>{item.nombre}</option>)}</select></label>
      <Field label="Referencia de la actividad o cobro" name="referencia" required maxLength={80} placeholder="Ej. FERIA-2027" disabled={busy} />
      <Field label="Título para el apoderado" name="titulo" required maxLength={160} placeholder="Ej. Materiales de la feria de ciencias" disabled={busy} />
      <Field label="Importe regular por estudiante (S/)" name="monto" type="number" required min="0.01" max="9999999999.99" step="0.01" value={amount} onChange={(event) => setAmount(event.target.value)} disabled={busy} />
      <Field label="Fecha de vencimiento del pago" name="fechaVencimiento" type="date" required disabled={busy} />
      <Field label="Fecha límite de aceptación (opcional)" name="fechaLimiteAceptacion" type="date" disabled={busy} />
      <label className="field full-field">Condiciones o descripción<textarea name="descripcion" maxLength={600} rows={3} disabled={busy} /></label>
      <EnrollmentPicker selected={null} disabled={busy || enrollments.length === 100} onSelect={add} />
    </div>
    {!!enrollments.length && <><h4>Estudiantes seleccionados ({enrollments.length})</h4><ul className={styles.enrollmentChoices}>{enrollments.map((item) => <li key={item.id}><span><strong>{item.alumno}</strong><small>{item.anioAcademico} · {item.codigoMatricula}</small></span><button type="button" className="secondary-action" disabled={busy} aria-label={`Quitar a ${item.alumno}`} onClick={() => setEnrollments(enrollments.filter((row) => row.id !== item.id))}>Quitar</button></li>)}</ul></>}
    <p className={styles.notice}>Los beneficios vigentes se calculan por estudiante. La referencia identifica esta publicación y evita que se repita el mismo cobro.</p>
    <div className="form-actions"><button type="button" className="secondary-action" disabled={busy} onClick={onClose}>Cancelar</button><button className="primary-action" disabled={busy || loading || !concepts.length}>{busy ? "Publicando…" : "Publicar propuestas"}</button></div>
  </form></Dialog>;
}
