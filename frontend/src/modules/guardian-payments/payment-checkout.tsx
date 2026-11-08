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
import type { CardScenario, PaymentMethod, PaymentOrder } from "./types";
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
  const [scenario, setScenario] = useState<CardScenario>("APROBADO");
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
      // Una respuesta perdida no contradice un resultado definitivo recibido por sondeo.
      if (!latest.current || isPending(latest.current.estado)) {
        setError(message(caught));
        setConflict(caught instanceof ApiError && caught.code === "PAGO_EN_CURSO");
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
    description="Revisa el detalle y confirma el pago de las cuotas seleccionadas." size="lg" onClose={onClose} busy={busy}>
    <Feedback error={error} />
    {conflict && onFollowPayments && <button type="button" className="secondary-action" onClick={onFollowPayments}>Ir a Seguimiento de pagos</button>}
    {!order ? <>
      <PaymentTabs label="Medio de pago" selected={method} onSelect={(value) => setMethod(value as PaymentMethod)} disabled={busy}
        items={[{ id: "TARJETA", label: "Tarjeta" }, { id: "QR", label: "Código QR" }]}>
      <ul className={styles.itemList}>{obligations.map((item) => <li key={item.id}><span><strong>{item.alumno}</strong><small>{item.concepto} · {item.codigoCompromiso}</small></span><strong>{moneyLabel(item.saldo, "PEN")}</strong></li>)}</ul>
      <p className={styles.hint}>Revisa las cuotas seleccionadas antes de continuar. Sin recargos por mora.</p>
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
            {url ? <><div className={styles.qrCode}><QRCodeSVG value={url} size={208} title="Escanea para abrir la billetera" level="M" /></div>
              <p role="status">Esperando confirmación en la billetera…</p>
              <a className="secondary-action" href={url} target="_blank" rel="noopener noreferrer" referrerPolicy="no-referrer">Abrir billetera en esta pantalla</a></>
              : <p role="alert">No pudimos preparar el enlace del QR. Cierra la ventana e inténtalo nuevamente.</p>}
          </div> : order.estado === "REQUIERE_AUTENTICACION" ? <section className={styles.challenge} aria-label="Autenticación adicional">
            <h4>Confirma la autenticación adicional</h4><p>Completa la verificación adicional para continuar.</p>
            <div className={styles.actions}><button type="button" className="primary-action" disabled={busy || remaining === 0} onClick={() => perform(() => authenticateCard(order.id, true))}>Completar autenticación</button>
              <button type="button" className="secondary-action" disabled={busy} onClick={() => perform(() => authenticateCard(order.id, false))}>Cancelar autenticación</button></div>
          </section> : <section className={styles.cardPanel}>
            {order.simulador && <label className="field">Resultado de autorización<select value={scenario} disabled={busy} onChange={(event) => setScenario(event.target.value as CardScenario)}>
              <option value="APROBADO">Aprobada · Visa terminada en 4242</option>
              <option value="REQUIERE_AUTENTICACION">Autenticación adicional · terminada en 3155</option>
              <option value="FONDOS_INSUFICIENTES">Fondos insuficientes · terminada en 9995</option>
              <option value="RECHAZADO">Tarjeta rechazada · terminada en 0002</option>
            </select></label>}
            <p className={styles.hint}>Revisa el importe antes de confirmar.</p>
            <button type="button" className="primary-action" disabled={busy || remaining === 0 || !order.simulador} onClick={() => perform(() => confirmCard(order.id, scenario))}>{busy ? "Confirmando…" : `Confirmar pago ${moneyLabel(order.montoTotal, "PEN")}`}</button>
          </section>}
          {pollError && <p role="status" className={styles.notice}>No pudimos actualizar el estado. Seguiremos consultando; evita iniciar otro pago mientras confirmamos el resultado.</p>}
          <button type="button" className="secondary-action" disabled={busy} onClick={() => perform(() => getOrder(order.id))}>Consultar estado</button>
          <p className={styles.hint}>Puedes cerrar esta ventana y continuar desde Seguimiento de pagos mientras siga vigente. Cerrar la ventana no cancela el pago.</p>
        </>}
      </>}
      <div className={styles.actions}>
        {!pending && order.estado !== "COMPLETADO" && obligations.length > 0 && <button type="button" className="primary-action" onClick={retry}>Intentar de nuevo</button>}
        <button type="button" className={`secondary-action ${styles.closeButton}`} disabled={busy} onClick={onClose}>Cerrar</button>
      </div>
    </>}
  </Dialog>;
}
