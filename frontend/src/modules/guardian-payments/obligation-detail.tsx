"use client";
import { useEffect, useState } from "react";
import { message } from "@/core/api/client";
import { Dialog, Feedback, StatusPill } from "@/shared/ui";
import { dateLabel } from "@/shared/lib/formatters";
import { dueDateLabel, financialLabel, moneyLabel, paymentMethodLabel, quotaLabel, receiptCode } from "./payment-format";
import { getObligation } from "./services/payment-service";
import type { ObligationDetail } from "./types";
import styles from "./guardian-payments.module.css";

const transactionLabels: Record<string, string> = {
  PENDIENTE: "Pendiente de confirmación", APROBADA: "Pago aprobado", RECHAZADA: "Intento rechazado", EXPIRADA: "Intento vencido",
};
export function ObligationDetailDialog({ id, onClose }: { id: number; onClose: () => void }) {
  const [detail, setDetail] = useState<ObligationDetail | null>(null);
  const [error, setError] = useState("");
  const [revision, setRevision] = useState(0);
  useEffect(() => {
    let active = true;
    getObligation(id).then((value) => { if (active) { setDetail(value); setError(""); } })
      .catch((caught) => { if (active) setError(message(caught)); });
    return () => { active = false; };
  }, [id, revision]);
  const item = detail?.compromiso;
  return <Dialog title="Detalle de la obligación" description={item ? `${item.alumno} · ${item.codigoCompromiso}` : "Consulta las cuotas y sus movimientos."} onClose={onClose} size="lg">
    <Feedback error={error} />
    {!detail && !error && <p role="status">Consultando obligación…</p>}
    {error && <button type="button" className="secondary-action" onClick={() => setRevision((value) => value + 1)}>Reintentar consulta</button>}
    {detail && item && <>
      <dl className={styles.summary}>
        <div><dt>Concepto</dt><dd>{item.concepto}{item.numeroCuota != null && <small>{quotaLabel(item.numeroCuota)}</small>}</dd></div>
        <div><dt>Estado</dt><dd>{financialLabel(item)}</dd></div>
        <div><dt>Monto</dt><dd>{moneyLabel(item.montoTotal, item.moneda)}</dd></div>
        {(item.montoDescuento ?? 0) > 0 && <><div><dt>Importe regular</dt><dd>{moneyLabel(item.montoBase ?? item.montoTotal, item.moneda)}</dd></div>
          <div><dt>Beneficio aplicado</dt><dd>{item.beneficioNombre}<small>−{moneyLabel(item.montoDescuento!, item.moneda)}</small></dd></div></>}
        {item.montoPagado > 0 && item.saldo > 0 && <><div><dt>Pagado a la fecha</dt><dd>{moneyLabel(item.montoPagado, item.moneda)}</dd></div><div><dt>Por pagar</dt><dd>{moneyLabel(item.saldo, item.moneda)}</dd></div></>}
        <div><dt>Vencimiento</dt><dd>{dueDateLabel(item.fechaVencimiento)}</dd></div>
        <div><dt>Matrícula</dt><dd>{item.codigoMatricula}<small>{item.anioAcademico}</small></dd></div>
      </dl>
      {item.estadoFinanciero === "EXONERADO" && <p className={styles.notice}>Esta obligación está exonerada. No requiere pago y no genera un comprobante de dinero recibido.</p>}
      <h4>Historial de transacciones</h4>
      {!detail.transacciones.length ? <p className={styles.hint}>Todavía no hay intentos de pago para esta obligación.</p> : <ul className={styles.historyList}>
        {detail.transacciones.map((tx) => <li key={tx.id}>
          <span><strong>{paymentMethodLabel(tx.medioPago)}</strong><small>{dateLabel(tx.fechaTransaccion)}</small></span>
          <span><strong>{moneyLabel(tx.monto, item.moneda)}</strong><StatusPill state={tx.estado} label={transactionLabels[tx.estado] ?? tx.estado} tone={tx.estado === "APROBADA" ? "success" : tx.estado === "RECHAZADA" ? "danger" : "neutral"} /></span>
        </li>)}
      </ul>}
      <h4>Comprobantes de pago</h4>
      {!detail.comprobantes.length ? <p className={styles.hint}>Los comprobantes se emiten cuando un pago es aprobado.</p> : <ul className={styles.itemList}>{detail.comprobantes.map((receipt) => <li key={receipt.id}>
        <span><strong>{receiptCode(receipt.id)}</strong><small>{dateLabel(receipt.fechaEmision)} · Constancia de pago</small></span><strong>{moneyLabel(receipt.montoTotal, item.moneda)}</strong>
      </li>)}</ul>}
      <p className={styles.hint}>Los intentos pendientes, rechazados o vencidos no representan pagos realizados.</p>
    </>}
    <div className={styles.actions}><button type="button" className={`secondary-action ${styles.closeButton}`} onClick={onClose}>Cerrar</button></div>
  </Dialog>;
}
