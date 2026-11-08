"use client";
import { useState } from "react";
import { Dialog, Feedback, Pager, PanelHeading } from "@/shared/ui";
import { usePaged } from "@/shared/hooks/use-paged";
import { dateLabel } from "@/shared/lib/formatters";
import { RECEIPTS_PATH } from "./services/payment-service";
import { moneyLabel, paymentMethodLabel, quotaLabel } from "./payment-format";
import { ReceiptDetail } from "./receipt-detail";
import type { ReceiptHistoryItem } from "./types";
import styles from "./guardian-payments.module.css";

export function ReceiptsHistory() {
  const list = usePaged<ReceiptHistoryItem>(RECEIPTS_PATH, "", "", 10);
  const [selected, setSelected] = useState<ReceiptHistoryItem | null>(null);
  return <section className="panel">
    <PanelHeading title="Historial de pagos" summary="Consulta todos los pagos realizados con su comprobante, incluidos los registrados por la institución."
      actions={<button type="button" className="secondary-action" disabled={list.loading} onClick={list.refresh}>Actualizar historial</button>} />
    <p className={styles.hint}>Cada comprobante acredita un pago aplicado a una cuota. Un pago puede agrupar varias cuotas; consulta las solicitudes pendientes o rechazadas en Seguimiento de pagos.</p>
    <Feedback error={list.error} />
    {list.loading ? <p role="status">Cargando historial…</p> : !list.error && !list.data?.content.length ? <p className="empty">Todavía no tienes pagos registrados.</p> : list.data && <div className={styles.tableWrap}>
      <table className={`user-table ${styles.historyTable}`} aria-label="Comprobantes de mis estudiantes"><thead><tr><th>Comprobante y fecha</th><th>Estudiante</th><th>Concepto y cuota</th><th>Medio</th><th>Monto pagado</th><th>Acciones</th></tr></thead><tbody>
        {list.data.content.map((receipt) => <tr key={receipt.id}>
          <td><strong>{receipt.codigo}</strong><small>{dateLabel(receipt.fechaEmision)}</small>{receipt.codigoOrden && <small>{receipt.codigoOrden}</small>}</td>
          <td>{receipt.alumno}</td><td><strong>{receipt.concepto}</strong><small>{quotaLabel(receipt.numeroCuota)} · {receipt.codigoCompromiso}</small></td>
          <td>{paymentMethodLabel(receipt.medioPago)}</td><td>{moneyLabel(receipt.montoTotal, receipt.moneda)}</td>
          <td><button type="button" className="secondary-action" aria-label={`Ver comprobante ${receipt.codigo}`} onClick={() => setSelected(receipt)}>Ver comprobante</button></td>
        </tr>)}
      </tbody></table>
    </div>}
    <Pager data={list.data} loading={list.loading} setPage={list.setPage} />
    {selected && <Dialog title="Comprobante de pago" description={selected.codigo} onClose={() => setSelected(null)} size="lg"><ReceiptDetail id={selected.id} /><div className={styles.actions}><button type="button" className={`secondary-action ${styles.closeButton}`} onClick={() => setSelected(null)}>Cerrar</button></div></Dialog>}
  </section>;
}
