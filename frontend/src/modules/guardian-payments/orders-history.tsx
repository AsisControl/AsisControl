"use client";
import { useState } from "react";
import { Dialog, Feedback, Pager, PanelHeading, StatusPill } from "@/shared/ui";
import { usePaged } from "@/shared/hooks/use-paged";
import { dateLabel } from "@/shared/lib/formatters";
import { ORDERS_PATH } from "./services/payment-service";
import { isPending, moneyLabel, orderLabels } from "./payment-format";
import { PaymentCheckout } from "./payment-checkout";
import { ReceiptPanel } from "./receipt-panel";
import type { PaymentOrder } from "./types";
import styles from "./guardian-payments.module.css";

export function OrdersHistory({ onRefreshObligations }: { onRefreshObligations: () => void }) {
  const list = usePaged<PaymentOrder>(ORDERS_PATH, "", "", 10);
  const [selected, setSelected] = useState<PaymentOrder | null>(null);
  function settled() { list.refresh(); onRefreshObligations(); }
  return <section className="panel">
    <PanelHeading title="Seguimiento de pagos" summary="Continúa un pago pendiente o consulta su resultado. Un pago puede incluir varias cuotas."
      actions={<button type="button" className="secondary-action" disabled={list.loading} onClick={list.refresh}>Actualizar seguimiento</button>} />
    <Feedback error={list.error} />
    <p className={styles.hint}>Cerrar la ventana no cancela un pago pendiente. Puedes continuarlo durante sus cinco minutos de vigencia; después podrás iniciar uno nuevo.</p>
    {list.loading ? <p role="status">Cargando seguimiento…</p> : !list.error && !list.data?.content.length ? <p className="empty">Todavía no has iniciado un pago.</p> : list.data && <div className={styles.tableWrap}>
      <table className={`user-table ${styles.historyTable}`} aria-label="Seguimiento de mis pagos"><thead><tr><th>Referencia y fecha</th><th>Medio</th><th>Monto</th><th>Resultado</th><th>Acciones</th></tr></thead><tbody>
        {list.data.content.map((order) => <tr key={order.id}>
          <td><strong>{order.codigo}</strong><small>{dateLabel(order.creadaEn)}</small></td><td>{order.metodo === "TARJETA" ? "Tarjeta" : "Código QR"}</td>
          <td>{moneyLabel(order.montoTotal, "PEN")}</td><td><StatusPill state={order.estado} label={orderLabels[order.estado]} tone={order.estado === "COMPLETADO" ? "success" : order.estado === "RECHAZADO" ? "danger" : "neutral"} /></td>
          <td><button type="button" className="secondary-action" onClick={() => setSelected(order)}>{order.estado === "COMPLETADO" ? "Ver comprobante" : isPending(order.estado) ? "Continuar pago" : "Ver resultado"}</button></td>
        </tr>)}
      </tbody></table>
    </div>}
    <Pager data={list.data} loading={list.loading} setPage={list.setPage} />
    {selected && (selected.estado === "COMPLETADO" ? <Dialog title="Constancia de pago" description={selected.codigo} onClose={() => setSelected(null)} size="lg"><ReceiptPanel orderId={selected.id} /><div className={styles.actions}><button type="button" className={`secondary-action ${styles.closeButton}`} onClick={() => setSelected(null)}>Cerrar</button></div></Dialog>
      : <PaymentCheckout obligations={[]} initialOrder={selected} onClose={() => setSelected(null)} onSettled={settled} />)}
  </section>;
}
