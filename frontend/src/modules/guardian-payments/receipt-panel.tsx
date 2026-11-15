"use client";
import { useEffect, useState } from "react";
import { message } from "@/core/api/client";
import { Feedback } from "@/shared/ui";
import { dateLabel } from "@/shared/lib/formatters";
import { moneyLabel, receiptCode } from "./payment-format";
import { downloadReceipt, getReceipt } from "./services/payment-service";
import type { OrderReceipt } from "./types";
import styles from "./guardian-payments.module.css";

export function ReceiptPanel({ orderId }: { orderId: number }) {
  const [receipt, setReceipt] = useState<OrderReceipt | null>(null);
  const [error, setError] = useState("");
  const [revision, setRevision] = useState(0);
  const [downloading, setDownloading] = useState(false);
  useEffect(() => {
    let active = true;
    getReceipt(orderId).then((value) => { if (active) { setReceipt(value); setError(""); } })
      .catch((caught) => { if (active) setError(message(caught)); });
    return () => { active = false; };
  }, [orderId, revision]);
  async function download() {
    if (!receipt || downloading) return;
    setDownloading(true); setError("");
    try { await downloadReceipt(orderId, receipt.orden.codigo); }
    catch (caught) { setError(message(caught)); }
    finally { setDownloading(false); }
  }
  return <section className={styles.receipt} aria-label="Constancia de pago">
    <Feedback error={error} />
    {!receipt && !error && <p role="status">Preparando comprobante…</p>}
    {!receipt && error && <button type="button" className="secondary-action" onClick={() => setRevision((value) => value + 1)}>Reintentar comprobante</button>}
    {receipt && <>
      <header className={styles.receiptHeading}><div><p>{receipt.orden.institucion}</p><h4>Constancia de pago</h4></div><strong>{receipt.orden.codigo}</strong></header>
      <dl className={styles.summary}><div><dt>Institución</dt><dd>{receipt.orden.institucion}</dd></div><div><dt>Orden</dt><dd>{receipt.orden.codigo}</dd></div>
        <div><dt>Fecha</dt><dd>{dateLabel(receipt.orden.pagadaEn || receipt.orden.creadaEn)}</dd></div><div><dt>Total aprobado</dt><dd>{moneyLabel(receipt.orden.montoTotal, "PEN")}</dd></div></dl>
      <ul className={styles.itemList}>{receipt.orden.items.map((item) => <li key={item.idCompromiso}><span><strong>{item.alumno}</strong><small>{item.concepto} · {item.codigoCompromiso}</small></span><strong>{moneyLabel(item.monto, "PEN")}</strong></li>)}</ul>
      <p className={styles.hint}>Referencias: {receipt.comprobantes.map((item) => receiptCode(item.id)).join(", ")}</p>
      <p className={styles.hint}>Documento de control institucional.</p>
      <button type="button" className="secondary-action" disabled={downloading} onClick={download}>{downloading ? "Descargando…" : "Descargar comprobante"}</button>
    </>}
  </section>;
}
