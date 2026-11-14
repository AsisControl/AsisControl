"use client";
import { useEffect, useState } from "react";
import { message } from "@/core/api/client";
import { Feedback } from "@/shared/ui";
import { dateLabel } from "@/shared/lib/formatters";
import { moneyLabel, paymentMethodLabel, quotaLabel } from "./payment-format";
import { downloadIndividualReceipt, getIndividualReceipt } from "./services/payment-service";
import type { ReceiptHistoryItem } from "./types";
import styles from "./guardian-payments.module.css";

export function ReceiptDetail({ id }: { id: number }) {
  const [receipt, setReceipt] = useState<ReceiptHistoryItem | null>(null);
  const [error, setError] = useState("");
  const [revision, setRevision] = useState(0);
  const [downloading, setDownloading] = useState(false);
  useEffect(() => {
    let active = true;
    getIndividualReceipt(id).then((value) => { if (active) { setReceipt(value); setError(""); } })
      .catch((caught) => { if (active) setError(message(caught)); });
    return () => { active = false; };
  }, [id, revision]);
  async function download() {
    if (!receipt || downloading) return;
    setDownloading(true); setError("");
    try { await downloadIndividualReceipt(receipt.id, receipt.codigo); }
    catch (caught) { setError(message(caught)); }
    finally { setDownloading(false); }
  }
  return <section className={styles.receipt} aria-label="Detalle del comprobante">
    <Feedback error={error} />
    {!receipt && !error && <p role="status">Consultando comprobante…</p>}
    {!receipt && error && <button type="button" className="secondary-action" onClick={() => setRevision((value) => value + 1)}>Reintentar comprobante</button>}
    {receipt && <>
      <header className={styles.receiptHeading}><div><p>{receipt.institucion}</p><h4>Constancia de pago</h4></div><strong>{receipt.codigo}</strong></header>
      <dl className={styles.summary}>
        <div><dt>Apoderado</dt><dd>{receipt.apoderado}</dd></div>
        <div><dt>Fecha de pago</dt><dd>{dateLabel(receipt.fechaEmision)}</dd></div>
        <div><dt>Medio de pago</dt><dd>{paymentMethodLabel(receipt.medioPago)}</dd></div>
        {receipt.codigoOrden && <div><dt>Operación</dt><dd>{receipt.codigoOrden}</dd></div>}
      </dl>
      <ul className={styles.itemList}><li><span><strong>{receipt.alumno}</strong><small>{receipt.concepto}{receipt.numeroCuota != null && ` · ${quotaLabel(receipt.numeroCuota)}`}</small><small>{receipt.codigoCompromiso}</small></span><strong>{moneyLabel(receipt.montoTotal, receipt.moneda)}</strong></li></ul>
      <div className={styles.receiptTotal}><span>Total pagado</span><strong>{moneyLabel(receipt.montoTotal, receipt.moneda)}</strong></div>
      <p className={styles.hint}>Documento de control institucional.</p>
      <button type="button" className="secondary-action" disabled={downloading} onClick={download}>{downloading ? "Descargando…" : "Descargar comprobante"}</button>
    </>}
  </section>;
}
