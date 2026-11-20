"use client";
import { useEffect, useEffectEvent, useState } from "react";
import { Feedback, Pager, PanelHeading, StatusPill, Loading } from "@/shared/ui";
import { usePaged } from "@/shared/hooks/use-paged";
import { dateLabel } from "@/shared/lib/formatters";
import { ORDERS_PATH } from "./services/payment-service";
import { isPending, moneyLabel, orderLabels } from "./payment-format";
import { PaymentCheckout } from "./payment-checkout";
import type { PaymentOrder } from "./types";
import styles from "./guardian-payments.module.css";

/** Pagos iniciados que incluyen cuotas del estudiante elegido o, sin estudiante, de toda la familia. */
export function OrdersHistory({ onRefreshObligations, studentId = "", scope = "mis estudiantes" }: {
  onRefreshObligations: () => void;
  studentId?: string;
  scope?: string;
}) {
  const list = usePaged<PaymentOrder>(ORDERS_PATH, "", studentId ? `idAlumno=${encodeURIComponent(studentId)}` : "", 10);
  const [selected, setSelected] = useState<PaymentOrder | null>(null);
  function settled() { list.refresh(); onRefreshObligations(); }
  const refreshPendingOrders = useEffectEvent(() => {
    if (document.visibilityState === "visible" && !list.loading) list.refresh();
  });
  const hasPendingOrders = list.data?.content.some((order) => isPending(order.estado)) ?? false;
  const monitoring = hasPendingOrders && !selected;
  useEffect(() => {
    if (!monitoring) return;
    const timer = setInterval(() => refreshPendingOrders(), 5000);
    return () => clearInterval(timer);
  }, [monitoring]);
  return <section className="panel">
    <PanelHeading title={`Seguimiento de pagos de ${scope}`}
      summary="Continúa las órdenes pendientes o que requieren autenticación y consulta su estado actualizado."
      actions={<button type="button" className="secondary-action" disabled={list.loading} onClick={list.refresh}>Actualizar seguimiento</button>} />
    <Feedback error={list.error} />
    <p className={styles.hint}>Puedes continuar una orden durante sus cinco minutos de vigencia. Cuando termina, se consulta en Historial de pagos.</p>
    {list.loading ? <Loading>Cargando seguimiento…</Loading> : !list.error && !list.data?.content.length ? <p className="empty">No hay órdenes en curso para la selección.</p> : list.data && <div className={styles.tableWrap}>
      <table className={`user-table ${styles.historyTable}`} aria-label="Seguimiento de mis pagos"><thead><tr><th>Referencia y fecha</th><th>Estudiantes</th><th>Medio</th><th>Monto</th><th>Estado</th><th>Acciones</th></tr></thead><tbody>
        {list.data.content.map((order) => <tr key={order.id}>
          <td><strong>{order.codigo}</strong><small>{dateLabel(order.creadaEn)}</small></td>
          <td>{[...new Set(order.items.map((item) => item.alumno))].join(", ") || "—"}</td><td>{order.metodo === "TARJETA" ? "Tarjeta" : "Código QR"}</td>
          <td>{moneyLabel(order.montoTotal, "PEN")}</td><td><StatusPill state={order.estado} label={orderLabels[order.estado]} tone="neutral" /></td>
          <td><button type="button" className="secondary-action" onClick={() => setSelected(order)}>Continuar pago</button></td>
        </tr>)}
      </tbody></table>
    </div>}
    <Pager data={list.data} loading={list.loading} setPage={list.setPage} />
    {selected && <PaymentCheckout obligations={[]} initialOrder={selected} onClose={() => { setSelected(null); settled(); }} onSettled={settled} />}
  </section>;
}
