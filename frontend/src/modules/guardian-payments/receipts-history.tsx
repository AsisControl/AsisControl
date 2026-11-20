"use client";
import { useState } from "react";
import { Dialog, Feedback, Pager, PanelHeading, StatusPill, Loading } from "@/shared/ui";
import { usePaged } from "@/shared/hooks/use-paged";
import { dateLabel } from "@/shared/lib/formatters";
import { PAYMENT_HISTORY_PATH } from "./services/payment-service";
import { moneyLabel, paymentMethodLabel, quotaLabel } from "./payment-format";
import { ReceiptDetail } from "./receipt-detail";
import type { PaymentHistoryItem } from "./types";
import styles from "./guardian-payments.module.css";

const labels = { APROBADO: "Aprobado", RECHAZADO: "Rechazado", VENCIDO: "Vencido" };

/** Una fila por pago/intento de una cuota, incluidos comprobantes institucionales previos. */
export function ReceiptsHistory({ studentId = "", scope = "mis estudiantes" }: { studentId?: string; scope?: string }) {
  const [state, setState] = useState("");
  const filters = new URLSearchParams();
  if (studentId) filters.set("idAlumno", studentId);
  if (state) filters.set("estado", state);
  const list = usePaged<PaymentHistoryItem>(PAYMENT_HISTORY_PATH, "", filters.toString(), 10);
  const [selected, setSelected] = useState<PaymentHistoryItem | null>(null);
  return <section className="panel">
    <PanelHeading title={"Historial de pagos de " + scope} summary="Consulta cómo terminaron tus pagos e intentos: aprobados, rechazados o vencidos."
      actions={<button type="button" className="secondary-action" disabled={list.loading} onClick={list.refresh}>Actualizar historial</button>} />
    <div className={styles.historyFilters}>
      <label className="field">Estado<select value={state} onChange={event => { setState(event.target.value); list.setPage(0); setSelected(null); }}>
        <option value="">Todos los estados</option>
        {Object.entries(labels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
      </select></label>
    </div>
    <Feedback error={list.error} />
    {list.loading ? <Loading>Cargando historial…</Loading> : !list.error && !list.data?.content.length ? <p className="empty">No hay operaciones finalizadas para los filtros seleccionados.</p> : list.data && <div className={styles.tableWrap}>
      <table className={"user-table " + styles.historyTable} aria-label="Historial de mis pagos"><thead><tr><th>Referencia y fecha</th><th>Estudiante</th><th>Concepto y cuota</th><th>Medio</th><th>Monto</th><th>Estado</th><th>Acciones</th></tr></thead><tbody>
        {list.data.content.map(item => <tr key={item.id}>
          <td><strong>{item.codigo}</strong><small>{dateLabel(item.fechaEmision)}</small>{item.codigoOrden && item.codigoOrden !== item.codigo && <small>{item.codigoOrden}</small>}</td>
          <td>{item.alumno}</td><td><strong>{item.concepto}</strong><small>{item.numeroCuota != null ? quotaLabel(item.numeroCuota) + " · " : ""}{item.codigoCompromiso}</small></td>
          <td>{paymentMethodLabel(item.medioPago)}</td><td>{moneyLabel(item.montoTotal, item.moneda)}</td>
          <td><StatusPill state={item.estado} label={labels[item.estado]} tone={item.estado === "APROBADO" ? "success" : item.estado === "RECHAZADO" ? "danger" : "neutral"} /></td>
          <td>{item.estado === "APROBADO" && item.idComprobante != null
            ? <button type="button" className="secondary-action" aria-label={"Ver comprobante " + item.codigo} onClick={() => setSelected(item)}>Ver comprobante</button>
            : <button type="button" className="secondary-action" aria-label={"Ver resultado " + item.codigo} onClick={() => setSelected(item)}>Ver resultado</button>}</td>
        </tr>)}
      </tbody></table>
    </div>}
    <Pager data={list.data} loading={list.loading} setPage={list.setPage} />
    {selected && <Dialog title={selected.estado === "APROBADO" ? "Comprobante de pago" : "Resultado del intento"} description={selected.codigo} onClose={() => setSelected(null)} size="lg">
      {selected.estado === "APROBADO" && selected.idComprobante != null ? <ReceiptDetail id={selected.idComprobante} /> : <>
        <StatusPill state={selected.estado} label={labels[selected.estado]} tone={selected.estado === "RECHAZADO" ? "danger" : "neutral"} />
        <p>{selected.alumno} · {selected.concepto}</p>
        <p>{moneyLabel(selected.montoTotal, selected.moneda)}</p>
        {selected.mensaje && <p>{selected.mensaje}</p>}
        <p className={styles.hint}>Este intento no registró un pago. Para volver a pagar, selecciona la obligación pendiente en Obligaciones e inicia una nueva operación.</p>
      </>}
      <div className={styles.actions}><button type="button" className={"secondary-action " + styles.closeButton} onClick={() => setSelected(null)}>Cerrar</button></div>
    </Dialog>}
  </section>;
}
