"use client";
import { useEffect, useEffectEvent, useState, type FormEvent } from "react";
import { message } from "@/core/api/client";
import { usePaged } from "@/shared/hooks/use-paged";
import { Feedback, PageHeader, Pager, PanelHeading, PersonCell, StatusPill, Loading } from "@/shared/ui";
import type { PaymentCommitment } from "@/modules/finance";
import { getStudents, OBLIGATIONS_PATH } from "./services/payment-service";
import { canPay, dueDateLabel, financialLabel, financialLabels, financialStatus, financialTone, moneyLabel, quotaLabel } from "./payment-format";
import { ObligationDetailDialog } from "./obligation-detail";
import { OrdersHistory } from "./orders-history";
import { ReceiptsHistory } from "./receipts-history";
import { OptionalCharges } from "./optional-charges";
import { PaymentCheckout } from "./payment-checkout";
import { PaymentTabs } from "./payment-tabs";
import { scopeLabel, StudentScope } from "./student-scope";
import type { FamilyStudent, PaymentOrder } from "./types";
import styles from "./guardian-payments.module.css";

export function GuardianPayments() {
  const [tab, setTab] = useState("obligaciones");
  const [students, setStudents] = useState<FamilyStudent[] | null>(null);
  const [studentError, setStudentError] = useState("");
  const [revision, setRevision] = useState(0);
  /** Estudiante elegido en el selector; vacío es el estado de cuenta consolidado. */
  const [studentId, setStudentId] = useState("");
  /** Tipo de cobro y situación de pago elegidos en el formulario de Obligaciones. */
  const [formFilters, setFormFilters] = useState("");
  const applied = new URLSearchParams(formFilters);
  const query = new URLSearchParams(formFilters);
  if (studentId) query.set("idAlumno", studentId);
  const filters = query.toString();
  const list = usePaged<PaymentCommitment>(OBLIGATIONS_PATH, "", filters, 10);
  const [selected, setSelected] = useState<PaymentCommitment[]>([]);
  const [selectionError, setSelectionError] = useState("");
  const [detail, setDetail] = useState<number | null>(null);
  const [checkout, setCheckout] = useState<PaymentCommitment[] | null>(null);
  const [success, setSuccess] = useState("");
  const refreshOnFocus = useEffectEvent(() => {
    if (document.visibilityState === "visible" && !list.loading) list.refresh();
  });
  useEffect(() => {
    if (tab !== "obligaciones" || checkout) return;
    const onFocus = () => refreshOnFocus();
    window.addEventListener("focus", onFocus);
    return () => window.removeEventListener("focus", onFocus);
  }, [tab, checkout]);
  useEffect(() => {
    let active = true;
    getStudents().then((value) => { if (active) { setStudents(value); setStudentError(""); } })
      .catch((caught) => { if (active) setStudentError(message(caught)); });
    return () => { active = false; };
  }, [revision]);
  function applyFilters(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget), query = new URLSearchParams();
    for (const name of ["tipo", "estado"]) {
      const value = String(form.get(name) || ""); if (value) query.set(name, value);
    }
    setFormFilters(query.toString()); list.setPage(0); setSelected([]); setSelectionError("");
  }
  function chooseStudent(value: string) {
    setStudentId(value); list.setPage(0); setSelected([]); setSelectionError(""); setSuccess("");
  }
  const scope = scopeLabel(students, studentId);
  function toggle(item: PaymentCommitment) {
    setSelectionError("");
    if (selected.some((value) => value.id === item.id)) return setSelected(selected.filter((value) => value.id !== item.id));
    if (selected.length >= 20) return setSelectionError("Puedes seleccionar hasta 20 obligaciones en un pago.");
    setSelected([...selected, item]);
  }
  const payable = list.data?.content.filter(canPay) ?? [];
  const allSelected = payable.length > 0 && payable.every((item) => selected.some((value) => value.id === item.id));
  function togglePage() {
    setSelectionError("");
    if (allSelected) return setSelected(selected.filter((value) => !payable.some((item) => item.id === value.id)));
    const missing = payable.filter((item) => !selected.some((value) => value.id === item.id));
    if (selected.length + missing.length > 20) return setSelectionError("Puedes seleccionar hasta 20 obligaciones en un pago.");
    setSelected([...selected, ...missing]);
  }
  function settled(order: PaymentOrder) {
    list.refresh();
    if (order.estado === "COMPLETADO") { setSelected([]); setSuccess(`Pago ${order.codigo} aprobado. Tus obligaciones fueron actualizadas.`); }
  }
  function followPayments() {
    setCheckout(null); setSelected([]); setSelectionError(""); setTab("operaciones");
  }
  function selectTab(next: string) {
    if (next === "obligaciones" && tab !== next) list.refresh();
    setTab(next);
  }
  return <section className={styles.payments}>
    <PageHeader title="Mis pagos" description="Consulta las obligaciones y los pagos de tus estudiantes en soles." />
    {tab !== "opcionales" && <StudentScope students={students} value={studentId} onChange={chooseStudent} />}
    <PaymentTabs label="Secciones de pagos" selected={tab} onSelect={selectTab} items={[{ id: "obligaciones", label: "Obligaciones" }, { id: "historial", label: "Historial de pagos" }, { id: "operaciones", label: "Seguimiento de pagos" }, { id: "opcionales", label: "Actividades y servicios" }]}>
    {tab === "historial" ? <ReceiptsHistory key={studentId} studentId={studentId} scope={scope} />
      : tab === "operaciones" ? <OrdersHistory studentId={studentId} scope={scope} onRefreshObligations={list.refresh} onReturnToObligations={() => selectTab("obligaciones")} />
      : tab === "opcionales" ? <OptionalCharges onChanged={list.refresh} onObligation={setDetail} /> : <section className="panel">
      <PanelHeading title={`Obligaciones de ${scope}`} summary="Selecciona las obligaciones que deseas pagar."
        actions={<button type="button" className="secondary-action" disabled={list.loading} onClick={list.refresh}>Actualizar obligaciones</button>} />
      <Feedback error={studentError || list.error || selectionError} success={success} />
      {studentError && <button type="button" className="secondary-action" onClick={() => setRevision((value) => value + 1)}>Reintentar estudiantes</button>}
      {students?.length === 0 && <p className={styles.notice}>No tienes estudiantes asociados. Consulta con la institución para revisar tu vínculo familiar.</p>}
      <form className={`${styles.filters} ${styles.stateFilters}`} onSubmit={applyFilters}>
        <label className="field">Tipo de cobro<select name="tipo" defaultValue={applied.get("tipo") ?? ""}><option value="">Todos los conceptos</option><option value="PENSION">Pensiones</option><option value="OTRO">Actividades y servicios</option></select></label>
        <label className="field">Situación de pago<select name="estado" defaultValue={applied.get("estado") ?? ""}><option value="">Todas</option>{Object.entries(financialLabels).map(([state, label]) => <option value={state} key={state}>{label}</option>)}</select></label>
        <button type="submit" className="primary-action" disabled={list.loading}>Buscar</button>
      </form>
      {list.loading ? <Loading>Cargando obligaciones…</Loading> : !list.error && !list.data?.content.length ? <p className="empty">No hay obligaciones que coincidan con la consulta.</p> : list.data && <div className={styles.tableWrap}>
        <table className={`user-table ${styles.obligationsTable}`} aria-label="Obligaciones de mis estudiantes"><thead><tr>
          <th><input type="checkbox" aria-label="Seleccionar obligaciones de esta página" checked={allSelected} disabled={!payable.length} onChange={togglePage} /></th>
          <th>Estudiante</th><th>Concepto y cuota</th><th>Vencimiento</th><th>Monto</th><th>Situación</th><th>Acciones</th>
        </tr></thead><tbody>{list.data.content.map((item) => <tr key={item.id}>
          <td><input type="checkbox" aria-label={`Seleccionar ${item.codigoCompromiso} de ${item.alumno}`} disabled={!canPay(item)} checked={selected.some((value) => value.id === item.id)} onChange={() => toggle(item)} /></td>
          <td><PersonCell name={item.alumno} detail={`${item.codigoAlumno} · ${item.anioAcademico}`} /></td>
          <td><strong>{item.concepto}</strong><small>{item.numeroCuota == null ? item.codigoCompromiso : `${quotaLabel(item.numeroCuota)} · ${item.codigoCompromiso}`}</small></td>
          <td>{dueDateLabel(item.fechaVencimiento)}</td><td>{moneyLabel(item.montoTotal, item.moneda)}{item.montoPagado > 0 && item.saldo > 0 && <small>Por pagar: {moneyLabel(item.saldo, item.moneda)}</small>}</td>
          <td><StatusPill state={financialStatus(item)} label={financialLabel(item)} tone={financialTone(item)} /></td>
          <td><button type="button" className="secondary-action" aria-label={`Ver obligación ${item.codigoCompromiso}`} onClick={() => setDetail(item.id)}>Ver detalle</button></td>
        </tr>)}</tbody></table>
      </div>}
      <Pager data={list.data} loading={list.loading} setPage={list.setPage} />
      <div className={styles.selectionBar}><div><strong>{selected.length} {selected.length === 1 ? "obligación seleccionada" : "obligaciones seleccionadas"}</strong><span>{moneyLabel(selected.reduce((sum, item) => sum + item.saldo, 0), "PEN")}</span></div>
        <button type="button" className="secondary-action" disabled={!selected.length} onClick={() => { setSelected([]); setSelectionError(""); }}>Limpiar selección</button>
        <button type="button" className="primary-action" disabled={!selected.length || list.loading} onClick={() => setCheckout([...selected])}>Pagar seleccionadas</button></div>
    </section>}
    </PaymentTabs>
    {detail != null && <ObligationDetailDialog id={detail} onClose={() => setDetail(null)} />}
    {checkout && <PaymentCheckout obligations={checkout} onClose={() => setCheckout(null)} onSettled={settled} onFollowPayments={followPayments} />}
  </section>;
}
