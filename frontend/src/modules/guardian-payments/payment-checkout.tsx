"use client";
import { useRef, useState } from "react";
import { QRCodeSVG } from "qrcode.react";
import { ApiError, message } from "@/core/api/client";
import { Dialog, Feedback, StatusPill } from "@/shared/ui";
import type { PaymentCommitment } from "@/modules/finance";
import { authenticateCard, confirmCard, getOrder, startOrder } from "./services/payment-service";
import { countdownLabel, isPending, moneyLabel, orderLabels, walletUrl } from "./payment-format";
import { useCountdown, useOrderMonitor } from "./use-order-monitor";
import { OrderSummary } from "./order-summary";
import { ReceiptPanel } from "./receipt-panel";
import { PaymentTabs } from "./payment-tabs";
import { paymentRequestKey } from "./payment-request-key";
import type { PaymentMethod, PaymentOrder } from "./types";
import styles from "./guardian-payments.module.css";

export function PaymentCheckout({ obligations, initialOrder, onClose, onSettled, onFollowPayments }: {
  obligations: PaymentCommitment[];
  initialOrder?: PaymentOrder;
  onClose: () => void;
  onSettled: (order: PaymentOrder) => void;
  onFollowPayments?: () => void;
}) {
  const [method, setMethod] = useState<PaymentMethod>(initialOrder?.metodo ?? "TARJETA");
  const [order, setOrder] = useState<PaymentOrder | null>(initialOrder ?? null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [conflict, setConflict] = useState(false);
  const inFlight = useRef(false);
  const latest = useRef(order);
  const notified = useRef<number | null>(null);
  const keys = useRef<Partial<Record<PaymentMethod, string>>>({});
  function update(next: PaymentOrder) {
    // Una consulta iniciada antes de confirmar no debe devolver la pantalla a Pendiente.
    if (latest.current?.id === next.id && !isPending(latest.current.estado) && isPending(next.estado)) return;
    latest.current = next; setOrder(next);
    setMethod(next.metodo);
    if (!isPending(next.estado)) setError("");
    if (!isPending(next.estado) && notified.current !== next.id) {
      notified.current = next.id; onSettled(next);
    }
  }
  const pollError = useOrderMonitor(order, () => getOrder(order!.id), update);
  const pending = !!order && isPending(order.estado);
  const remaining = useCountdown(order?.expiraEn ?? null, pending);
  async function perform(action: () => Promise<PaymentOrder>) {
    if (inFlight.current) return;
    inFlight.current = true; setBusy(true); setError(""); setConflict(false);
    try { update(await action()); }
    catch (caught) {
      if (!latest.current || isPending(latest.current.estado)) {
        setError(message(caught));
        setConflict(caught instanceof ApiError && caught.code === "PAGO_EN_CURSO");
        const current = latest.current;
        if (current && caught instanceof ApiError && caught.code === "TRANSICION_PAGO_INVALIDA") {
          try { update(await getOrder(current.id)); } catch { /* El sondeo volverá a consultar. */ }
        }
      }
    }
    finally { inFlight.current = false; setBusy(false); }
  }
  function begin() {
    return perform(() => {
      const key = keys.current[method] ??= paymentRequestKey();
      return startOrder(obligations.map((item) => item.id), method, key);
    });
  }
  function retry() {
    keys.current = {}; latest.current = null; notified.current = null;
    setOrder(null); setError(""); setConflict(false);
  }
  const url = walletUrl(order?.qrUrl ?? null);
  return <Dialog title={order?.estado === "COMPLETADO" ? "Pago aprobado" : "Pagar obligaciones"}
    description={order ? "Consulta el importe y el estado de tu operación." : "Revisa tus obligaciones y elige cómo pagar."}
    size="lg" onClose={onClose} busy={busy}>
    <Feedback error={error} />
    {conflict && onFollowPayments && <button type="button" className="secondary-action" onClick={onFollowPayments}>Ir a Seguimiento de pagos</button>}
    {!order ? <>
      <PaymentTabs label="Medio de pago" selected={method} onSelect={(value) => setMethod(value as PaymentMethod)} disabled={busy}
        items={[{ id: "TARJETA", label: "Tarjeta" }, { id: "QR", label: "Código QR" }]}>
      <ul className={styles.itemList}>{obligations.map((item) => <li key={item.id}><span><strong>{item.alumno}</strong><small>{item.concepto} · {item.codigoCompromiso}</small></span><strong>{moneyLabel(item.saldo, "PEN")}</strong></li>)}</ul>
      <dl className={styles.totals}>
        <div className={styles.total}><dt>Total seleccionado</dt><dd>{moneyLabel(obligations.reduce((sum, item) => sum + item.saldo, 0), "PEN")}</dd></div>
      </dl>
      <p className={styles.hint}>Pago simulado. No se realizará un cargo bancario.</p>
      <div className="form-actions"><button type="button" className="secondary-action" disabled={busy} onClick={onClose}>Cancelar</button>
        <button type="button" className="primary-action" disabled={busy || !obligations.length} onClick={begin}>{busy ? "Preparando…" : method === "QR" ? "Generar código QR" : "Continuar con tarjeta"}</button></div>
      </PaymentTabs>
    </> : <>
      {pending && initialOrder && <p role="status" className={styles.notice}>Retomaste el pago {order.codigo} que ya estaba pendiente. Conserva su medio de pago y el tiempo disponible.</p>}
      <StatusPill state={order.estado} label={orderLabels[order.estado]} tone={order.estado === "COMPLETADO" ? "success" : order.estado === "RECHAZADO" ? "danger" : "info"} />
      {order.estado === "COMPLETADO" ? <ReceiptPanel orderId={order.id} /> : <>
        <OrderSummary order={order} />
        {order.mensaje && <p role="status" className={styles.notice}>{order.mensaje}</p>}
        {pending && <>
          <p className={styles.timer}>Tiempo disponible: <strong>{countdownLabel(remaining)}</strong></p>
          {order.metodo === "QR" ? <div className={styles.qrPanel}>
            {url && remaining > 0 ? <><div className={styles.qrCode}><QRCodeSVG value={url} size={208} title="Escanea para abrir la billetera" level="M" /></div>
              <p role="status">Esperando confirmación en la billetera…</p>
              <p className={styles.hint}>Escanea el código con tu celular o abre la billetera en esta pantalla.</p>
              <a className="secondary-action" href={url} target="_blank" rel="noopener noreferrer" referrerPolicy="no-referrer">Abrir billetera en esta pantalla</a></>
              : url ? <p role="status">El tiempo del código terminó. Estamos verificando el resultado de la operación.</p>
                : <p role="alert">No pudimos preparar el enlace del QR. Cierra la ventana e inténtalo nuevamente.</p>}
          </div> : order.estado === "REQUIERE_AUTENTICACION" ? <section className={styles.challenge} aria-label="Autenticación adicional">
            <h4>Confirma la autenticación adicional</h4><p>Completa la verificación adicional para continuar.</p>
            <div className={styles.actions}><button type="button" className="primary-action" disabled={busy || remaining === 0} onClick={() => perform(() => authenticateCard(order.id, true))}>Completar autenticación</button>
              <button type="button" className="secondary-action" disabled={busy} onClick={() => perform(() => authenticateCard(order.id, false))}>Cancelar autenticación</button></div>
          </section> : <section className={styles.cardPanel} aria-label="Pago con tarjeta">
            <div className={styles.cardMethod}>
              <span className={styles.cardIcon} aria-hidden="true" />
              <div><h4>Pago con tarjeta</h4><p>Confirma la operación con una tarjeta virtual del sistema.</p></div>
            </div>
            <p className={styles.hint}>Pago simulado. No se realizará un cargo bancario.</p>
            <button type="button" className="primary-action" disabled={busy || remaining === 0 || !order.simulador} onClick={() => perform(() => confirmCard(order.id))}>{busy ? "Confirmando…" : `Confirmar pago ${moneyLabel(order.montoTotal, "PEN")}`}</button>
          </section>}
          {pollError && <p role="status" className={styles.notice}>No pudimos actualizar el estado. Seguiremos consultando; evita iniciar otro pago mientras confirmamos el resultado.</p>}
          <button type="button" className="secondary-action" disabled={busy} onClick={() => perform(() => getOrder(order.id))}>Consultar estado</button>
          <p className={styles.hint}>Puedes retomar esta operación desde Seguimiento de pagos mientras siga vigente.</p>
        </>}
      </>}
      <div className={styles.actions}>
        {!pending && order.estado !== "COMPLETADO" && obligations.length > 0 && <button type="button" className="primary-action" onClick={retry}>Intentar de nuevo</button>}
        <button type="button" className={`secondary-action ${styles.closeButton}`} disabled={busy} onClick={onClose}>Cerrar</button>
      </div>
    </>}
  </Dialog>;
}
