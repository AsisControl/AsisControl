import { moneyLabel, quotaLabel } from "./payment-format";
import type { PaymentOrder } from "./types";
import styles from "./guardian-payments.module.css";
export function OrderSummary({ order }: { order: PaymentOrder }) {
  return <>
    <p className={styles.orderCode}>Orden <strong>{order.codigo}</strong></p>
    <ul className={styles.itemList}>{order.items.map((item) => <li key={item.idCompromiso}>
      <span><strong>{item.alumno}</strong><small>{item.concepto} · {quotaLabel(item.numeroCuota)}</small></span>
      <strong>{moneyLabel(item.monto, "PEN")}</strong>
    </li>)}</ul>
    <dl className={styles.totals}><div><dt>Obligaciones seleccionadas</dt><dd>{moneyLabel(order.montoBase, "PEN")}</dd></div>
      <div><dt>Mora</dt><dd>{moneyLabel(order.mora, "PEN")}</dd></div><div className={styles.total}><dt>Total a pagar</dt><dd>{moneyLabel(order.montoTotal, "PEN")}</dd></div></dl>
  </>;
}
