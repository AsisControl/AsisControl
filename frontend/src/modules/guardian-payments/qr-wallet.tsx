"use client";
import { useEffect, useRef, useState } from "react";
import { message } from "@/core/api/client";
import { Feedback, StatusPill } from "@/shared/ui";
import { confirmQr, getQrOrder } from "./services/payment-service";
import { countdownLabel, isPending, moneyLabel, orderLabels } from "./payment-format";
import { OrderSummary } from "./order-summary";
import { useCountdown, useOrderMonitor } from "./use-order-monitor";
import type { PaymentOrder } from "./types";
import styles from "./guardian-payments.module.css";

/** Página pública de una única capacidad temporal. Nunca solicita una cuenta o tarjeta. */
export function QrWallet({ token }: { token: string }) {
  const [order, setOrder] = useState<PaymentOrder | null>(null);
  const [error, setError] = useState("");
  const [revision, setRevision] = useState(0);
  const [busy, setBusy] = useState(false);
  const inFlight = useRef(false);
  const latest = useRef<PaymentOrder | null>(null);
  useEffect(() => {
    let active = true;
    getQrOrder(token).then((value) => { if (active) { latest.current = value; setOrder(value); setError(""); } })
      .catch((caught) => { if (active) setError(message(caught)); });
    return () => { active = false; };
  }, [token, revision]);
  function update(next: PaymentOrder) {
    if (latest.current && !isPending(latest.current.estado) && isPending(next.estado)) return;
    latest.current = next; setOrder(next);
    if (!isPending(next.estado)) setError("");
  }
  const hasDetails = !!order?.items.length;
  const pollError = useOrderMonitor(hasDetails ? order : null, () => getQrOrder(token), update);
  const pending = !!order && hasDetails && isPending(order.estado);
  const remaining = useCountdown(order?.expiraEn ?? null, pending);
  async function confirm(action: "APROBAR" | "RECHAZAR") {
    if (inFlight.current) return;
    inFlight.current = true; setBusy(true); setError("");
    try { update(await confirmQr(token, action)); }
    catch (caught) { if (!latest.current || isPending(latest.current.estado)) setError(message(caught)); }
    finally { inFlight.current = false; setBusy(false); }
  }
  return <main className={styles.wallet}>
    <div className={styles.walletCard}>
      <header className={styles.walletHeader}><span className={styles.walletIcon} aria-hidden="true">S/</span><div><p>Billetera digital</p><h1>{order?.institucion || "AsisControl"}</h1></div></header>
      <Feedback error={error} />
      {!order && !error && <p role="status">Consultando el código QR…</p>}
      {error && <button type="button" className="secondary-action" disabled={busy} onClick={() => setRevision((value) => value + 1)}>Volver a consultar QR</button>}
      {order && <>
        <div className={styles.walletStatus}><StatusPill state={order.estado} label={orderLabels[order.estado]} tone={order.estado === "COMPLETADO" ? "success" : order.estado === "RECHAZADO" ? "danger" : "info"} /></div>
        {!hasDetails ? <p className={styles.notice} role="status">Este enlace venció. Consulta el resultado y el comprobante desde tu cuenta.</p> : <>
        <p className={styles.hint}>Pago solicitado por {order.apoderado}</p>
        <OrderSummary order={order} />
        {order.mensaje && <p className={styles.notice}>{order.mensaje}</p>}
        {pending ? <>
          <p className={styles.timer}>Vigencia del QR: <strong>{countdownLabel(remaining)}</strong></p>
          <div className={styles.walletActions}>
            <button type="button" className="primary-action" disabled={busy || remaining === 0} onClick={() => confirm("APROBAR")}>{busy ? "Confirmando…" : `Confirmar pago ${moneyLabel(order.montoTotal, "PEN")}`}</button>
            <button type="button" className="secondary-action" disabled={busy || remaining === 0} onClick={() => confirm("RECHAZAR")}>Rechazar operación</button>
          </div>
          {remaining === 0 && <p role="status">La vigencia terminó. Estamos comprobando el estado de la orden.</p>}
          {pollError && <p role="status" className={styles.notice}>No pudimos actualizar el estado. Se volverá a consultar automáticamente.</p>}
        </> : <div className={order.estado === "COMPLETADO" ? styles.approved : styles.notice} role="status">
          <strong>{order.estado === "COMPLETADO" ? "Tu pago fue confirmado." : order.estado === "EXPIRADO" ? "Este código QR ya venció." : "El pago fue rechazado."}</strong>
          <p>{order.estado === "COMPLETADO" ? "La pantalla principal se actualizará automáticamente. Consulta allí tu comprobante." : "Las obligaciones no se han cobrado. Puedes iniciar un nuevo pago desde la pantalla principal."}</p>
        </div>}
        </>}
      </>}
      <footer className={styles.walletFooter}>AsisControl · Gestión de pagos</footer>
    </div>
  </main>;
}
